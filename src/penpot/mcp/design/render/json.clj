(ns penpot.mcp.design.render.json
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [penpot.mcp.design.js.number :as jsnum]))

(def ^:private step "  ")

(defn string [s]
  (json/write-str (str s)))

(declare write)

(defn- padding [depth]
  (apply str (repeat depth step)))

(defn- write-map [m depth]
  (let [rows (map (fn [[k v]] (str (padding (inc depth)) (string (name k)) ": " (write v (inc depth)))) m)]
    (str "{\n" (str/join ",\n" rows) "\n" (padding depth) "}")))

(defn- write-vector [v depth]
  (let [rows (map #(str (padding (inc depth)) (write % (inc depth))) v)]
    (str "[\n" (str/join ",\n" rows) "\n" (padding depth) "]")))

(defn write [v depth]
  (cond
    (and (coll? v) (empty? v)) (if (map? v) "{}" "[]")
    (map? v) (write-map v depth)
    (sequential? v) (write-vector v depth)
    (string? v) (string v)
    (number? v) (jsnum/to-string v)
    (boolean? v) (str v)
    :else "null"))

(defn object [& kvs]
  (apply array-map kvs))

(defn pretty [v]
  (str (write v 0) "\n"))
