(ns atomist.encrypt
  (:require [cljs-node-io.core :as io :refer [slurp spit]]
            [cljs.reader :refer [read-string]]
            [goog.json :as json]
            [cljs.core :refer [drop map?]]
            [clojure.string :refer [split]]
            [clojure.pprint :refer [pprint]]
            [goog.crypt.base64 :as b64]
            [goog.string :as gstring]
            [goog.string.format]
            [goog.crypt :as crypt]
            [goog.crypt.Aes :as aes]))

(defn generate-key []
  (let [t (take 16 (repeatedly #(rand-int 20)))]
    (spit "key.txt" (print-str t))))

(defn read-key []
  (->> (slurp "key.txt")
       (read-string)
       (into [])
       (clj->js)
       (goog.crypt.Aes.)))

(defn block-size [k]
  (.-BLOCK_SIZE k))

(defn s->blocks [n s]
  (->> (seq s)
       (map #(.charCodeAt % 0))
       (partition 16 16 (take 16 (repeat (.charCodeAt " " 0))))))

(defn encrypt [k s]
  (->>
   (s->blocks (block-size k) s)
   (map #(js->clj (.encrypt k (clj->js %))))
   (flatten)))

(defn decrypt [k cypher]
  (->>
   (partition 16 cypher)
   (map #(js->clj (.decrypt k (clj->js %))))
   (flatten)
   (map #(char %))
   (apply str)
   (gstring/trim)))

(defn encrypt-vault [m]
  (->> (pr-str m)
       (encrypt (read-key))
       (pr-str)
       (spit "vault.txt")))

(defn decrypt-vault []
  (->> (slurp "vault.txt")
       (read-string)
       (decrypt (read-key))
       (read-string)))

(comment
  (encrypt-vault {:github-token ""
                  :artifactory-user ""
                  :artifactory-pwd ""})
  (pprint (decrypt-vault)))
