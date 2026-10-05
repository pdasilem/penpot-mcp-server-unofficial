(ns penpot.mcp.design.sd.references
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.budget :as budget]
   [penpot.mcp.design.js.data :as jsdata]))

(def ^:private reference #"\{[^}]+\}")

(defn uses-references? [v]
  (cond
    (string? v) (boolean (re-find reference v))
    (map? v) (boolean (some uses-references? (vals v)))
    (sequential? v) (boolean (some uses-references? v))
    :else false))

(def ^:private max-depth 500)

(def ^:private max-length 1000)

(defn- bounded [v]
  (if (and (string? v) (> (count v) max-length))
    (throw (ex-info "Token value is too long after references are resolved" {:type :penpot.mcp.design.sd/too-long}))
    v))

(declare resolve-string)

(defn- resolve-match [token-map ignored state match]
  (let [trimmed (str "{" (str/trim (subs match 1 (dec (count match)))) "}")
        ref     (get-in token-map [trimmed "value"] :undefined)
        replace (fn [v] (bounded (if (= match (:value state)) v (jsdata/replace-first (jsdata/to-string (:value state)) match (jsdata/to-string v)))))]
    (cond
      (contains? ignored match) state
      (= :undefined ref) state
      (and (string? ref) (uses-references? ref))
      (cond
        (contains? @(:found-circ state) ref) state
        (some #{ref} (conj (:stack state) match)) (let [stack (conj (:stack state) match)
                                                        idx   (.indexOf ^java.util.List stack ref)]
                                                    (swap! (:found-circ state) into (subvec stack idx))
                                                    state)
        :else (assoc state :value (replace (resolve-string ref token-map ignored (conj (:stack state) match) (:found-circ state)))))
      :else (assoc state :value (replace ref)))))

(defn- resolve-string [value token-map ignored stack found-circ]
  (budget/check!)
  (when (> (count stack) max-depth)
    (throw (ex-info "Token references are nested too deeply or circular" {:type :penpot.mcp.design.sd/circular})))
  (:value (reduce (partial resolve-match token-map ignored)
                  {:value value :stack stack :found-circ found-circ}
                  (map first (re-seq #"(\{[^}]+\})" value)))))

(defn- resolve-slice [slice token-map ignored found-circ stack]
  (cond
    (string? slice) (if (uses-references? slice) (resolve-string slice token-map ignored stack found-circ) slice)
    (map? slice) (reduce-kv (fn [m k v] (assoc m k (resolve-slice v token-map ignored found-circ []))) slice slice)
    (vector? slice) (mapv #(resolve-slice % token-map ignored found-circ []) slice)
    :else slice))

(defn- too-long? [e]
  (= :penpot.mcp.design.sd/too-long (:type (ex-data e))))

(defn- resolve-token [m k ignored found-circ]
  (try
    (update-in m [k "value"] resolve-slice m ignored found-circ [k])
    (catch clojure.lang.ExceptionInfo e
      (if (too-long? e) (assoc-in m [k "failure"] :value-too-long) (throw e)))))

(defn resolve-token-map [token-map order ignored]
  (let [found-circ (atom #{})]
    (reduce (fn [m k] (resolve-token m k ignored found-circ)) token-map order)))
