(ns penpot.mcp.transform.svg-test
  (:require
   [app.common.geom.matrix :as gmt]
   [app.common.geom.point :as gpt]
   [app.common.types.shape :as cts]
   [app.common.uuid :as uuid]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.transform.svg :as svg]))

(def objects (:objects fx/page))

(deftest renders-standalone-svg-document-with-viewbox
  (let [doc (svg/shape->svg objects fx/rect)]
    (is (str/starts-with? doc "<svg xmlns=\"http://www.w3.org/2000/svg\""))
    (is (str/includes? doc "viewBox=\"16 24 120 40\""))
    (is (str/includes? doc "width=\"120\" height=\"40\""))))

(deftest renders-rectangle-with-fill-stroke-and-radius
  (let [doc (svg/shape->svg objects fx/rect)]
    (is (str/includes? doc "<rect x=\"16\" y=\"24\" width=\"120\" height=\"40\" rx=\"8\""))
    (is (str/includes? doc "fill=\"#3366FF\" fill-opacity=\"0.5\""))
    (is (str/includes? doc "stroke=\"#000000\" stroke-width=\"2\""))
    (is (str/includes? doc "opacity=\"0.9\""))))

(deftest renders-board-with-children-and-clip
  (let [doc (svg/shape->svg objects fx/board)]
    (is (str/includes? doc "<clipPath id=\"clip-22222222-0000-0000-0000-000000000001\">"))
    (is (str/includes? doc "Submit Button"))
    (is (str/includes? doc ">Sign in</tspan></text>"))))

(deftest renders-ellipse-with-gradient
  (let [doc (svg/shape->svg objects fx/ellipse)]
    (is (str/includes? doc "<linearGradient id=\"fill-22222222-0000-0000-0000-000000000004-0\""))
    (is (str/includes? doc "<ellipse cx=\"525\" cy=\"35\" rx=\"25\" ry=\"25\""))
    (is (str/includes? doc "fill=\"url(#fill-22222222-0000-0000-0000-000000000004-0)\""))))

(deftest renders-path-from-content
  (let [doc (svg/shape->svg objects fx/path-shape)]
    (is (re-find #"<path d=\"M[^\"]+\" " doc))
    (is (str/includes? doc "stroke=\"#999999\""))
    (is (str/includes? doc "fill=\"none\""))))

(deftest escapes-text
  (is (= "a &lt;b&gt; &amp; &quot;c&quot;" (svg/escape "a <b> & \"c\""))))

(deftest strips-xml-invalid-control-characters
  (is (= "ab" (svg/escape "a\u0001b"))))

(deftest rotated-group-transform-is-not-applied-twice
  (let [rotation (gmt/rotate-matrix 90 (gpt/point 15 15))
        child-id (uuid/next)
        group-id (uuid/next)
        child    (assoc (cts/setup-shape {:id child-id :type :rect :name "Child" :x 10 :y 10 :width 10 :height 10
                                          :frame-id uuid/zero :parent-id group-id})
                        :transform rotation)
        group    (assoc (cts/setup-shape {:id group-id :type :group :name "Group" :x 10 :y 10 :width 10 :height 10
                                          :frame-id uuid/zero :parent-id uuid/zero :shapes [child-id]})
                        :transform rotation)
        doc      (svg/shape->svg {group-id group child-id child} group)]
    (is (str/includes? doc "<g data-name=\"Group\">"))
    (is (= 1 (count (re-seq #"transform=" doc))))))

(deftest hidden-shapes-are-not-rendered
  (is (not (str/includes? (svg/shape->svg (assoc objects fx/rect-id (assoc fx/rect :hidden true)) fx/board) "Submit"))))

(deftest invalid-colors-render-as-none
  (let [doc (svg/shape->svg objects (assoc fx/rect :fills [{:fill-color "url(http://evil)" :fill-opacity 1}]))]
    (is (not (str/includes? doc "evil")))
    (is (str/includes? doc "fill=\"none\""))))
