(ns penpot.mcp.design.sd.tree
  (:require
   [clojure.string :as str]))

(def ^:private aligned-types
  {"fontFamilies" "fontFamily" "fontWeights" "fontWeight" "fontSizes" "fontSize" "lineHeights" "lineHeight"
   "boxShadow" "shadow" "spacing" "dimension" "sizing" "dimension" "borderRadius" "dimension"
   "borderWidth" "dimension" "letterSpacing" "dimension" "paragraphSpacing" "dimension"
   "paragraphIndent" "dimension" "text" "content"})

(def ^:private max-array-index 4294967295)

(defn- array-index? [k]
  (and (re-matches #"0|[1-9]\d{0,9}" k) (< (parse-long k) max-array-index)))

(defn- js-key-order [m]
  (let [ks (keys m)]
    (concat (sort-by parse-long (filter array-index? ks)) (remove array-index? ks))))

(defn- token? [v]
  (and (map? v) (contains? v "value")))

(defn- tree-tokens [tree path]
  (mapcat (fn [k]
            (let [v (get tree k)]
              (cond
                (token? v) [[(conj path k) v]]
                (map? v) (tree-tokens v (conj path k))
                :else [])))
          (js-key-order tree)))

(defn- preprocess [{:strs [type] :as token}]
  (let [aligned (get aligned-types type type)]
    (cond-> (assoc token "type" aligned)
      (not= aligned type) (assoc "original-type" type))))

(defn token-map [tree]
  (let [entries (map (fn [[path token]]
                       (let [t (preprocess token)]
                         [(str "{" (str/join "." path) "}") (assoc t "original" t)]))
                     (tree-tokens tree []))]
    {:order (mapv first entries) :tokens (into {} entries)}))
