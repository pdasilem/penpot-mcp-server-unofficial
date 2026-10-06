(ns penpot.mcp.transform.layout
  (:require
   [clojure.string :as str]
   [penpot.mcp.transform.geometry :as geometry]))

(defn- px [n]
  (let [r (/ (Math/round (* 100.0 (double (or n 0)))) 100.0)]
    (str (if (== r (Math/floor r)) (long r) r) "px")))

(def ^:private flex-alignment
  {:start "flex-start" :end "flex-end" :center "center" :stretch "stretch"
   :space-between "space-between" :space-around "space-around" :space-evenly "space-evenly"
   :baseline "baseline"})

(defn- track [{:keys [type value]}]
  (case type
    :flex (str (or value 1) "fr")
    :fixed (px value)
    :percent (str value "%")
    "auto"))

(defn- padding [{:keys [layout-padding-type layout-padding]}]
  (let [{:keys [p1 p2 p3 p4]} layout-padding]
    (when layout-padding
      (if (= :simple layout-padding-type)
        (str (px p1) " " (px p2))
        (str/join " " (map px [p1 p2 p3 p4]))))))

(defn- gap [{:keys [layout-gap]}]
  (when layout-gap
    (str (px (:row-gap layout-gap)) " " (px (:column-gap layout-gap)))))

(defn- flex-props [shape]
  [["display" "flex"]
   ["flex-direction" (some-> (:layout-flex-dir shape) name)]
   ["flex-wrap" (when (= :wrap (:layout-wrap-type shape)) "wrap")]
   ["justify-content" (flex-alignment (:layout-justify-content shape))]
   ["align-items" (flex-alignment (:layout-align-items shape))]
   ["align-content" (when (= :wrap (:layout-wrap-type shape)) (flex-alignment (:layout-align-content shape)))]])

(defn- grid-props [shape]
  [["display" "grid"]
   ["grid-template-columns" (some->> (seq (:layout-grid-columns shape)) (map track) (str/join " "))]
   ["grid-template-rows" (some->> (seq (:layout-grid-rows shape)) (map track) (str/join " "))]
   ["justify-items" (some-> (:layout-justify-items shape) name)]
   ["align-items" (some-> (:layout-align-items shape) name)]])

(defn container-props [shape]
  (let [base (case (:layout shape)
               :flex (flex-props shape)
               :grid (grid-props shape)
               nil)]
    (when base
      (conj (vec base) ["gap" (gap shape)] ["padding" (padding shape)]))))

(defn- sizing [mode size]
  (case mode
    :fill "100%"
    :auto "fit-content"
    (px size)))

(defn child-size-props [shape]
  [["width" (sizing (:layout-item-h-sizing shape) (geometry/width shape))]
   ["height" (sizing (:layout-item-v-sizing shape) (geometry/height shape))]])
