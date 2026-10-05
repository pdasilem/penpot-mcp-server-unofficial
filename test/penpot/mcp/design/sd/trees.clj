(ns penpot.mcp.design.sd.trees
  (:require
   [clojure.string :as str]))

(defn- sd-token [{:keys [name type value]}]
  {"name" name "type" (clojure.core/name type) "value" value})

(defn tree [tokens]
  (reduce-kv (fn [acc name token] (assoc-in acc (str/split name #"\.") (sd-token token)))
             {}
             (into {} (comp (filter #(some? (:value %))) (map (juxt :name identity))) tokens)))
