(ns penpot.mcp.html.text-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.html.cascade :as cascade]
   [penpot.mcp.html.text :as text])
  (:import
   (org.jsoup Jsoup)))

(defn- content [html selector]
  (let [doc (Jsoup/parse ^String html)
        computed (cascade/compute doc {:viewport 1440})]
    (text/content (.selectFirst doc ^String selector) computed)))

(deftest inline-elements-become-styled-runs
  (let [c (content "<style>body{font:15px/1.5 Inter; color:#111} b{color:#222}</style><p id='p'>Hello <b>big</b>   world</p>" "#p")]
    (is (= ["Hello " "big" " world"] (mapv :text (:runs c))))
    (is (= "700" (get-in c [:runs 1 :style :fontWeight])))
    (is (= [{:fillColor "#222222" :fillOpacity 1.0}] (get-in c [:runs 1 :style :fills])))
    (is (= {:fontFamily "Inter" :fontSize "15" :fontWeight "400" :fontStyle "normal" :lineHeight "1.5"
            :letterSpacing "0" :textTransform "none" :textDecoration "none"
            :fills [{:fillColor "#111111" :fillOpacity 1.0}]}
           (get-in c [:runs 0 :style])))))

(deftest equal-neighbours-merge
  (let [c (content "<p id='p'>a<span>b</span>c</p>" "#p")]
    (is (= ["abc"] (mapv :text (:runs c))))))

(deftest pseudo-content-is-added
  (let [c (content "<style>.s::after{content:\"▾\"; color:#666} .s::before{content:\"→ \"}</style><div class='s' id='s'>Pick</div>" "#s")]
    (is (= "→ Pick▾" (apply str (map :text (:runs c)))))
    (is (= [{:fillColor "#666666" :fillOpacity 1.0}] (:fills (:style (last (:runs c))))))))

(deftest whitespace-and-breaks
  (is (= "a b\nc" (apply str (map :text (:runs (content "<p id='p'>\n  a   b<br>c  </p>" "#p"))))))
  (is (= "  a\n  b" (apply str (map :text (:runs (content "<pre id='p'>  a\n  b</pre>" "#p")))))))

(deftest line-height-and-letter-spacing
  (let [c (content "<style>p{font-size:20px; line-height:30px; letter-spacing:.1em; text-transform:uppercase; text-decoration:underline}</style><p id='p'>x</p>" "#p")
        st (get-in c [:runs 0 :style])]
    (is (= "1.5" (:lineHeight st)))
    (is (= "2" (:letterSpacing st)))
    (is (= "uppercase" (:textTransform st)))
    (is (= "underline" (:textDecoration st)))))

(deftest paragraph-properties
  (let [c (content "<style>p{text-align:center; white-space:nowrap}</style><p id='p'>x</p>" "#p")]
    (is (= "center" (:align c)))
    (is (true? (:nowrap c)))))

(deftest empty-text-is-nil
  (is (nil? (content "<div id='d'>  \n </div>" "#d"))))

(deftest block-children-are-not-part-of-text
  (testing "text/content only reads inline content"
    (is (= "a" (apply str (map :text (:runs (content "<div id='d'>a<div>inner</div></div>" "#d"))))))))
