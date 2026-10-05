(ns penpot.mcp.design.tokens.sd-input
  (:require
   [app.common.path-names :as cpn]))

(defn- sd-value [v]
  (cond
    (keyword? v) (name v)
    (map? v) (into {} (map (fn [[k x]] [(name k) (sd-value x)])) v)
    (sequential? v) (mapv sd-value v)
    (integer? v) (double v)
    :else v))

(defn- sd-token [{:keys [name type value]}]
  {"name" name "type" (clojure.core/name type) "value" (sd-value value)})

(defn valid-tokens [tokens]
  (into {} (comp (filter #(some? (:value %))) (map (juxt :name identity))) tokens))

(defn tree [valid]
  (reduce-kv (fn [acc _ token]
               (assoc-in acc (cpn/split-path (:name token) :separator ".") (sd-token token)))
             {}
             valid))
