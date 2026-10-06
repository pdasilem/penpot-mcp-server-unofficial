(ns penpot.mcp.html.cascade-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.sample :as sample]))

(defn- sides [style prop]
  (mapv #(get style (str prop "-" %)) ["top" "right" "bottom" "left"]))

(deftest more-specific-rules-win
  (let [st (sample/style ".nav div.on")]
    (is (= "#1a1d20" (st "color")))
    (is (= ["6px" "10px" "6px" "10px"] (sides st "padding")))
    (is (= "8px" (st "margin-left")))))

(deftest a-later-rule-of-higher-specificity-resets-a-shorthand
  (let [st (sample/style ".blk")]
    (is (= "0" (st "border-top-width")))
    (is (= "0" (st "padding-top")))))

(deftest inline-styles-beat-class-rules
  (is (= "56px" (get (sample/style "div.nav[style*=56px]") "width")))
  (is (= ["34px" "14px" "34px" "14px"] (sides (sample/style "div.main[style*=34px]") "padding"))))

(deftest shorthands-expand-to-every-side
  (let [st (sample/style ".rule")]
    (is (= ["12px" "14px" "12px" "14px"] (sides st "padding")))
    (is (= ["1px" "1px" "1px" "1px"] (mapv #(st (str "border-" % "-width")) ["top" "right" "bottom" "left"])))
    (is (= "solid" (st "border-left-style")))
    (is (= "#c9cdd0" (st "border-left-color")))))

(deftest a-one-side-border-sets-only-that-side
  (let [st (sample/style ".top")]
    (is (= "1px" (st "border-bottom-width")))
    (is (nil? (st "border-top-width")))))

(deftest text-properties-inherit-and-box-properties-do-not
  (let [st (sample/style ".num span")]
    (is (= "ui-sans-serif,system-ui,sans-serif" (st "font-family")))
    (is (= "1.5" (st "line-height")))
    (is (nil? (st "padding-top")))))

(deftest variables-resolve-to-their-values
  (is (= "#1a1d20" (get (sample/style "h1") "color")))
  (is (= "#6b7276" (get (sample/style "h2") "color")))
  (is (= "#e3e6e8" (get (sample/style ".top") "border-bottom-color"))))

(deftest em-lengths-resolve-against-the-font-size
  (is (= "1.56px" (get (sample/style "h2") "letter-spacing")))
  (is (= "-0.28px" (get (sample/style "h1") "letter-spacing")))
  (is (= "0.52px" (get (sample/style ".num em") "letter-spacing")) "the parent's computed spacing is inherited"))

(deftest user-agent-defaults-apply-under-author-rules
  (is (= "block" (get (sample/style ".rule") "display")))
  (is (= "inline" (get (sample/style ".num span") "display")))
  (is (= "700" (get (sample/style ".rule b") "font-weight")))
  (is (= "table-cell" (get (sample/style "td.m") "display")))
  (is (= "flex" (get (sample/style ".desk") "display"))))

(deftest pseudo-elements-carry-content-and-their-own-color
  (is (= "▾" (get (sample/pseudo ".sel" "after") "content")))
  (is (= "#6b7276" (get (sample/pseudo ".sel" "after") "color")))
  (is (= " ↕" (get (sample/pseudo "th.s" "after") "content")))
  (is (nil? (sample/pseudo ".sel" "before"))))

(deftest style-elements-are-not-rendered
  (is (= "none" (get-in (sample/computed) [(sample/element "style") :style "display"]))))
