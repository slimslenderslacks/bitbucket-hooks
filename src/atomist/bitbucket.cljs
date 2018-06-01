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

(defn hook-resource [server hook-key]
  (gstring/format (str server "rest/api/1.0/hooks/%s") hook-key))

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
  (= "com.atlassian.stash.plugin.stash-web-post-receive-hooks-plugin:postReceiveHook" (-> x :details :key)))

(defn trace [x]
  x)

(defn check-for-our-webhook [x]
  (and
   (= webhook-url (:url x))
   (:active x)))

(defn create-webhook
  "schedule creation of webhook - no error channel or error callback - just log errors to console"
  [server project slug username password webhook-url]
  (go
   (let [response (<! (http/post (repo-webhook-resource server project slug)
                                 {:json-params (webhook-data webhook-url)
                                  :basic-auth [username password]}))]
     (if (= 201 (:status response))
       (log/info "successfully created webhook " (-> response :body :id))
       (log/error "error creating webhook " (-> response :status))))))

(defn check-webhooks
  "read a project's repo slugs off of slug-channel and validate that all of the project webhooks are set up"
  [{:keys [server project username password]} slug-channel]
  (go-loop
   [slug (<! slug-channel)]
   (when slug
     (log/info "------" slug "------")
     (let [webhooks (<! (async/into
                         [] (add-transducer
                             (comp (filter check-for-our-webhook))
                             (bitbucket-resource-value-channel
                              (repo-webhook-resource server project slug)
                              username
                              password))))]
       (if (empty? webhooks)
         (create-webhook server project slug username password webhook-url)
         (log/info "active webhook for " slug))))
   (if slug (recur (<! slug-channel)))))

(defn post-receive-hook-channel
  "scan all of the project hooks and send any hooks we care about to the out channel"
  [{:keys [server project username password]}]
  (add-transducer
   (filter check-for-hook)
   (bitbucket-resource-value-channel
    (project-hook-resource server project)
    username
    password)))

(defn check-hook
  [config]
  (let [c (chan)]
    (check-for-postReceiveHook config)
    (go (if-let [hook (<! c)]
          (pprint hook)))))

(defn on-repo [{:keys [server project username password]} repo]
  (println server project repo username password))

(def config {:server bitbucket-server
             :project "SLIM"
             :username "slimslenderslacks"
             :password "slimslenderslacks"})

(defn slug-channel [{:keys [server project username password]}]
  (add-transducer
   (comp (map :slug) (map trace))
   (bitbucket-resource-value-channel
    (repo-resources server project)
    username
    password)))

(comment
 (go (pprint (<! (post-receive-hook-channel config))))
 (check-webhooks config (slug-channel config)))
