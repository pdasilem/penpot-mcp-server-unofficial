(ns penpot.mcp.design.usage.references
  (:require
   [penpot.mcp.penpot.token :as cto]))

(defn- referenced [value]
  (into #{} (comp (filter string?) (mapcat cto/find-token-value-references)) (tree-seq coll? seq value)))

(defn graph [tokens]
  (reduce (fn [acc {:keys [name value]}]
            (update acc name (fnil into #{}) (referenced value)))
          {}
          tokens))

(defn live [graph roots]
  (loop [seen #{} pending (vec roots)]
    (if-let [name (peek pending)]
      (if (contains? seen name)
        (recur seen (pop pending))
        (recur (conj seen name) (into (pop pending) (get graph name))))
      seen)))
