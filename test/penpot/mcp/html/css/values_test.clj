(ns penpot.mcp.html.css.values-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.css.values :as v]
   [penpot.mcp.html.sample :as sample]))

(def ^:private ctx {:font-size 13 :root-font-size 15 :viewport sample/viewport})

(defn- real [value]
  (is (sample/declared? value) (str value " is not in the sample"))
  value)

(deftest tokens-respect-parentheses
  (is (= ["1px" "solid" "var(--line)"] (v/tokens (real "1px solid var(--line)"))))
  (is (= ["0" "8px" "28px" "rgba(0,0,0,.12)"] (v/tokens (real "0 8px 28px rgba(0,0,0,.12)"))))
  (is (= ["ui-sans-serif" "system-ui" "sans-serif"] (v/comma-split (real "ui-sans-serif, system-ui, sans-serif")))))

(deftest lengths-resolve-to-pixels
  (is (= 12.0 (v/px (real "12px") ctx)))
  (is (= 1.56 (v/px (real ".12em") ctx)))
  (is (= -0.13 (v/px (real "-.01em") ctx)))
  (is (= 481.0 (v/px (real "74ch") ctx)))
  (is (= 0.0 (v/px "0" ctx)))
  (is (nil? (v/px (real "auto") ctx)))
  (is (= {:percent 100.0} (v/length (real "100%") ctx))))

(deftest colors-become-hex-and-opacity
  (is (= {:hex "#1a1d20" :opacity 1.0} (v/color (real "#1a1d20"))))
  (is (= {:hex "#ffffff" :opacity 1.0} (v/color (real "#fff"))))
  (is (= {:hex "#000000" :opacity 0.12} (v/color (real "rgba(0,0,0,.12)"))))
  (is (nil? (v/color (real "transparent")))))

(deftest box-shorthands-expand-to-sides
  (is (= {"margin-top" "0" "margin-right" "0" "margin-bottom" "20px" "margin-left" "0"}
         (into {} (v/expand "margin" (real "0 0 20px")))))
  (is (= {"padding-top" "7px" "padding-right" "10px" "padding-bottom" "7px" "padding-left" "10px"}
         (into {} (v/expand "padding" (real "7px 10px")))))
  (is (= {"padding-top" "40px" "padding-right" "24px" "padding-bottom" "80px" "padding-left" "24px"}
         (into {} (v/expand "padding" (real "40px 24px 80px"))))))

(deftest border-shorthands-expand
  (is (= {"border-top-width" "1px" "border-top-style" "solid" "border-top-color" "var(--line)"
          "border-right-width" "1px" "border-right-style" "solid" "border-right-color" "var(--line)"
          "border-bottom-width" "1px" "border-bottom-style" "solid" "border-bottom-color" "var(--line)"
          "border-left-width" "1px" "border-left-style" "solid" "border-left-color" "var(--line)"}
         (into {} (v/expand "border" (real "1px solid var(--line)")))))
  (is (= {"border-bottom-width" "1px" "border-bottom-style" "solid" "border-bottom-color" "var(--thin)"}
         (into {} (v/expand "border-bottom" (real "1px solid var(--thin)")))))
  (is (= {"border-top-width" "0"} (into {} (v/expand "border-top" (subs (real "border-top:0") 11)))))
  (is (= "dashed" (get (into {} (v/expand "border-style" (real "dashed"))) "border-left-style"))))

(deftest radius-font-flex-gap-background
  (is (= {"border-top-left-radius" "8px" "border-top-right-radius" "8px" "border-bottom-right-radius" "8px" "border-bottom-left-radius" "8px"}
         (into {} (v/expand "border-radius" (real "8px")))))
  (is (= {"font-style" "normal" "font-weight" "400" "font-size" "15px" "line-height" "1.5" "font-family" "ui-sans-serif, system-ui, sans-serif"}
         (into {} (v/expand "font" (real "15px/1.5 ui-sans-serif, system-ui, sans-serif")))))
  (is (= "12px" (get (into {} (v/expand "font" (real "12px ui-monospace, monospace"))) "font-size")))
  (is (= {"flex-grow" "1" "flex-shrink" "1" "flex-basis" "0%"} (into {} (v/expand "flex" (subs (real "flex:1") 5)))))
  (is (= {"flex-grow" "0" "flex-shrink" "0" "flex-basis" "auto"} (into {} (v/expand "flex" (real "none")))))
  (is (= {"row-gap" "16px" "column-gap" "16px"} (into {} (v/expand "gap" (real "16px")))))
  (is (= {"background-color" "var(--fill)"} (into {} (v/expand "background" (real "var(--fill)"))))))

(deftest shadows-parse
  (is (= [{:inset false :x 0.0 :y 8.0 :blur 28.0 :spread 0.0 :color {:hex "#000000" :opacity 0.12}}]
         (v/shadows (real "0 8px 28px rgba(0,0,0,.12)") ctx)))
  (is (= [{:inset true :x 0.0 :y -2.0 :blur 0.0 :spread 0.0 :color {:hex "#5a6167" :opacity 1.0}}]
         (v/shadows (get (sample/style ".tabs span.on") "box-shadow") ctx))))
