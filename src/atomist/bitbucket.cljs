(ns atomist.bitbucket
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [http.client :as http]
            [cljs.core.async :refer [<! >! chan close!] :as async]
            [cljs.pprint :refer [pprint]]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]))

(defn webhook-data [url]
  {:name "Atomist Webhook",
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
    "pr:reviewer:unapproved"],
   :configuration {},
   :url url,
   :active true})

(def bitbucket-server "http://bitbucket-server-54.atomist.com:7990/")
(def webhook-url "https://webhook-staging.atomist.services/atomist/bitbucket/teams/A4EOI5D1E/7cq796lgw39rlsi")

(def config {:server bitbucket-server
             :project "SLIM"
             :username "slimslenderslacks"
             :password "slimslenderslacks"
             :url webhook-url})

(def hook-key "com.atlassian.stash.plugin.stash-web-post-receive-hooks-plugin:postReceiveHook")

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

(defn bitbucket-resource-value-channel
  [url username password]
  (let [c (async/chan 10)]
    (go-loop
     [start 0]
     (let [response (async/<! (http/get url {:basic-auth [username password]}))]
       (doseq [x (-> response :body :values)]
         (async/>! c x))
       (if (false? (-> response :body :isLastPage))
         (recur (-> response :body :size))
         (async/close! c))))
    c))

(defn add-transducer [xf chan]
  (let [c (async/chan 10)]
    (async/pipeline 1 c xf chan)
    c))

(defn check-for-hook [x]
  (= hook-key (-> x :details :key)))

(defn trace [x] x)

(defn check-for-our-webhook [x]
  (and
   (= webhook-url (:url x))
   (:active x)))

;; ----- edit resources ------

(defn create-webhook
  "schedule creation of webhook - no error channel or error callback besides logging"
  [{:keys [server project username password url]} slug]
  (go
   (let [response (<! (http/post (repo-webhook-resource server project slug)
                                 {:json-params (webhook-data webhook-url)
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

(defn post-receive-hook-channel
  "scan all of the project hooks and send any hooks we care about to the out channel"
  [{:keys [server project username password]}]
  (add-transducer
   (filter check-for-hook)
   (bitbucket-resource-value-channel
    (project-hook-resource server project)
    username
    password)))

(defn slug-channel [{:keys [server project username password]}]
  (add-transducer
   (comp (map :slug) (map trace))
   (bitbucket-resource-value-channel
    (repo-resources server project)
    username
    password)))

(defn repo-webhooks-channel [{:keys [server project username password]} slug]
  (async/into []
              (add-transducer
               (comp (filter check-for-our-webhook))
               (bitbucket-resource-value-channel
                (repo-webhook-resource server project slug)
                username
                password))))

(defn check-webhook
  [config slug]
  (when slug
    (go
     (let [webhooks (<! (repo-webhooks-channel config slug))]
       (if (empty? webhooks)
         (create-webhook config slug)
         (log/info "active webhook for " slug))))))

(defn check-all-project-webhooks
  "read a project's repo slugs off of slug-channel and validate that all of the project webhooks are set up"
  [{:keys [server project username password] :as config} slug-channel]
  (go-loop
   [slug (<! slug-channel)]
   (check-webhook config slug)
   (if slug (recur (<! slug-channel)))))

(defn on-repo [{:keys [url] :as config} repo]
  (go
   (if-let [hook (<! (post-receive-hook-channel config))]
     (cond
       (not (:enabled hook))
       (do
         (log/info "enabling hook")
         (enable-hook config))
       :else
       (log/info "project hook enabled"))
     (log/info "there is no hook installed for" hook-key))
   (check-webhook config repo)))

(comment
 (on-repo config "chatswood2")
 (go (pprint (<! (post-receive-hook-channel config))))

 ;; fix all project webhooks in bibucket
 (check-all-project-webhooks config (slug-channel config)))
