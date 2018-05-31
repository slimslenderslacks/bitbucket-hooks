(ns atomist.cljs-log
  (:require [goog.string :as gstring]
            [goog.string.format]))

(defn- log [& args]
  (.info js/console (apply str args)))
(def warn log)
(def info log)

(defn infof [s & args]
  (info (gstring/format s args)))