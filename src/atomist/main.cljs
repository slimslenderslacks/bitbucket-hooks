(ns atomist.main
  (:require [atomist.bitbucket :as bb]
            [cljs-node-io.core :as io :refer [slurp spit]]
            [cljs.analyzer :as cljs]
            [cljs.spec.alpha :as s]
            [clojure.pprint :refer [pprint]]
            [atomist.cljs-log :as log]))

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

(defn ^:export testCallback [config callback]
  (let [c (js->clj config :keywordize-keys true)]
    (log/info "first" c)
    (println "second" c)
    (.apply callback callback (into-array ["a"]))))

(defn noop []
  (println "exporting ..."))

(set! *main-cli-fn* noop)
