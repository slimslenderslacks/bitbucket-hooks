(ns atomist.vault
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
   [kafkajs]))

(def url process.env.VAULT_ADDR)
(def token (slurp process.env.VAULT_TOKEN_PATH))
(def dynamo-url "https://dynamodb.us-west-2.amazonaws.com")

(defn- body-data [response]
  (->
   response
   :body
   :data))

(defn vault-path->chan
  "returns channel containing vault data"
  [domain path]
  (go (body-data (<! (http/get
                      (str url (gstring/format "/v1/secret/domains/%s/%s" domain path))
                      {:headers {"X-VAULT-TOKEN" token}})))))

(defn vault-full-path->chan
  "returns channel containing vault data"
  [path]
  (go (body-data (<! (http/get
                      (str url path)
                      {:headers {"X-VAULT-TOKEN" token}})))))

(defn neo-creds->chan
  "returns chan with {:username ... :password ...}"
  [domain]
  (vault-path->chan domain "neo4j/creds"))

(defn neo-url->chan
  "returns chan with url"
  [domain]
  (vault-path->chan domain "neo4j/url"))

(defn aws-creds->chan
  "channel should contain a set of dynamo creds {:access_key :secret_key :url}
   cache these creds so we don't grab them over and over again"
  []
  (go
   (let [f (io/file "/Users/slim/.atomist/creds-cache" "aws")]
     (io/make-parents f)
     (if (not (.exists f))
       (let [response (<! (vault-full-path->chan "/v1/aws/creds/dynamo"))
             creds-map (-> response
                           (select-keys [:access_key :secret_key])
                           (assoc :url dynamo-url)
                           (assoc :endpoint dynamo-url))]
         (io/make-parents f)
         (spit f (pr-str creds-map))
         creds-map)
       (read-string (slurp f))))))
