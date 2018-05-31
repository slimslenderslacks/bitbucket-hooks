(ns atomist.bitbucket
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [http.client :as http]
            [cljs.core.async :refer [<! >! chan close!] :as async]
            [cljs.pprint :refer [pprint]]
            [goog.string :as gstring]
            [goog.string.format]))

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

(defn bitbucket-resource-value-channel [url username password]
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

(defn check-for-our-webhook [x]
  (and
   (= webhook-url (:url x))
   (:active x)))

(defn create-webhook [server project slug username password webhook-url]
  (go
   (let [response (<! (http/post (repo-webhook-resource server project slug)
                                 {:json-params (webhook-data webhook-url)
                                  :basic-auth [username password]}))]
     (if (= 201 (:status response))
       (println "successfully created webhook " (-> response :body :id))
       (pprint response)))))

(defn check-webhooks [slug-channel]
  (go-loop
   [slug (<! slug-channel)]
   (when slug
     (println "------" slug "------")
     (let [webhooks (<! (async/into [] (add-transducer (filter check-for-our-webhook) (bitbucket-resource-value-channel (repo-webhook-resource bitbucket-server "SLIM" slug) "slimslenderslacks" "slimslenderslacks"))))]
       (if (empty? webhooks)
         (create-webhook bitbucket-server "SLIM" slug "slimslenderslacks" "slimslenderslacks" webhook-url)
         (println "active webhook for " slug))))
   (if slug (recur (<! slug-channel)))))

(defn check-hook []
  (go (pprint (<! (add-transducer
                   (filter check-for-hook)
                   (bitbucket-resource-value-channel
                    (project-hook-resource bitbucket-server "SLIM")
                    "slimslenderslacks" "slimslenderslacks"))))))

(defn on-repo [server project repo username password]
  (println server project repo username password))

(comment
 (go (<! (check-webhooks (add-transducer (map :slug) (bitbucket-resource-value-channel (repo-resources bitbucket-server "SLIM") "slimslenderslacks" "slimslenderslacks"))))) ())
