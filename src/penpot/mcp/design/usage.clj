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

(defn- add-page-usage [acc {:keys [page-id page usage]}]
  (reduce (fn [m [name {:keys [shapes copies attributes]}]]
            (update m name (fn [u] (-> (or u {:shapes 0 :copies 0 :attributes #{} :pages []})
                                       (update :shapes + shapes)
                                       (update :copies + copies)
                                       (update :attributes into attributes)
                                       (update :pages conj {:id page-id :name page :shapes shapes})))))
          acc
          usage))

(defn- usage-entries [merged pred]
  (into [] (comp (filter (comp pred key))
                 (map (fn [[name u]] (assoc u :name name :attributes (vec (sort (:attributes u)))))))
        (sort-by key merged)))

(defn raw-window [offset limit page-id]
  {:offset offset :limit limit :page-id page-id :count 0 :groups []})

(defn- add-group [{:keys [offset limit] :as window} group]
  (let [values (:values group)
        start  (:count window)
        from   (max offset start)
        to     (min (+ offset limit) (+ start (count values)))]
    (cond-> (update window :count + (count values))
      (< from to) (update :groups conj (assoc group :values (subvec values (- from start) (- to start)))))))

(defn add-raw [window {:keys [page-id page raw]}]
  (if (and (:page-id window) (not= page-id (:page-id window)))
    window
    (reduce add-group window (map #(assoc % :page-id page-id :page page) raw))))

(defn with-matches [scale-tokens groups]
  (if scale-tokens
    (mapv (fn [g] (update g :values (fn [vs] (mapv #(assoc % :matches (scale/matches scale-tokens %)) vs)))) groups)
    (vec groups)))

(defn report [{:keys [tokens facts]}]
  (let [names        (token-names tokens)
        graph        (references/graph tokens)
        merged       (reduce add-page-usage {} facts)
        applied      (into (sorted-set) (filter names) (keys merged))
        live         (references/live graph applied)
        dead         (into (sorted-set) (remove live) names)
        through-refs (into [] (comp (filter live) (remove applied)) names)
        missing      (usage-entries merged (complement names))]
    {:summary {:tokens (count names)
               :applied (count applied)
               :missing (count missing)
               :referenced-only (count through-refs)
               :unused (count dead)
               :shapes (reduce + (map :shapes facts))}
     :unused (vec (unused tokens dead))
     :referenced-only through-refs
     :references (vec (references graph live))
     :usage (usage-entries merged names)
     :missing missing}))
