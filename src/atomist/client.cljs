(ns atomist.client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [http.client :as http]
            [cljs.core.async :refer [<! >! chan close!] :as async]
            [cljs.pprint :refer [pprint]]
            [goog.string :as gstring]
            [goog.string.format]))

(defn get-webhook-names []
  (go (->> (<! (http/get "http://localhost:8083/expose/webhooks"))
           :body
           (map :name)
           (pprint))))

(defn get-webhook [s]
  (go (->> (<! (http/get (gstring/format "http://localhost:8083/expose/webhooks/%s" s)))
           :body
           (pprint))))

(defn get-providers [teamid]
  (go (->> (<! (http/get (gstring/format "http://localhost:8086/private/providers") {:query-params {"teamid" teamid}}))
           :body
           (pprint))) )

(defn create-provider [teamid]
  (go (->> (<! (http/post "http://localhost:8086/private/providers/bitbucket"
                          {:json-params {:client-id "client-id"
                                         :client-secret "client-secret"
                                         :url "https://bitbucket.com"
                                         :team-id teamid
                                         :api-url "https://bitbucket.com"}}))
           :body
           (pprint))))

(comment
  (create-provider)
  (get-webhook "bitbucket-provider")
  (get-providers "AH8M1K4LJ"))

(defn new-repo [project repo-slug url username password]
  (let [hooks (filter post-receive-hook (bitbucket-pager fetch 0 nil))]
    (cond
      (empty? hooks)
      )))


(defn hook-resource [hook-key]
  (gstring/format (str bitbucket-server "rest/api/1.0/hooks/%s") hook-key))

(defn project-hook-resource-settings [project-name hook-key]
  (gstring/format (str bitbucket-server "rest/api/1.0/projects/%s/settings/hooks/%s/settings") project-name hook-key))

(defn repo-webhook-resource [project-name repo-slug]
  (gstring/format (str bitbucket-server "rest/api/1.0/projects/%s/repos/%s/webhooks") project-name repo-slug))

(go (->>
     (<! (http/get (project-hook-resource "SLIM") {:basic-auth ["slimslenderslacks" "slimslenderslacks"]}))
     :body
     :values
     (map #(if (:enabled %)
             (pprint (:details %))))
     (doall)))

(go (->>
     (<! (http/get (repo-webhook-resource "SLIM" "bitbucket54-slim-test") {:basic-auth ["slimslenderslacks" "slimslenderslacks"]}))
     :body
     pprint))

(go (->>
     (<! (http/get (project-hook-resource-settings "SLIM" "com.atlassian.stash.plugin.stash-web-post-receive-hooks-plugin:postReceiveHook")
                   {:basic-auth ["slimslenderslacks" "slimslenderslacks"]}))
     :body
     (pprint)))

(-> (hook-resource "com.atlassian.stash.plugin.stash-web-post-receive-hooks-plugin:postReceiveHook")
    (pprint-get-body))

(-> (repo-webhook-resource "SLIM" "bitbucket54-slim-test")
    (pprint-get-body))
