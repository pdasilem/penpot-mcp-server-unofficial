(ns penpot.mcp.json
  (:require
   [clojure.data.json :as data.json]
   [clojure.string :as str]
   [penpot.mcp.penpot.tokens-lib :as ctob]
   [penpot.mcp.penpot.types :as types])
  (:import
   (java.time Instant)
   (java.util Date)
   (penpot.mcp.penpot.types Matrix Point)))

(defn- key-name [k]
  (cond
    (keyword? k) (str/replace (subs (str k) 1) "-" "_")
    (uuid? k) (str k)
    :else (str k)))

(declare plain)

(defn- record-fields [r ks]
  (into {} (map (fn [k] [(name k) (plain (get r k))])) ks))

(defn plain [x]
  (cond
    (or (nil? x) (string? x) (number? x) (boolean? x)) x
    (keyword? x) (subs (str x) 1)
    (uuid? x) (str x)
    (instance? Instant x) (str x)
    (instance? Date x) (str (.toInstant ^Date x))
    (instance? Matrix x) (record-fields x [:a :b :c :d :e :f])
    (instance? Point x) (record-fields x [:x :y])
    (types/path-data? x) (str x)
    (ctob/tokens-lib? x) (ctob/export-dtcg-json x)
    (map? x) (into {} (map (fn [[k v]] [(key-name k) (plain v)])) x)
    (or (sequential? x) (set? x)) (mapv plain x)
    :else (str x)))

(defn- empty-value? [v]
  (or (nil? v) (and (coll? v) (empty? v))))

(defn- prune-nested [x]
  (cond
    (map? x) (into {} (keep (fn [[k v]] (if (= "changed" k)
                                          [k v]
                                          (let [v (prune-nested v)] (when-not (empty-value? v) [k v])))))
                   x)
    (vector? x) (mapv prune-nested x)
    :else x))

(defn prune [x]
  (if (map? x)
    (into {} (keep (fn [[k v]] (when (some? v) [k (if (= "changed" k) v (prune-nested v))]))) x)
    (prune-nested x)))

(defn write-str [x]
  (data.json/write-str (plain x)))
