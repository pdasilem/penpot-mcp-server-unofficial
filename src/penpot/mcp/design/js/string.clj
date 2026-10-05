(ns penpot.mcp.design.js.string
  (:require
   [clojure.string :as str])
  (:import
   (java.util Locale)))

(def whitespace
  "\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff")

(def ^:private edges
  (re-pattern (str "^[" whitespace "]+|[" whitespace "]+\\z")))

(defn trim [s]
  (str/replace s edges ""))

(defn char-at [^String s i]
  (when (< -1 i (.length s))
    (.charAt s (int i))))

(defn digit? [c]
  (and (some? c) (<= 48 (int c) 57)))

(defn lower-case [^String s]
  (.toLowerCase s Locale/ROOT))

(defn upper-case [^String s]
  (.toUpperCase s Locale/ROOT))
