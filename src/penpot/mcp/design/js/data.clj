(ns penpot.mcp.design.js.data
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.js.number :as jsnum]))

(defn to-string [v]
  (cond
    (string? v) v
    (number? v) (jsnum/to-string v)
    (boolean? v) (str v)
    (nil? v) "null"
    (= :undefined v) "undefined"
    (sequential? v) (str/join "," (map #(if (or (nil? %) (= :undefined %)) "" (to-string %)) v))
    (map? v) "[object Object]"
    :else (str v)))

(defn- js-replacement [s match pos replacement]
  (str/replace replacement #"\$([$&`'])"
               (fn [[_ c]]
                 (case c
                   "$" "$"
                   "&" match
                   "`" (subs s 0 pos)
                   "'" (subs s (+ pos (count match)))))))

(defn replace-first [s match replacement]
  (let [pos (str/index-of s match)]
    (if (nil? pos)
      s
      (str (subs s 0 pos) (js-replacement s match pos replacement) (subs s (+ pos (count match)))))))
