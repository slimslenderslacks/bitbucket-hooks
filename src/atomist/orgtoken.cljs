(ns atomist.orgtoken
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [http.client :as http]
            [cljs.core.async :refer [<! >! chan close!] :as async]
            [cljs.pprint :refer [pprint]]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]))

(def org-token "")  ;; vault read secret/domains/prod.atomist.services./providers/zjlmxjzwhurspem/teams/T5964N9B7/orgs/spring-team/github
(def team-token "") ;; vault read secret/domains/prod.atomist.services./providers/zjlmxjzwhurspem/teams/T5964N9B7/github
(def client-id "")
(def client-secret "") ;; vault read secret/domains/prod.atomist.services./ghe/zjlmxjzwhurspem/oauth2

(defn check-token [token]
  (go (pprint (<!
               (http/get
                (gstring/format "https://api.github.com/applications/%s/tokens/%s" client-id token)
                {:headers {"User-Agent" "atomist"}
                 :basic-auth [client-id client-secret]})))))




