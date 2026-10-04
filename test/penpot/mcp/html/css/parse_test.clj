(ns penpot.mcp.html.css.parse-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.css.parse :as parse]))

(deftest specificity-counts-ids-classes-and-elements
  (is (= [0 0 1] (parse/specificity "div")))
  (is (= [0 1 1] (parse/specificity "div.on")))
  (is (= [0 3 2] (parse/specificity ".tabs span.on > b:first-child")))
  (is (= [1 1 0] (parse/specificity "#top .x")))
  (is (= [0 1 1] (parse/specificity "th.s"))))

(deftest rules-keep-document-order-and-split-selector-lists
  (let [rules (parse/stylesheet "h1, .x { color: red } .y { margin: 0 !important }" 1440)]
    (is (= [["h1" nil 0] [".x" nil 0] [".y" nil 1]] (mapv (juxt :selector :pseudo :order) rules)))
    (is (= [["color" "red" false]] (:decls (first rules))))
    (is (= [["margin" "0" true]] (:decls (last rules))))))

(deftest pseudo-elements-are-separated-and-dynamic-states-dropped
  (let [rules (parse/stylesheet ".sel::after { content: \"▾\" } th.s:after { content: \" ↕\" } a:hover { color: red } .b:first-child { x: 1 }" 1440)]
    (is (= [[".sel" "after"] ["th.s" "after"] [".b:first-child" nil]] (mapv (juxt :selector :pseudo) rules)))))

(deftest media-rules-apply-by-viewport-width
  (let [css   "@media (min-width: 1000px) { .a { x: 1 } } @media (max-width: 600px) { .b { x: 1 } } @media print { .c { x: 1 } } @media screen and (min-width: 500px) { .d { x: 1 } }"
        names #(set (map :selector (parse/stylesheet css %)))]
    (is (= #{".a" ".d"} (names 1440)))
    (is (= #{".b" ".d"} (names 550)))))

(deftest inline-declarations
  (is (= [["display" "flex" false] ["gap" "6px" false]] (parse/declarations "display:flex; gap:6px;"))))

(deftest broken-css-does-not-fail
  (is (vector? (parse/stylesheet ".a { color: } .b { margin: 0 }" 1440)))
  (is (vector? (parse/declarations "color: ;;"))))
