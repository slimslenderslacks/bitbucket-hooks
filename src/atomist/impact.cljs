(ns atomist.impact
  (:require [atomist.json :as json]
            [clojure.data]
            [atomist.graphql :refer [get-fingerprint-data]]
            [atomist.cljs-log :as log]))

(defn- push-impact?
  [x]
  (->> x
       (map #(second %))
       (apply +)
       (< 0)))

(defn- get-repo-details [event]
  (cond
    (-> event :data :PushImpact)
    [(-> event :data :PushImpact first :push :after :repo :org :owner)
     (-> event :data :PushImpact first :push :after :repo :name)
     (-> event :data :PushImpact first :push :after :repo :channels first :name)]))

(defn- perform
  "action callback - channel-name could be nil"
  [event action]
  (log/infof "Running impact action %s" (:action action))
  (let [[owner repo channel-name] (get-repo-details event)]
    (assert (and owner repo))
    ((:action action) event owner repo channel-name (dissoc action :action))))

(defn- diff-fingerprint-data
  [fp-data1 fp-data2]
  (zipmap
   [:from :to]
   (clojure.data/diff
    (into #{} fp-data1)
    (into #{} fp-data2))))

(defn- diff-handler [handlers {:keys [event fp-name team-id from-commit to-commit from to] :as o}]
  (let [filtered (->> (or handlers [])
                      (filter #((:selector %) o)))]
    (if (not (empty? filtered))
      (let [from-data (get-fingerprint-data team-id from-commit from)
            to-data (get-fingerprint-data team-id to-commit to)
            data (diff-fingerprint-data from-data to-data)]
        (->> filtered
             (mapcat #(->> ((:prepare-actions %) (assoc o :from-data from-data :to-data to-data :diff-data data))
                           (map (fn [m] (if m (assoc m :action (:action %))))))))))))

(defn- diff-fp
  "Check one fingerprint between a from and to Commit.
   Plan any downstream activities by creating a vector of Action maps.

     - We will never plan any actions if the from commit is null
     - different Fingerprint names can have different planning strategies"
  [fp-name team-id from-commit to-commit event]
  (let [from (->> from-commit :fingerprints (some #(if (= fp-name (:name %)) %)))
        to (->> to-commit :fingerprints (some #(if (= fp-name (:name %)) %)))
        o (zipmap [:fp-name :team-id :from-commit :to-commit :event :from :to]
                  [fp-name team-id from-commit to-commit event from to])
        diffs (concat
               []
               (if (and from (not (= (:sha from) (:sha to))))
                 (diff-handler (:handlers event) o))
               (diff-handler (:no-diff-handlers event) o))]
    diffs))

(defn get-team-id [event]
  (or (-> o :extensions :team_id)
      (-> o :team :id)))

(defn- check-push-impact
  "iterate over each after fingerprint and compute impact"
  [event]
  (let [fp-data (-> event :data :PushImpact first)
        sha-impacts? (push-impact? (some-> fp-data :data (json/read-str :key-fn keyword)))
        before (-> fp-data :push :before)
        after (-> fp-data :push :after)]
    (log/info "sha-impacts" sha-impacts? "push impact id is" (-> event :data :PushImpact first :id))
    (->> (map :name (-> fp-data :push :after :fingerprints))
         (mapcat #(try
                    (diff-fp % (get-team-id event) before after event)
                    (catch Throwable t
                      (log/warnf t "Error diffing fingerprint %s" %))))
         (remove nil?))))

;; -----------------------------
;;      handlers
;; -----------------------------

(defn process-push-impact [event handlers no-diff-handlers]
  (let [trace (fn [x] (log/info "perform " (-> x keys)) x)]
    (try
      (->> (check-push-impact (assoc event :handlers handlers :no-diff-handlers no-diff-handlers))
           (filter identity)
           (map trace)
           (map (partial perform event))
           (doall))
      (catch Throwable t
        (log/error t (.getMessage t))
        (log/error "processing " (-> event :data :PushImpact first :data))))))


