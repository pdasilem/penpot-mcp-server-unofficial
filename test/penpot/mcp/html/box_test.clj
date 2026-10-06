(ns penpot.mcp.html.box-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.box :as box]
   [penpot.mcp.html.sample :as sample]))

(def ^:private ctx {:font-size 15 :root-font-size 15 :viewport sample/viewport})

(defn- decoration [selector] (box/decoration (sample/style selector) ctx))

(defn- spacing [selector] (box/spacing (sample/style selector) ctx))

(deftest a-background-color-becomes-a-fill
  (is (= [{:fillColor "#ffffff" :fillOpacity 1.0}] (:fills (decoration ".rule"))))
  (is (= [{:fillColor "#e9ecee" :fillOpacity 1.0}] (:fills (decoration ".nav div.on"))))
  (is (= [] (:fills (decoration ".top")))))

(deftest equal-borders-become-an-inner-stroke
  (let [d (decoration ".rule")]
    (is (= [{:strokeColor "#c9cdd0" :strokeOpacity 1.0 :strokeWidth 1.0 :strokeStyle "solid" :strokeAlignment "inner"}] (:strokes d)))
    (is (= [] (:lines d)))))

(deftest a-one-side-border-becomes-a-line
  (let [d (decoration ".top")]
    (is (= [] (:strokes d)))
    (is (= [{:side "bottom" :width 1.0 :color "#e3e6e8" :opacity 1.0}] (:lines d)))))

(deftest dashed-borders-keep-their-style
  (is (= "dashed" (get-in (decoration "div[style*=dashed]") [:strokes 0 :strokeStyle]))))

(deftest radius-comes-from-the-border-radius
  (is (= [8.0 8.0 8.0 8.0] (:radius (decoration ".rule"))))
  (is (= [10.0 10.0 10.0 10.0] (:radius (decoration ".desk")))))

(deftest an-inset-box-shadow-becomes-an-inner-shadow
  (is (= [{:style "inner-shadow" :offsetX 0.0 :offsetY -2.0 :blur 0.0 :spread 0.0 :hidden false
           :color {:color "#5a6167" :opacity 1.0}}]
         (:shadows (decoration ".tabs span.on")))))

(deftest an-outer-box-shadow-becomes-a-drop-shadow
  (is (= [{:style "drop-shadow" :offsetX 0.0 :offsetY 8.0 :blur 28.0 :spread 0.0 :hidden false
           :color {:color "#000000" :opacity 0.12}}]
         (:shadows (decoration "div.form[style*=box-shadow]")))))

(deftest opacity-and-overflow-become-opacity-and-clipping
  (is (= 0.45 (:opacity (decoration "div.main[style*=opacity]"))))
  (is (true? (:clip (decoration ".desk"))))
  (is (false? (:clip (decoration ".rule")))))

(deftest spacing-resolves-in-pixels
  (let [s (spacing ".rule")]
    (is (= [12.0 14.0 12.0 14.0] (:padding s)))
    (is (= [0.0 0.0 28.0 0.0] (:margin s))))
  (is (= [10.0 10.0] (:gap (spacing ".top"))))
  (is (= [0.0 8.0 0.0 8.0] (:margin (spacing ".nav div.on")))))

(deftest auto-margins-are-reported-for-alignment
  (is (= #{:left} (:auto-margins (spacing ".top .who"))))
  (is (= #{} (:auto-margins (spacing ".rule")))))
