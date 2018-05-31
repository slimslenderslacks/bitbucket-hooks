(ns http.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [http.util :as util]
            [cljs.nodejs :as node]
            [cljs.core.async :refer [chan >! <! put!] :as async]))

(def http (node/require "http"))
(def https (node/require "https"))

(defn ->node-req
  [req]
  (clj->js {:method (or (:request-method req) :get)
            :port (or (:server-port req) (if (= :https (:scheme req)) 443 80))
            :hostname (:server-name req)
            :path (str (:uri req) (if-let [s (:query-string req)] (str "?" s) ""))
            :headers (:headers req)}))

(defn clean-response
  [res]
  (assoc res :body (->> res :body clj->js (.concat js/Buffer) js->clj)
         :status (-> res :status first (or 200))
         :headers (->> res :headers (apply merge))))

(defn request
  "Execute the HTTP request using the node.js primitives"
  [{:keys [request-method headers body with-credentials?] :as request}]
  (let [timeout (or (:timeout request) 0)
        content-length (or (when body (.-length body)) 0)
        headers (util/build-headers (assoc headers "content-length" content-length))
        js-request (->node-req (assoc request :headers headers))
        scheme (if (= (:scheme request) :https) https http)
        ;; This needs a stream abstraction!
        chunks-ch (chan)
        response-ch (chan)
        client (.request scheme js-request
                         (fn [js-res]
                           (put! chunks-ch {:headers (-> js-res
                                                         (aget "headers")
                                                         (js->clj :keywordize-keys true))})
                           (put! chunks-ch {:status (.-statusCode js-res)})
                           (doto js-res
                             (.on "data" (fn [stuff] (put! chunks-ch {:body stuff})))
                             (.on "end" (fn [] (async/close! chunks-ch))))))]
    (go (loop [response {:status [] :body [] :headers []}]
          (let [stuff (<! chunks-ch)]
            (if (nil? stuff)
              (do
                (>! response-ch (clean-response response))
                (async/close! response-ch))
              (recur (merge-with conj response stuff))))))

    (when body
      (do (prn body) (.write client body)))

    (doto client
      (.on "error" (fn [error]
                     (do
                       (.log js/console (str "Error in request: " error))
                       (put! chunks-ch {:status -1 :error error})
                       (async/close! chunks-ch))))
      (.end))
    response-ch))
