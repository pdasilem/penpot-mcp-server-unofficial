(ns penpot.mcp.html.css.values-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.html.css.values :as v]))

(def ctx {:font-size 16 :root-font-size 16 :viewport 1440})

(deftest tokens-respect-parentheses
  (is (= ["1px" "solid" "rgba(0, 0, 0, .5)"] (v/tokens "1px solid rgba(0, 0, 0, .5)")))
  (is (= ["inset 0 -2px 0 #000" "0 1px 2px red"] (v/comma-split "inset 0 -2px 0 #000, 0 1px 2px red"))))

(deftest lengths-resolve-to-pixels
  (is (= 12.0 (v/px "12px" ctx)))
  (is (= 24.0 (v/px "1.5em" ctx)))
  (is (= 32.0 (v/px "2rem" ctx)))
  (is (= 4.0 (v/px "0.5ch" ctx)))
  (is (= 144.0 (v/px "10vw" ctx)))
  (is (= 0.0 (v/px "0" ctx)))
  (is (= -6.0 (v/px "-6px" ctx)))
  (is (nil? (v/px "auto" ctx)))
  (is (= {:percent 50.0} (v/length "50%" ctx))))

(deftest colors-become-hex-and-opacity
  (is (= {:hex "#1a1d20" :opacity 1.0} (v/color "#1a1d20")))
  (is (= {:hex "#ffffff" :opacity 1.0} (v/color "#fff")))
  (is (= {:hex "#000000" :opacity 0.5} (v/color "rgba(0, 0, 0, .5)")))
  (is (= {:hex "#ff8000" :opacity 0.25} (v/color "rgb(255 128 0 / 25%)")))
  (is (= {:hex "#112233" :opacity 0.6666666666666666} (v/color "#112233aa")))
  (is (= {:hex "#ff0000" :opacity 1.0} (v/color "red")))
  (is (nil? (v/color "transparent")))
  (is (nil? (v/color "currentColor"))))

(deftest box-shorthands-expand-to-sides
  (is (= {"margin-top" "0" "margin-right" "0" "margin-bottom" "6px" "margin-left" "0"}
         (into {} (v/expand "margin" "0 0 6px"))))
  (is (= {"padding-top" "7px" "padding-right" "10px" "padding-bottom" "7px" "padding-left" "10px"}
         (into {} (v/expand "padding" "7px 10px")))))

(deftest border-shorthands-expand
  (is (= {"border-top-width" "1px" "border-top-style" "solid" "border-top-color" "var(--line)"
          "border-right-width" "1px" "border-right-style" "solid" "border-right-color" "var(--line)"
          "border-bottom-width" "1px" "border-bottom-style" "solid" "border-bottom-color" "var(--line)"
          "border-left-width" "1px" "border-left-style" "solid" "border-left-color" "var(--line)"}
         (into {} (v/expand "border" "1px solid var(--line)"))))
  (is (= {"border-bottom-width" "1px" "border-bottom-style" "solid" "border-bottom-color" "#eee"}
         (into {} (v/expand "border-bottom" "1px solid #eee"))))
  (is (= {"border-top-width" "0"} (into {} (v/expand "border-top" "0"))))
  (testing "border-style alone"
    (is (= "dashed" (get (into {} (v/expand "border-style" "dashed")) "border-left-style")))))

(deftest radius-font-flex-gap-background
  (is (= {"border-top-left-radius" "8px" "border-top-right-radius" "8px" "border-bottom-right-radius" "8px" "border-bottom-left-radius" "8px"}
         (into {} (v/expand "border-radius" "8px"))))
  (is (= {"font-style" "normal" "font-weight" "400" "font-size" "15px" "line-height" "1.5" "font-family" "ui-sans-serif, system-ui, sans-serif"}
         (into {} (v/expand "font" "15px/1.5 ui-sans-serif, system-ui, sans-serif"))))
  (is (= {"flex-grow" "1" "flex-shrink" "1" "flex-basis" "0%"} (into {} (v/expand "flex" "1"))))
  (is (= {"flex-grow" "0" "flex-shrink" "0" "flex-basis" "auto"} (into {} (v/expand "flex" "none"))))
  (is (= {"row-gap" "4px" "column-gap" "8px"} (into {} (v/expand "gap" "4px 8px"))))
  (is (= {"background-color" "var(--fill)"} (into {} (v/expand "background" "var(--fill)"))))
  (is (= {"background-image" "linear-gradient(90deg, #fff, #000)"} (into {} (v/expand "background" "linear-gradient(90deg, #fff, #000)"))))
  (is (= [["color" "red"]] (v/expand "color" "red"))))

(deftest shadows-parse
  (is (= [{:inset true :x 0.0 :y -2.0 :blur 0.0 :spread 0.0 :color {:hex "#5a6167" :opacity 1.0}}]
         (v/shadows "inset 0 -2px 0 #5a6167" ctx)))
  (is (= [{:inset false :x 0.0 :y 1.0 :blur 2.0 :spread 0.0 :color {:hex "#000000" :opacity 0.1}}
          {:inset false :x 0.0 :y 4.0 :blur 8.0 :spread 1.0 :color {:hex "#000000" :opacity 1.0}}]
         (v/shadows "0 1px 2px rgba(0,0,0,.1), 0 4px 8px 1px #000" ctx)))
  (is (= [] (v/shadows "none" ctx))))

(deftest linear-gradients-parse
  (is (= {:angle 90.0 :stops [{:color {:hex "#ffffff" :opacity 1.0} :offset 0.0}
                              {:color {:hex "#000000" :opacity 1.0} :offset 1.0}]}
         (v/linear-gradient "linear-gradient(90deg, #fff, #000)")))
  (is (= {:angle 180.0 :stops [{:color {:hex "#ff0000" :opacity 1.0} :offset 0.0}
                               {:color {:hex "#00ff00" :opacity 1.0} :offset 0.3}
                               {:color {:hex "#0000ff" :opacity 1.0} :offset 1.0}]}
         (v/linear-gradient "linear-gradient(to bottom, red, #0f0 30%, blue)")))
  (is (nil? (v/linear-gradient "radial-gradient(red, blue)"))))
