(ns atomist.main
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!] :as async]
            [atomist.bitbucket :as bb]
            [cljs-node-io.core :as io :refer [slurp spit]]
            [clojure.pprint :refer [pprint]]
            [atomist.cljs-log :as log]))

(defn ^:export converge [policy]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [c (js->clj policy :keywordize-keys true)]
         (log/infof "converge policy %s" c)
         (resolve (bb/check-all-project-webhooks c (bb/slug->channel c))))
       (catch :default e
         (log/warnf "failure to run check bitbucket project %s" e)
         (reject e))))))

(defn ^:export onRepo [config repo-slug]
  (js/Promise.
   (fn [resolve reject]
     (try
       (log/info "onRepo " config repo-slug)
       (resolve (bb/on-repo config repo-slug))
       (catch :default e
         (log/warn "failure to run onRepo " e)
         (reject e))))))

(defn noop []
  (println "exporting ..."))

(set! *main-cli-fn* noop)
