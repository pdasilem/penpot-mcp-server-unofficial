(ns penpot.mcp.design.usage
  (:require
   [penpot.mcp.design.usage.references :as references]
   [penpot.mcp.design.usage.scale :as scale]
   [penpot.mcp.design.usage.shapes :as shapes]))

(defn page-facts [page]
  (shapes/facts page))

(defn- token-names [tokens]
  (into (sorted-set) (map :name) tokens))

(defn- unused [tokens names]
  (for [[name entries] (sort-by key (group-by :name tokens))
        :when (contains? names name)]
    {:name name
     :type (:type (first entries))
     :sets (mapv #(select-keys % [:set :value]) entries)}))

(defn- references [graph live]
  (for [[name refs] (sort-by key graph)
        :when (seq refs)]
    {:name name :references (vec (sort refs)) :live (contains? live name)}))

(defn- add-application [acc {:keys [name shape-id page-id page copy? attributes]}]
  (-> acc
      (update-in [name :shapes] (fnil conj #{}) shape-id)
      (update-in [name :copies] (fnil into #{}) (when copy? [shape-id]))
      (update-in [name :attributes] (fnil into #{}) attributes)
      (update-in [name :pages page-id] (fn [p] (-> (or p {:id page-id :name page :order (count (get-in acc [name :pages])) :shapes #{}})
                                                    (update :shapes conj shape-id))))))

(defn- usage-entry [[name {:keys [shapes copies attributes pages]}]]
  {:name name
   :shapes (count shapes)
   :copies (count copies)
   :attributes (vec (sort attributes))
   :pages (mapv (fn [p] (-> p (dissoc :order) (update :shapes count))) (sort-by :order (vals pages)))})

(defn- usage [applications pred]
  (->> (filter #(pred (:name %)) applications)
       (reduce add-application {})
       (sort-by key)
       (mapv usage-entry)))

(defn with-matches [scale-tokens entries]
  (if scale-tokens
    (mapv #(assoc % :matches (scale/matches scale-tokens %)) entries)
    (vec entries)))

(defn report [{:keys [tokens facts]}]
  (let [names        (token-names tokens)
        graph        (references/graph tokens)
        applications (mapcat :applications facts)
        applied      (into (sorted-set) (comp (map :name) (filter names)) applications)
        live         (references/live graph applied)
        dead         (into (sorted-set) (remove live) names)
        through-refs (into [] (comp (filter live) (remove applied)) names)
        missing      (usage applications (complement names))
        raw          (into [] (mapcat :raw-values) facts)]
    {:summary {:tokens (count names)
               :applied (count applied)
               :missing (count missing)
               :referenced-only (count through-refs)
               :unused (count dead)
               :shapes (reduce + (map :shapes facts))
               :raw-values (count raw)}
     :unused (vec (unused tokens dead))
     :referenced-only through-refs
     :references (vec (references graph live))
     :usage (usage applications names)
     :missing missing
     :raw-values raw}))
