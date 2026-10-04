(ns penpot.mcp.html.tree-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.html.cascade :as cascade]
   [penpot.mcp.html.tree :as tree])
  (:import
   (org.jsoup Jsoup)))

(defn- frame [html selector]
  (let [doc      (Jsoup/parse ^String (str "<style>body{margin:0;font:15px/1.5 Inter}</style>" html))
        computed (cascade/compute doc {:viewport 1440})]
    (tree/frame (.selectFirst doc ^String selector) computed {:viewport 1440})))

(defn- kinds [node] (mapv :kind (:children node)))

(deftest frame-is-a-fixed-board
  (let [{:keys [node]} (frame "<div id='f' style='width:400px;height:300px;background:#fff'>Hi</div>" "#f")]
    (is (= "board" (:kind node)))
    (is (= [400.0 300.0] [(:width node) (:height node)]))
    (is (= ["fix" "fix"] [(get-in node [:sizing :horizontal]) (get-in node [:sizing :vertical])]))
    (is (= [{:fillColor "#ffffff" :fillOpacity 1.0}] (:fills node)))
    (is (= ["text"] (kinds node)))))

(deftest frame-height-follows-aspect-ratio-or-content
  (is (= 250.0 (:height (:node (frame "<div id='f' style='width:400px;aspect-ratio:16/10'>x</div>" "#f")))))
  (is (= "auto" (get-in (:node (frame "<div id='f' style='width:400px'>x</div>" "#f")) [:sizing :vertical]))))

(deftest pure-inline-content-is-one-text
  (let [{:keys [node]} (frame "<div id='f' style='width:400px'><p>Hello <b>big</b> world</p></div>" "#f")
        p              (first (:children node))]
    (is (= "text" (:kind p)))
    (is (= ["Hello " "big" " world"] (mapv :text (:runs p))))
    (is (= "fill" (get-in p [:self :horizontalSizing])))
    (is (= "auto-height" (:grow p)))
    (is (= [0.0 0.0 0.0 0.0] (get-in p [:self :margin])) "margins of the only child collapse through the parent")))

(deftest sibling-margins-collapse-to-the-larger-one
  (let [{:keys [node]} (frame "<div id='f' style='width:400px;padding:10px'><h2>T</h2><p>a</p><p>b</p></div>" "#f")
        [h p1 p2]      (:children node)]
    (is (= 18.675 (get-in h [:self :margin 0])) "first child keeps its margin inside padding")
    (is (= 0.0 (get-in p1 [:self :margin 0])) "h2 bottom 18.675 already covers p top 15")
    (is (= 0.0 (get-in p2 [:self :margin 0])) "p bottom 15 equals next p top 15")
    (is (= 15.0 (get-in p2 [:self :margin 2])))))

(deftest boxy-inline-element-becomes-board-with-text
  (let [{:keys [node]} (frame "<div id='f' style='width:400px;display:flex'><span style='border:1px solid #5a6167;border-radius:6px;padding:7px 12px'>Save</span></div>" "#f")
        btn            (first (:children node))]
    (is (= "board" (:kind btn)))
    (is (= "row" (get-in btn [:layout :dir])))
    (is (= "center" (get-in btn [:layout :alignItems])))
    (is (= [7.0 12.0 7.0 12.0] (get-in btn [:layout :padding])))
    (is (= 6.0 (first (:radius btn))))
    (is (= ["text"] (kinds btn)))
    (is (= "auto-width" (:grow (first (:children btn)))))
    (is (= "auto" (get-in btn [:self :horizontalSizing])))))

(deftest flex-row-children-get-fix-and-fill
  (let [{:keys [node]} (frame "<div id='f' style='width:800px;height:400px;display:flex'><div style='width:210px'>nav</div><div style='flex:1'>main</div></div>" "#f")
        [nav main]     (:children node)]
    (is (= "row" (get-in node [:layout :dir])))
    (is (= ["fix" 210.0] [(get-in nav [:self :horizontalSizing]) (get-in nav [:self :width])]))
    (is (= "fill" (get-in main [:self :horizontalSizing])))
    (is (= "fill" (get-in main [:self :verticalSizing])) "stretch inside a fixed-height row")))

