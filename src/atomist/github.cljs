(ns atomist.github
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [http.client :as http]
            [cljs.core.async :refer [<! >! chan close!] :as async]
            [cljs.pprint :refer [pprint]]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]))

(defn get-oauth-client-secret
  "returns [client-id client-secret]"
  []
  ["id" "s"])

(defn check-oauth-token [token]
  (let [[id _ :as cs] (get-oauth-client-secret)]
    (go (pprint (<! (http/get (gstring/format "https://api.github.com/applications/%s/tokens/%s" id token)
                              {:headers {"User-Agent" "atomist"}
                               :basic-auth cs}))))))


