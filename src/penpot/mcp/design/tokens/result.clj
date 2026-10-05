(ns penpot.mcp.design.tokens.result
  (:require
   [penpot.mcp.design.color :as color]
   [penpot.mcp.design.tokens.parse :as parse]))

(defn- present [m]
  (into {} (filter (comp some? val)) m))

(defn- keywordized [v]
  (cond
    (map? v) (into {} (map (fn [[k x]] [(keyword k) (keywordized x)])) v)
    (vector? v) (mapv keywordized v)
    :else v))

(defn- export-result [type resolved]
  (let [parsed (parse/parse-resolved type resolved)
        base   (assoc parsed :type type :resolved (keywordized resolved))]
    (cond
      (:errors parsed) base
      (= :color type) (-> base (dissoc :unit) (assoc :format (:unit parsed) :rgba (color/rgba (:value parsed))))
      :else (present base))))

(defn- blocked-reference [result blocked]
  (some (fn [{:keys [code value]}]
          (when (= :missing-reference code)
            (some #(when (contains? blocked %) %) value)))
        (:errors result)))

(defn- with-blocked-references [result blocked]
  (if-let [name (blocked-reference result blocked)]
    (assoc result :errors [{:code :rejected-reference :value name}])
    result))

(defn token-result [{:keys [values failures]} rejected {:keys [name type value]}]
  (cond
    (nil? value) {:errors [{:code :empty-input}]}
    (contains? rejected name) {:errors [(get rejected name)]}
    (contains? failures name) {:errors [{:code (get failures name)}]}
    (not (contains? values name)) {:errors [{:code :name-collision :value name}]}
    :else (with-blocked-references (export-result type (get values name))
            (into (set (keys rejected)) (keys failures)))))
