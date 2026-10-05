(ns penpot.mcp.design.export
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.export.library :as library]
   [penpot.mcp.design.export.problems :as problems]
   [penpot.mcp.design.export.values :as values]))

(defn- combination-id [themes]
  (if (empty? themes) "default" (str/join ";" (map (fn [[g n]] (str (if (str/blank? g) "theme" g) "=" n)) themes))))

(defn- default-themes [themes]
  (reduce (fn [acc {:keys [group name active]}]
            (cond
              active (assoc acc group name)
              (contains? acc group) acc
              :else (assoc acc group name)))
          {}
          (sort-by (comp not :active) themes)))

(defn- descriptions [sets]
  (reduce (fn [acc {:keys [tokens]}]
            (reduce (fn [m {:keys [name description]}]
                      (if (seq description) (assoc m name description) (dissoc m name)))
                    acc tokens))
          {} sets))

(defn- path [name]
  (into [] (comp (map str/trim) (remove str/blank?)) (str/split (str name) #"\.")))

(defn- token-entry [id texts [name result]]
  (cond
    (:errors result) {:problem {:code :token-error :token name :combination id :errors (:errors result)}}
    :else (if-let [v (values/value result)]
            {:token (cond-> {:name name :path (path name) :type (:type result) :value v}
                      (get texts name) (assoc :description (get texts name)))}
            {:problem {:code :unsupported-value :token name :combination id}})))

(defn- combination [default texts {:keys [themes tokens failure warnings]}]
  (let [id (combination-id themes)]
    (if failure
      {:problems [(assoc failure :combination id)]}
      (let [entries (map #(token-entry id texts %) tokens)]
        {:combination {:id id :themes themes :default? (= default themes) :tokens (into [] (keep :token) entries)}
         :problems (concat (map #(assoc % :combination id) warnings) (keep :problem entries))}))))

(defn- shape [{:keys [kind] :as v}]
  (case kind
    :typography [kind (set (keys (:fields v)))]
    :shadow [kind (count (:layers v))]
    [kind]))

(defn- uniform [combinations]
  (let [shapes (map (fn [c] (into {} (map (juxt :name (comp shape :value))) (:tokens c))) combinations)
        names  (into [] (comp (mapcat :tokens) (map :name) (distinct)) combinations)]
    (filterv (fn [n] (and (every? #(contains? % n) shapes) (apply = (map #(get % n) shapes)))) names)))

(defn model [{:keys [sets themes colors typographies warnings]} resolution]
  (let [default  (default-themes themes)
        texts    (descriptions sets)
        parts    (map #(combination default texts %) (:combinations resolution))
        combos   (into [] (keep :combination) parts)
        typed    (uniform combos)
        palette  (library/colors colors)
        missing  (when (and (seq combos) (not-any? :default? combos))
                   [{:code :default-combination-failed :combination (combination-id default)}])]
    {:combinations combos
     :uniform typed
     :library {:colors (:colors palette) :typographies (library/typographies typographies)}
     :problems (vec (concat warnings
                            (:warnings resolution)
                            (mapcat :problems parts)
                            missing
                            (:problems palette)))}))

(defn problems [raw]
  (problems/normalize raw))
