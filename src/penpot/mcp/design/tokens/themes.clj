(ns penpot.mcp.design.tokens.themes
  (:require
   [linked.core :as linked]))

(defn- groups [themes]
  (reduce (fn [acc {:keys [group] :as theme}]
            (if-let [i (first (keep-indexed (fn [i [g _]] (when (= g group) i)) acc))]
              (update-in acc [i 1] conj theme)
              (conj acc [group [theme]])))
          []
          themes))

(defn- choices [themes]
  (reduce (fn [combos [_ members]]
            (for [combo combos theme members] (conj combo theme)))
          [[]]
          (reverse (groups themes))))

(defn- merged-tokens [sets active-names]
  (reduce (fn [acc {:keys [name tokens]}]
            (if (contains? active-names name)
              (into acc (map (juxt :name identity)) tokens)
              acc))
          (linked/map)
          sets))

(def ^:private max-combinations 64)

(defn- check-count! [themes]
  (let [n (reduce * 1 (map (comp count second) (groups themes)))]
    (when (> n max-combinations)
      (throw (ex-info (str "Themes make " n " combinations, more than " max-combinations)
                      {:type ::too-many-combinations :count n :limit max-combinations})))))

(defn combinations [{:keys [sets themes]}]
  (check-count! themes)
  (if (empty? themes)
    [{:themes {} :tokens (merged-tokens sets (into #{} (comp (filter :active) (map :name)) sets))}]
    (for [combo (choices themes)]
      {:themes (into (sorted-map) (map (juxt :group :name)) combo)
       :tokens (merged-tokens sets (into #{} (mapcat :sets) combo))})))

(defn unused-sets [{:keys [sets themes]}]
  (if (empty? themes)
    []
    (let [used (into #{} (mapcat :sets) themes)]
      (into [] (comp (map :name) (remove used)) sets))))