(deftest tables-become-grids
  (let [{:keys [node]} (frame "<div id='f' style='width:600px'><table style='width:100%'><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td colspan='2'>wide</td></tr></tbody></table></div>" "#f")
        table          (first (:children node))]
    (is (= "grid" (get-in table [:layout :type])))
    (is (= 2 (count (get-in table [:layout :columns]))))
    (is (= 2 (count (get-in table [:layout :rows]))))
    (is (= [{:row 1 :column 1 :rowSpan 1 :columnSpan 1} {:row 1 :column 2 :rowSpan 1 :columnSpan 1} {:row 2 :column 1 :rowSpan 1 :columnSpan 2}]
           (mapv :cell (:children table))))
    (is (= "fill" (get-in table [:self :horizontalSizing])))))

(deftest partial-borders-become-lines
  (let [{:keys [node]} (frame "<div id='f' style='width:400px'><div style='border-bottom:1px solid #e3e6e8;height:38px'>top</div></div>" "#f")]
    (is (= [{:side "bottom" :width 1.0 :color "#e3e6e8" :opacity 1.0}] (:lines (first (:children node)))))))

(deftest growing-items-fill-only-a-definite-height
  (let [{:keys [node]} (frame "<div id='f' style='width:400px'><div style='display:flex;flex-direction:column'><div style='flex:1;border:1px solid #ccc'>x</div></div></div>" "#f")]
    (is (= "auto" (get-in node [:children 0 :children 0 :self :verticalSizing]))))
  (let [{:keys [node]} (frame "<div id='f' style='width:400px;height:300px;display:flex;flex-direction:column'><div style='flex:1;border:1px solid #ccc'>x</div></div>" "#f")]
    (is (= "fill" (get-in node [:children 0 :self :verticalSizing])))))

(deftest pseudo-elements-of-boards-are-text-children
  (let [{:keys [node]} (frame "<style>.sel{display:flex;justify-content:space-between;border:1px solid #ccc}.sel::after{content:'▾'}</style><div id='f' style='width:400px'><div class='sel'>Pick</div></div>" "#f")
        sel            (first (:children node))]
    (is (= ["text" "text"] (kinds sel)))
    (is (= "▾" (get-in sel [:children 1 :runs 0 :text])))))

(deftest hidden-and-script-content-is-skipped
  (let [{:keys [node]} (frame "<div id='f' style='width:400px'><div style='display:none'>x</div><script>a()</script><p>y</p></div>" "#f")]
    (is (= ["text"] (kinds node)))))

(deftest svg-is-kept-as-markup
  (let [{:keys [node]} (frame "<div id='f' style='width:400px'><svg width='10' height='10'><rect width='10' height='10'/></svg></div>" "#f")]
    (is (= "svg" (:kind (first (:children node)))))
    (is (re-find #"(?s)^<svg.*<rect" (:markup (first (:children node)))))))

(deftest unsupported-constructs-are-counted
  (let [{:keys [unsupported]} (frame "<div id='f' style='width:400px'><div style='float:left'>a</div><div style='transform:rotate(3deg)'>b</div><div style='float:right'>c</div></div>" "#f")]
    (is (= {"float" 2 "transform" 1} unsupported))))

(deftest inline-flow-with-boxes-becomes-a-wrapping-row
  (let [{:keys [node]} (frame "<div id='f' style='width:400px'><div>Status <span style='border:1px solid #ccc;padding:1px 6px'>on</span></div></div>" "#f")
        line           (first (:children node))]
    (is (= ["row" "wrap"] [(get-in line [:layout :dir]) (get-in line [:layout :wrap])]))
    (is (= ["text" "board"] (kinds line)))))

(deftest auto-left-margin-pushes-to-the-end
  (let [{:keys [node]} (frame "<div id='f' style='width:400px;display:flex'><b>Title</b><span style='margin-left:auto'>who</span></div>" "#f")]
    (testing "a filling spacer is inserted before the pushed item"
      (is (= ["text" "board" "text"] (kinds node)))
      (is (= "fill" (get-in node [:children 1 :self :horizontalSizing])))
      (is (true? (get-in node [:children 1 :spacer]))))))
