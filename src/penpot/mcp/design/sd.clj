(ns penpot.mcp.design.sd
  (:require
   [penpot.mcp.design.sd.failure :as failure]
   [penpot.mcp.design.sd.pipeline :as pipeline]
   [penpot.mcp.design.sd.tree :as tree]))

(defn resolve-tree [tree]
  (binding [failure/*warnings* (atom [])]
    (let [tokens (vals (pipeline/run-passes (tree/token-map tree)))
          named  (map (fn [token] [(get-in token ["original" "name"]) token]) tokens)]
      {:values (into {} (map (fn [[n token]] [n (get token "value")])) named)
       :failures (into {} (keep (fn [[n token]] (when-let [f (get token "failure")] [n f]))) named)
       :warnings (vec (distinct @failure/*warnings*))})))
