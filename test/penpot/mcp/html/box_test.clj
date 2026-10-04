(ns penpot.mcp.html.box-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.html.box :as box]))

(def ctx {:font-size 16 :root-font-size 16 :viewport 1440})

(defn- border [side w style color]
  {(str "border-" side "-width") w (str "border-" side "-style") style (str "border-" side "-color") color})

(def all-borders
  (merge (border "top" "1px" "solid" "#c9cdd0") (border "right" "1px" "solid" "#c9cdd0")
         (border "bottom" "1px" "solid" "#c9cdd0") (border "left" "1px" "solid" "#c9cdd0")))

(deftest background-color-becomes-a-fill
  (is (= [{:fillColor "#e9ecee" :fillOpacity 1.0}] (:fills (box/decoration {"background-color" "#e9ecee"} ctx))))
  (is (= [] (:fills (box/decoration {"background-color" "transparent"} ctx)))))

(deftest linear-gradient-becomes-a-gradient-fill
  (let [[f] (:fills (box/decoration {"background-image" "linear-gradient(90deg, #fff, #000)"} ctx))]
    (is (= "linear" (get-in f [:fillColorGradient :type])))
    (is (= [0 50 100 50] (mapv #(Math/round (* 100.0 (double (get-in f [:fillColorGradient %])))) [:startX :startY :endX :endY])))
    (is (= [{:color "#ffffff" :opacity 1.0 :offset 0.0} {:color "#000000" :opacity 1.0 :offset 1.0}]
           (get-in f [:fillColorGradient :stops])))))

(deftest equal-borders-become-an-inner-stroke
  (let [d (box/decoration all-borders ctx)]
    (is (= [{:strokeColor "#c9cdd0" :strokeOpacity 1.0 :strokeWidth 1.0 :strokeStyle "solid" :strokeAlignment "inner"}] (:strokes d)))
    (is (= [] (:lines d)))))

(deftest partial-borders-become-lines
  (let [d (box/decoration (merge (border "bottom" "1px" "solid" "#e3e6e8") (border "top" "0" "solid" "#000")) ctx)]
    (is (= [] (:strokes d)))
    (is (= [{:side "bottom" :width 1.0 :color "#e3e6e8" :opacity 1.0}] (:lines d)))))

(deftest dashed-borders-keep-their-style
  (is (= "dashed" (get-in (box/decoration (assoc all-borders "border-top-style" "dashed" "border-right-style" "dashed"
                                                 "border-bottom-style" "dashed" "border-left-style" "dashed") ctx)
                          [:strokes 0 :strokeStyle]))))

(deftest radius-shadows-opacity-and-clip
  (let [d (box/decoration {"border-top-left-radius" "10px" "border-top-right-radius" "10px"
                           "border-bottom-right-radius" "6px" "border-bottom-left-radius" "0"
                           "box-shadow" "inset 0 -2px 0 #5a6167" "opacity" ".55" "overflow-x" "hidden"} ctx)]
    (is (= [10.0 10.0 6.0 0.0] (:radius d)))
    (is (= [{:style "inner-shadow" :offsetX 0.0 :offsetY -2.0 :blur 0.0 :spread 0.0 :hidden false
             :color {:color "#5a6167" :opacity 1.0}}]
           (:shadows d)))
    (is (= 0.55 (:opacity d)))
    (is (true? (:clip d)))))

(deftest spacing-resolves-in-pixels
  (let [s (box/spacing {"padding-top" "7px" "padding-right" "10px" "padding-bottom" "7px" "padding-left" "10px"
                        "margin-top" "-6px" "margin-left" "auto" "row-gap" "1em" "column-gap" "8px"} ctx)]
    (is (= [7.0 10.0 7.0 10.0] (:padding s)))
    (is (= [-6.0 0.0 0.0 0.0] (:margin s)))
    (is (= [16.0 8.0] (:gap s)))
    (testing "auto margins are reported for alignment"
      (is (= #{:left} (:auto-margins s))))))
