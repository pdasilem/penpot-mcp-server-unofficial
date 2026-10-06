(ns penpot.mcp.penpot.names
  (:require
   [clojure.string :as str])
  (:import
   (java.util.regex Pattern)))

(defn trim [s]
  (when (string? s)
    (str/replace s #"^[\n\f\r\t ]+|[\n\f\r\t ]+$" "")))

(defn split-path
  ([path] (split-path path "/"))
  ([path separator]
   (into [] (comp (map trim) (remove empty?)) (str/split path (re-pattern (Pattern/quote separator))))))
