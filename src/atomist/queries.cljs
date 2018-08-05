(ns atomist.queries
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [cljs.core.async :refer [<! >! chan close!] :as async]
   [http.client :as http]
   [goog.json :as json]
   [goog.string :as gstring]
   [goog.string.format]
   [eulalie.core :as eulalie]
   [eulalie.dynamo]
   [cljs.pprint :refer [pprint]]
   [atomist.vault :as vault]
   #_[hildebrand.channeled :refer [query! scan!]]))

(defn scan->chan [creds-chan table]
  (go
   (let [creds (<! creds-chan)
         response (<!
                   (eulalie/issue-request!
                    {:creds creds
                     :service :dynamo
                     :target :scan
                     :body {:tableName (name table)}}))]
     (-> response
         :body
         :items))))

(defn scan-webhook-audits []
  (go (->> (<! (scan->chan (vault/aws-creds->chan) :prod.atomist.services.WebhookAudit))
           (partition-by :team-id)
           (map (fn [coll] {:team-id (-> coll first :team-id)
                            :hooks (->> coll (map :name) (into []))
                            :counts (->> coll (map :counter) (into []))}))
           pprint)))

;; Dynamo scan
(comment
 (go (println (<! (scan-webhook-audits)))))

;; neo query
(comment
 (go (let [body {:statements [{:statement "MATCH (n:Team {id: \"T095SFFBK\"})-[]-(p:SCMProvider) return n,p"}]}
           response (<! (http/post (str neo "transaction/commit")
                                   {:basic-auth []          ;; TODO
                                    :json-params body}))]
       (-> response
           :body
           :results
           (cljs.pprint/pprint)))))







