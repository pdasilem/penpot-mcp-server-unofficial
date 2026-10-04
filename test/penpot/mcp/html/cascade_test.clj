(ns penpot.mcp.html.cascade-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.html.cascade :as cascade])
  (:import
   (org.jsoup Jsoup)))

(defn- styles [html]
  (let [doc (Jsoup/parse ^String html)]
    {:doc doc :computed (cascade/compute doc {:viewport 1440})}))

(defn- style-of [{:keys [doc computed]} selector]
  (:style (get computed (.selectFirst doc ^String selector))))

(deftest author-rules-follow-specificity-and-order
  (let [s (styles "<style>.a{color:red} div.a{color:blue} .b{margin:1px} .b{margin:2px}</style><div class='a b' id='x'>t</div>")]
    (is (= "blue" (get (style-of s "#x") "color")))
    (is (= "2px" (get (style-of s "#x") "margin-top")))))

(deftest inline-style-and-important
  (let [s (styles "<style>.a{color:red !important; margin:4px}</style><div class='a' style='color:blue; margin:8px' id='x'>t</div>")]
    (is (= "red" (get (style-of s "#x") "color")))
    (is (= "8px" (get (style-of s "#x") "margin-top")))))

(deftest shorthands-expand-and-longhands-override-in-order
  (let [s (styles "<style>.a{padding:4px 8px; padding-left:2px} .a{border:1px solid #ccc; border-bottom:0}</style><div class='a' id='x'>t</div>")
        st (style-of s "#x")]
    (is (= ["4px" "8px" "4px" "2px"] (mapv st ["padding-top" "padding-right" "padding-bottom" "padding-left"])))
    (is (= "1px" (st "border-top-width")))
    (is (= "0" (st "border-bottom-width")))))

(deftest text-properties-inherit-and-box-properties-do-not
  (let [s (styles "<style>body{color:#123456; font:15px/1.5 Inter, sans-serif} .p{padding:9px; letter-spacing:.1em}</style><body><div class='p'><span id='x'>t</span></div></body>")
        st (style-of s "#x")]
    (is (= "#123456" (st "color")))
    (is (= "15px" (st "font-size")))
    (is (= "1.5" (st "line-height")))
    (is (= "Inter,sans-serif" (st "font-family")))
    (is (= "1.5px" (st "letter-spacing")))
    (is (nil? (st "padding-top")))))

(deftest variables-resolve-with-fallback-and-inheritance
  (let [s (styles "<style>:root{--ink:#1a1d20; --b:var(--ink)} .x{color:var(--ink); border-top-color:var(--b); background-color:var(--missing, #fff)} .y{--ink:#ff0000}</style><div class='y'><p class='x' id='x'>t</p></div>")
        st (style-of s "#x")]
    (is (= "#ff0000" (st "color")))
    (is (= "#1a1d20" (st "border-top-color")))
    (is (= "#fff" (st "background-color")))))

(deftest font-size-resolves-to-pixels
  (let [s (styles "<style>body{font-size:15px} .a{font-size:.8em} .b{font-size:1.5rem}</style><body><div class='a' id='a'><span class='b' id='b'>t</span><small id='s'>s</small></div></body>")]
    (is (= "12px" (get (style-of s "#a") "font-size")))
    (is (= "24px" (get (style-of s "#b") "font-size")))
    (is (= "10px" (get (style-of s "#s") "font-size")))))

(deftest user-agent-defaults
  (let [s (styles "<div id='d'><b id='b'>x</b><span id='s'>y</span><h2 id='h'>z</h2></div>")]
    (is (= "block" (get (style-of s "#d") "display")))
    (is (= "inline" (get (style-of s "#s") "display")))
    (is (= "700" (get (style-of s "#b") "font-weight")))
    (is (= "24px" (get (style-of s "#h") "font-size")))
    (testing "author rules beat defaults"
      (is (= "flex" (get (style-of (styles "<style>span{display:flex}</style><span id='s'>y</span>") "#s") "display"))))))

(deftest pseudo-elements-carry-content-and-inherit
  (let [s  (styles "<style>.sel{color:#111} .sel::after{content:\"▾\"; color:#666} th.s::after{content:\" ↕\"} .n::before{content:none}</style><div class='sel' id='x'>A</div><div class='n' id='n'>B</div>")
        c  (get (:computed s) (.selectFirst ^org.jsoup.nodes.Document (:doc s) "#x"))]
    (is (= "▾" (get-in c [:pseudo "after" "content"])))
    (is (= "#666" (get-in c [:pseudo "after" "color"])))
    (is (nil? (get-in (get (:computed s) (.selectFirst ^org.jsoup.nodes.Document (:doc s) "#n")) [:pseudo "before"])))))

(deftest unsupported-selectors-are-skipped
  (let [s (styles "<style>.a:foo-bar{color:red} .a{color:blue}</style><div class='a' id='x'><b>t</b></div>")]
    (is (= "blue" (get (style-of s "#x") "color")))))

(deftest style-and-script-are-not-styled
  (let [{:keys [doc computed]} (styles "<style>.a{}</style><script>x()</script><div id='x'>t</div>")]
    (is (= "none" (get-in computed [(.selectFirst doc "script") :style "display"])))))
