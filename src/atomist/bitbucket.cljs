(ns atomist.bitbucket
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [http.client :as http]
            [cljs.core.async :refer [<! >! chan close!] :as async]
            [cljs.pprint :refer [pprint]]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]))

(defn webhook-data [url]
  {:name "Atomist Webhook"
   :events
   ["pr:comment:edited"
    "pr:reviewer:needs_work"
    "pr:comment:deleted"
    "repo:modified"
    "pr:merged"
    "repo:forked"
    "pr:reviewer:approved"
    "pr:declined"
    "repo:comment:deleted"
    "pr:comment:added"
    "repo:comment:edited"
    "pr:opened"
    "repo:comment:added"
    "pr:deleted"
    "pr:reviewer:unapproved"]
   :configuration {}
   :url url
   :active true})

(def hook-key "com.atlassian.stash.plugin.stash-web-post-receive-hooks-plugin:postReceiveHook")

(defn check-for-hook [x]
  (= hook-key (-> x :details :key)))

(defn check-for-our-webhook
  [webhook-url x]
  (and
   (= webhook-url (:url x))
   (:active x)))

(defn trace [x] x)

(defn hook-resource [server hook-key]
  (gstring/format (str server "rest/api/1.0/hooks/%s") hook-key))

(defn project-hook-resource-enabled [server project-name]
  (gstring/format (str server "rest/api/1.0/projects/%s/settings/hooks/%s/enabled") project-name hook-key))

(defn project-hook-resource-settings [server project-name hook-key]
  (gstring/format (str server "rest/api/1.0/projects/%s/settings/hooks/%s/settings") project-name hook-key))

(defn repo-webhook-resource [server project-name repo-slug]
  (gstring/format (str server "rest/api/1.0/projects/%s/repos/%s/webhooks") project-name repo-slug))

(defn repo-resources [server project-name]
  (gstring/format (str server "rest/api/1.0/projects/%s/repos") project-name))

(defn project-hook-resource
  ([server project-name]
   (gstring/format (str server "rest/api/1.0/projects/%s/settings/hooks") project-name))
  ([server project-name hook-key]
   (gstring/format (str server "rest/api/1.0/projects/%s/settings/hooks/%s") project-name hook-key)))

;; https://docs.atlassian.com/bitbucket-server/rest/5.12.0/bitbucket-rest.html
(defn bitbucket-resource->channel
  "create a channel that pages through all bitbucket values on a channel
   channel will be closed when values are exhausted
   TODO - forgot to add the start param to the query
        - what about GET failures
        - if values is empty, check whether isLastPage is always true
        - should we recur with size + start?nn
        - what about 200s with no values?"
  [url username password]
  (let [c (async/chan 10)]
    (go-loop
     [start 0]
      (let [response (<! (http/get url {:basic-auth [username password]
                                        :query-params {:start start}}))]
        (if (= 200 (-> response :status))
          (do
            (doseq [x (-> response :body :values)]
              (>! c x))
            (if (false? (-> response :body :isLastPage))
              (recur (+ start (-> response :body :size)))))
          (log/warnf "status on request to %s is %s" url (-> response :status)))
        (async/close! c)))
    c))

(defn add-transducer
  "create new channel with transducer added to existing channel"
  [xf chan]
  (let [c (async/chan 10)]
    (async/pipeline 1 c xf chan)
    c))

;; ----- edit resources ------

(defn create-webhook
  "schedule creation of webhook
   this is fire and forget so there is no error channel or error callback besides the logging"
  [{:keys [server project username password url]} slug]
  (go
    (let [response (<! (http/post (repo-webhook-resource server project slug)
                                  {:json-params (webhook-data url)
                                   :basic-auth [username password]}))]
      (if (= 201 (:status response))
        (log/info "successfully created webhook " (-> response :body :id))
        (log/error "error creating webhook " (-> response :status))))))

(defn enable-hook
  "schedule the creation of the hook - no error channel or error callback besides logging"
  [{:keys [server project username password url]}]
  (go
    (let [response (<! (http/put (project-hook-resource-enabled server project) {:basic-auth [username password]}))]
      (if (= 201 (:status response))
        (log/info "successfully enabled hook " (-> response :body))
        (log/error "error enabling hook " (-> response :status))))
    (let [response (<! (http/put (project-hook-resource-settings server project hook-key)
                                 {:json-params {:hook-url-0 url}
                                  :basic-auth [username password]}))]
      (if (= 201 (:status response))
        (log/info "successfully PUT settings for hook " (-> response :body))
        (log/error "error putting settings to hook " (-> response :status))))))

(defn post-receive-hook->channel
  "create a channel by scanning all project hook resources in this project and filtering out the
      ones that we care about"
  [{:keys [server project username password]}]
  (add-transducer
   (filter check-for-hook)
   (bitbucket-resource->channel
    (project-hook-resource server project)
    username
    password)))

(defn repo-webhooks->channel
  "create a channel that can emit one value
      - an [] of any repo resources that has our active webhook url
      - empty [] means this repo doesn't have any"
  [{:keys [server project username password url]} slug]
  (async/into []
              (add-transducer
               (comp (filter (partial check-for-our-webhook url)))
               (bitbucket-resource->channel
                (repo-webhook-resource server project slug)
                username
                password))))

(defn check-webhook
  "create our webhook if the repo webhook channel emits an empty []
    no error channel.
   runs async but does not return any value on this channel because it's
    actions are either creating things or logging something - no error channel"
  [config slug]
  (go
    (let [webhooks (<! (repo-webhooks->channel config slug))]
      (if (empty? webhooks)
        (create-webhook config slug)
        (log/info "found existing webhook on " slug)))))

(defn check-all-project-webhooks
  "PUBLIC
     read a project's repo slugs off of slug-channel and validate that all of the project webhooks are set up according
     to this config."
  [{:keys [server project username password] :as config} slug-channel]
  (go-loop
   [slug (<! slug-channel)]
    (when slug
      (check-webhook config slug)
      (recur (<! slug-channel)))))

(defn on-repo
  "PUBLIC
    whenever we create a new repo, check that the repo has the correct policy"
  [{:keys [url] :as config} repo]
  (go
    (if-let [hook (<! (post-receive-hook->channel config))]
      (cond
        (not (:enabled hook))
        (do
          (log/info "enabling hook")
          (enable-hook config))
        :else
        (log/info "project hook enabled"))
      (log/info "there is no hook installed for" hook-key))
    (check-webhook config repo)))

(defn slug->channel
  "PUBLIC
    return a channel with the slugs of all repo resources in this project"
  [{:keys [server project username password]}]
  (add-transducer
   (comp (map :slug) (map trace))
   (bitbucket-resource->channel
    (repo-resources server project)
    username
    password)))
