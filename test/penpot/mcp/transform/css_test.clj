(ns penpot.mcp.transform.css-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.transform.css :as css]))

(def objects (:objects fx/page))

(defn- props [shape]
  (into {} (:properties (css/shape->css objects shape))))

(defn- text-with [node-attrs]
  (assoc-in fx/text [:content :children 0 :children 0 :children 0]
            (merge (get-in fx/text [:content :children 0 :children 0 :children 0]) node-attrs)))

(deftest class-name-is-kebab-case
  (is (= "submit-button" (css/class-name "Submit Button")))
  (is (= "a-b-c" (css/class-name "  A / B__C  ")))
  (is (= "shape" (css/class-name "!!!"))))

(deftest rectangle-in-flex-layout
  (is (= {"width" "120px"
          "height" "40px"
          "opacity" "0.9"
          "border-radius" "8px"
          "background" "rgba(51, 102, 255, 0.5)"
          "border" "2px solid #000000"
          "box-shadow" "0px 4px 8px 0px rgba(0, 0, 0, 0.25)"}
         (props fx/rect))))

(deftest board-with-flex-layout
  (let [p (props fx/board)]
    (is (= "absolute" (get p "position")))
    (is (= "0px" (get p "left")))
    (is (= "flex" (get p "display")))
    (is (= "column" (get p "flex-direction")))
    (is (= "12px 0px" (get p "gap")))
    (is (= "24px 16px 24px 16px" (get p "padding")))
    (is (= "center" (get p "align-items")))
    (is (= "flex-start" (get p "justify-content")))
    (is (= "#FFFFFF" (get p "background")))))

(deftest text-styles-come-from-content
  (let [p (props fx/text)]
    (is (= "\"Inter\"" (get p "font-family")))
    (is (= "24px" (get p "font-size")))
    (is (= "700" (get p "font-weight")))
    (is (= "1.2" (get p "line-height")))
    (is (= "uppercase" (get p "text-transform")))
    (is (= "center" (get p "text-align")))
    (is (= "#111111" (get p "color")))
    (is (not (contains? p "background")))))

(deftest linear-gradient-fill
  (is (= "linear-gradient(135deg, rgba(255, 0, 0, 1) 0%, rgba(0, 0, 255, 1) 100%)"
         (get (props fx/ellipse) "background"))))

(deftest ellipse-is-rounded
  (is (= "50%" (get (props fx/ellipse) "border-radius"))))

(deftest css-text-renders-rule
  (let [text (:css (css/shape->css objects fx/rect))]
    (is (str/starts-with? text ".submit-button {\n"))
    (is (str/includes? text "  width: 120px;\n"))
    (is (str/ends-with? text "}"))))

(deftest quotes-font-family-to-prevent-css-injection
  (let [p   (props (text-with {:font-family "A; } .evil { color: red"}))
        out (:css (css/shape->css objects (text-with {:font-family "A; } .evil { color: red"})))]
    (is (= "\"A; } .evil { color: red\"" (get p "font-family")))
    (is (= 1 (count (re-seq #"\{" (str/replace out #"\"[^\"]*\"" "")))))))

(deftest drops-unsafe-text-values
  (let [p (props (text-with {:font-weight "700; } a {" :line-height "1}"}))]
    (is (not (contains? p "font-weight")))
    (is (not (contains? p "line-height")))))

(deftest gradient-text-fill-has-no-color
  (let [p (props (text-with {:fills [{:fill-color-gradient {:type :linear :start-x 0 :start-y 0 :end-x 1 :end-y 0 :width 1
                                                            :stops [{:color "#000000" :opacity 1 :offset 0}]}}]}))]
    (is (not (contains? p "color")))))

(deftest drops-invalid-colors
  (let [p (props (assoc fx/rect :fills [{:fill-color "red;}" :fill-opacity 1}]
                        :strokes [{:stroke-color "url(x)" :stroke-width 1}]))]
    (is (not (contains? p "background")))
    (is (not (contains? p "border")))))
