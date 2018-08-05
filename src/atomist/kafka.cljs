(ns atomist.kafka
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [cljs-node-io.core :as io :refer [slurp spit]]
   [cljs-node-io.file :as nodefile]
   [cljs.core.async :refer [<! >! chan close!] :as async]
   [http.client :as http]
   [atomist.json :as json]
   [goog.string :as gstring]
   [goog.string.format]
   [cljs.pprint :refer [pprint]]
   [cljs.reader :refer [read-string]]
   [kafkajs]
   [atomist.vault :refer [vault-path->chan]]))

(defn print-message-details [m message]
  (println "* topic partition" (get m "topic") (get m "partition"))
  (println "* message keys" (keys (get m "message")))
  (println "* offset" (get-in m ["message" "offset"]))
  (println "* timestamp" (get-in m ["message" "timestamp"]))
  (println "* key" (get-in m ["message" "key"]))
  (println "x-atomist-teamid" (get message "x-atomist-teamid"))
  (println "correlation-id" (get message "correlation-id")))

(defn kafka->chan
  "put a kafka broker on a chan and return the channel
     params
       creds-chan - a channel with the kafka creds to use (uses our standard kafka creds schema"
  [creds-chan]
  (go
   (let [kafka-creds (<! creds-chan)]
     (kafkajs/Kafka. (clj->js {:clientId "slim"
                               :logLevel (.-ERROR kafkajs/logLevel)
                               :brokers [(:brokers kafka-creds)]
                               :ssl {:rejectUnauthorized false
                                     :ca [(:ca-cert kafka-creds)]
                                     :cert (:access-cert kafka-creds)
                                     :key (:access-key kafka-creds)
                                     :passphrase (:store-pass kafka-creds)}})))))

(defn store-message-callback
  "store a kafka message's value on disk
   each message will have a filename ./data/${topic}-${partition}-${offset}.data"
  [message]
  (let [m (js->clj message)
        message (-> (get-in m ["message" "value"])
                    (.toString)
                    (json/json->clj :keyword-keys true))]
    (if (= "T04SYRAP3" (get message "x-atomist-teamid"))
      (spit
       (gstring/format "./data/%s-%s-%s.data" (get m "topic") (get m "partition") (get-in m ["message" "offset"]))
       (pr-str message)))))

(defn display-callback
  "print a kafka message to stdout"
  [message]
  (let [m (js->clj message)
        message (-> (get-in m ["message" "value"])
                    (.toString)
                    (json/json->clj :keyword-keys true))]
    (println (gstring/format "%s(%s)-%s %s" (get m "topic") (get m "partition") (get-in m ["message" "offset"]) message))))

(defn create-consumer->chan
  "create a chan with an active consumer
   consumer will be consuming topic using callback

   consumer on the chan can be used to disconnect the consumer"
  [topic callback]
  (go
   (let [kafka (<! (kafka->chan (vault-path->chan "prod.atomist.services." "kafka")))
         consumer (.consumer kafka (clj->js {:groupId "slim"}))]
     (.subscribe consumer (clj->js {:topic topic}))
     (.run consumer (clj->js {:eachMessage callback}))
     consumer)))

(defn create-producer-function->chan
  "create a producing function for a topic
   function will take 3 args - topic name, message key, and message"
  [topic]
  (go
   (let [kafka (<! (kafka->chan (vault-path->chan "prod.atomist.services." "kafka")))
         producer (.producer kafka)]
     (fn [topic key message] (.send producer (clj->js {:topic topic :messages [{:key key :value message}]}))))))

(comment
 "run a consumer - hold on to a var"
 (go (def consumer (<! (create-consumer->chan "git_incoming" display-callback))))
 "disconnect a consumer"
 (.then (.disconnect consumer) (fn [result] (println "disconnect " result)))
 "seek an active consumer to a different offset - continue consuming"
 (.seek consumer (clj->js {:topic "git_incoming" :partition 15 :offset 273687}))
 "not really sure what this is for"
 (.then (.describeGroup consumer) (fn [result] (pprint (js->clj result)))))

(comment
 "create a var containing a producing function"
 (go (def producer-fn (<! (create-producer-function->chan "topic")))))

;;
;; ------------------------
;;

(def paths [["object_kind"] ["project_id"] ["project" "namespace"] ["repository" "name"]])
(defn display [[f m]]
  (println f " " (apply str (interpose " " (map #(str (get-in m %)) paths)))))

(comment
 "filter all pushes and merge_requests out of this stream of gitlab events"
 (->>
  (for [f (io/file-seq "./data") :let [ff (io/file f)] :when (not (.isDirectory ff))]
    [f (-> (slurp f) (read-string))])
  (filter #(contains? (second %) "type"))
  (filter #(#{"push" "merge_request"} (get (second %) "object_kind")))
  (map display)))

(comment
 ""
 (pprint (read-string (slurp "./data/git_incoming-8-277908.data"))))
