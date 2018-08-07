(ns atomist.queries
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [cljs.core.async :refer [<! >! chan close!] :as async]
   [http.client :as http]
   [goog.json :as json]
   [goog.string :as gstring]
   [goog.string.format]
   [cljs.pprint :refer [pprint]]
   [atomist.vault :as vault]
   [dynamodb :as db]
   [clojure.walk :as walk]))

;; https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/GettingStarted.Js.04.html
(def ddb (atom nil))

(defn list-tables []
  (.listTables @ddb #js {}
               (fn [err data]
                 (doseq [t (js->clj (.-TableNames data))]
                   (println t)))))

(defn init-db []
  (go
   (let [ddb-constructor (.. dynamodb -AWS -DynamoDB)
         creds (<! (vault/aws-creds->chan))]
     (.update (.. dynamodb -AWS -config) (clj->js (assoc creds :region "us-west-2")))
     (swap! ddb (constantly (ddb-constructor.))))))

(defn type-result [m]
  (walk/prewalk #(cond
                   (and (map? %) (contains? % :S))
                   (:S %)
                   (and (map? %) (contains? % :L))
                   (:L %)
                   (and (map? %) (contains? % :N))
                   (int (:N %))
                   (and (map? %) (contains? % :M))
                   (:M %)
                   :else
                   %) m))

(defn items->clj [data]
  (:Items (js->clj data :keywordize-keys true)))

(defn scan-table [table-name filter-fn sort-fn map-fn]
  (.scan @ddb #js {"TableName" table-name}
         (fn [err data] (->> (-> data items->clj type-result)
                             (filter filter-fn)
                             (sort-by sort-fn)
                             (map map-fn)
                             (pprint)))))

(defn count-team-webhook-fires [type]
  (scan-table "prod.atomist.services.WebhookAudit"
              #(= (:name %) type)
              :counter
              #(gstring/format "%s -> %d" (get team-id->name (:team-id %)) (:counter %))))

(comment
 (init-db)

 (scan-table "prod.atomist.services.WebHooks" identity :name :name)
 (scan-table "prod.atomist.services.WebHooks" #(= (:name %) "gitlab-provider") :name identity)

 (count-team-webhook-fires "slack-event")
 (count-team-webhook-fires "slack")
 (count-team-webhook-fires "travis")
 (count-team-webhook-fires "build")
 (count-team-webhook-fires "github")
 (count-team-webhook-fires "github-provider")
 )

;; -------------- Direct Neo Queries -------------

(defn by-row [results]
  (->> (map :row (-> results first :data))
       (map #(zipmap (->> results first :columns (map keyword)) %))))

(defn create-team-id-name-map [results]
  (->> results
       (sort-by #(-> % :n :name))
       (map #(conj [] (-> % :n :atmTeamId) (-> % :n :name)))
       (into {})))

(defn neo-query->chan [domain]
  (go
   (let [neo-creds (<! (vault/neo-creds->chan domain))
         ;; {:value url} (<! (vault/neo-url->chan domain))
         url process.env.NEO_URL
         body {:statements [{:statement "MATCH (n:Team)-[]-(p:SCMProvider) return n,p"}]}
         response (<! (http/post (str url "transaction/commit")
                                 {:basic-auth [(:user neo-creds) (:password neo-creds)]
                                  :json-params body}))]
     (-> response
         :body
         :results))))

(comment
 (go (def data (-> (<! (neo-query->chan "prod.atomist.services."))
                   by-row
                   create-team-id-name-map)))
 (pprint data))
