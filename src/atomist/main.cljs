(ns atomist.main
  (:require [atomist.bitbucket :as bb]
            [cljs-node-io.core :as io :refer [slurp spit]]
            [cljs.analyzer :as cljs]
            [cljs.spec.alpha :as s]
            [clojure.pprint :refer [pprint]]
            [atomist.cljs-log :as log]))

(defn ^:export checkHook []
  (bb/check-hook))

(defn ^:export onRepo [server project repo-slug user password]
  (bb/on-repo server project repo-slug user password))

(defn noop []
  (println "exporting ..."))

(set! *main-cli-fn* noop)
