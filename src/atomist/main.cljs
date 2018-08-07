(ns atomist.main
  (:require [atomist.bitbucket :as bb]
            [cljs-node-io.core :as io :refer [slurp spit]]
            [clojure.pprint :refer [pprint]]
            [atomist.cljs-log :as log]))

(def bitbucket-server "http://bitbucket-server-54.atomist.com:7990/")
(def webhook-url "https://webhook-staging.atomist.services/atomist/bitbucket/teams/A4EOI5D1E/7cq796lgw39rlsi")

(def config {:server bitbucket-server
             :project "SLIM"
             :username "slimslenderslacks"
             :password "slimslenderslacks"
             :url webhook-url})

(defn ^:export checkProject [config]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [c (js->clj config :keywordize-keys true)]
         (log/info "run bitbucket project " c)
         (println "with println " c)
         (resolve (bb/check-all-project-webhooks c (bb/slug-channel c))))
       (catch :default e
         (log/warn "failure to run check bitbucket project " e)
         (reject e))))))

(defn ^:export onRepo [config repo-slug]
  (js/Promise.
   (fn [resolve reject]
     (try
       (log/info "run onRepo " config repo-slug)
       (resolve (bb/on-repo config repo-slug))
       (catch :default e
         (log/warn "failure to run onRepo " e)
         (reject e))))))

(defn noop []
  (println "exporting ..."))

(set! *main-cli-fn* noop)
