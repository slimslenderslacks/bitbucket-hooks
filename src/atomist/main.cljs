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
       (log/info "run bitbucket project " config)
       (resolve (bb/check-all-project-webhooks config (bb/slug-channel config)))
       (catch :default e
         (log/warn "failure to run check bitbucket project " e)
         (reject e)))))
  )

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
