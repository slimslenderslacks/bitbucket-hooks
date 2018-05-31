(ns user
  (:require [cljs.repl :as repl]
            [tubular.core]
            [cljs.repl.node :as node]
            #_[cider.piggieback]))

(defn connect []
  (tubular.core/connect 7777))

#_(def repl-env (node/repl-env :port 7777))

#_(defn node-repl []
    (cider.piggieback/cljs-repl repl-env))

