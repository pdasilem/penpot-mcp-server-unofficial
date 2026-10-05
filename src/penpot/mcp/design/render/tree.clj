(ns penpot.mcp.design.render.tree
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.render.naming :as naming]
   [penpot.mcp.design.render.table :as table]))

(defn- ident [segments]
  (str/join "." segments))

(defn- prefixes [segments]
  (map #(subvec segments 0 %) (range 1 (count segments))))

(defn- drop-group-clashes [entries]
  (let [taken (into #{} (mapcat (comp prefixes :segments)) entries)]
    (reduce (fn [acc e]
              (if (contains? taken (:segments e))
                (update acc :problems conj {:code :name-collision :identifier (ident (:segments e)) :tokens [(:name e)]})
                (update acc :entries conj e)))
            {:entries [] :problems []}
            entries)))

(defn entries [model segments-of typed?]
  (let [listed   (concat (table/tokens model) (table/library model))
        typed    (if typed? (table/typed model listed) {:entries listed :problems []})
        all      (->> (:entries typed)
                      (map #(assoc % :segments (segments-of %)))
                      (filter (comp seq :segments)))
        resolved (naming/resolve-collisions all (comp ident :segments) :values)
        pruned   (drop-group-clashes (:entries resolved))]
    {:entries (:entries pruned)
     :problems (-> (:problems typed) (into (:problems resolved)) (into (:problems pruned)))}))

(defn nest
  ([entries] (nest entries 0))
  ([entries depth]
   (let [order  (distinct (map #(nth (:segments %) depth) entries))
         groups (group-by #(nth (:segments %) depth) entries)]
     (mapv (fn [k]
             (let [members (get groups k)
                   leaf    (first (filter #(= (inc depth) (count (:segments %))) members))]
               (if leaf
                 {:key k :entry leaf}
                 {:key k :children (nest members (inc depth))})))
           order))))
