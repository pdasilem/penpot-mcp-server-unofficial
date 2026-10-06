(ns penpot.mcp.html.tree-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.cascade :as cascade]
   [penpot.mcp.html.sample :as sample]
   [penpot.mcp.html.tree :as tree])
  (:import
   (org.jsoup Jsoup)))

(defn- desks [] (.select (sample/doc) ".desk"))

(defn- frame [i]
  (tree/frame (nth (desks) i) (sample/computed) {:viewport sample/viewport}))

(defn- nodes [i] (tree-seq :children :children (:node (frame i))))

(defn- named [i node-name]
  (or (first (filter #(= node-name (:name %)) (nodes i)))
      (throw (ex-info (str "No " node-name " in desk " i) {}))))

(defn- kinds [node] (mapv :kind (:children node)))

(def ^:private content-width (double (- sample/viewport 24 24)))

(deftest a-desk-is-a-fixed-board-sized-by-its-aspect-ratio
  (let [{:keys [node]} (frame 0)]
    (is (= "board" (:kind node)))
    (is (= [content-width (/ (* content-width 10) 16)] [(:width node) (:height node)]))
    (is (= {:horizontal "fix" :vertical "fix"} (:sizing node)))
    (is (= [{:fillColor "#ffffff" :fillOpacity 1.0}] (:fills node)))
    (is (= {:type "flex" :dir "column" :wrap "nowrap"} (select-keys (:layout node) [:type :dir :wrap])))))

(deftest a-fixed-width-and-a-growing-flex-child
  (let [nav  (named 0 "div.nav")
        main (named 0 "div.main")]
    (is (= ["fix" 210.0] [(get-in nav [:self :horizontalSizing]) (get-in nav [:self :width])]))
    (is (= ["fill" "fill"] [(get-in main [:self :horizontalSizing]) (get-in main [:self :verticalSizing])]))))

(deftest a-growing-item-fills-a-definite-height
  (is (= "fill" (get-in (named 0 "div.split") [:self :verticalSizing])))
  (is (= "fix" (get-in (named 0 "div.top") [:self :verticalSizing]))))

(deftest a-one-side-border-becomes-a-line
  (is (= [{:side "bottom" :width 1.0 :color "#e3e6e8" :opacity 1.0}] (:lines (named 0 "div.top")))))

(deftest an-auto-left-margin-inserts-a-filling-spacer
  (let [top    (named 0 "div.top")
        spacer (first (filter :spacer (:children top)))]
    (is (= ["text" "text" "board" "text" "board"] (kinds top)))
    (is (= "fill" (get-in spacer [:self :horizontalSizing])))
    (is (= "admin@sayvibe.app" (:name (second (drop-while (complement :spacer) (:children top))))))))

(deftest a-boxy-inline-element-becomes-a-board-with-its-text
  (let [tag (named 0 "span.tag")]
    (is (= "row" (get-in tag [:layout :dir])))
    (is (= [1.0 6.0 1.0 6.0] (get-in tag [:layout :padding])))
    (is (= 4.0 (first (:radius tag))))
    (is (= ["text"] (kinds tag)))
    (is (= "auto" (get-in tag [:self :horizontalSizing])))))

(deftest a-pseudo-element-of-a-board-is-a-text-child
  (let [sel (named 0 "div.sel")]
    (is (= ["text" "text"] (kinds sel)))
    (is (= "▾" (get-in sel [:children 1 :runs 0 :text])))))

(deftest a-table-becomes-a-grid-with-placed-cells
  (let [table   (named 0 "table")
        element (.selectFirst (nth (desks) 0) "table")
        columns (count (.select (.selectFirst element "tr") "th,td"))
        rows    (count (.select element "tr"))]
    (is (= "grid" (get-in table [:layout :type])))
    (is (= columns (count (get-in table [:layout :columns]))))
    (is (= rows (count (get-in table [:layout :rows]))))
    (is (= (for [r (range 1 (inc rows)) c (range 1 (inc columns))] {:row r :column c :rowSpan 1 :columnSpan 1})
           (mapv :cell (:children table))))
    (is (= "fill" (get-in table [:self :horizontalSizing])))))

(deftest a-cell-holding-only-boxes-becomes-a-wrapping-row
  (let [cell (first (filter #(= ["board"] (kinds %)) (filter :cell (nodes 0))))]
    (is (= ["row" "wrap"] [(get-in cell [:layout :dir]) (get-in cell [:layout :wrap])]))))

(deftest a-row-item-holding-wrapping-text-fills-the-row
  (let [row (first (filter #(= "div.row" (:name %)) (mapcat #(nodes %) (range (count (desks))))))
        [text-box value] (:children row)]
    (is (= "fill" (get-in text-box [:self :horizontalSizing])))
    (is (= "auto" (get-in value [:self :horizontalSizing])))))

(deftest names-never-end-up-blank
  (doseq [i (range (count (desks)))
          n (map :name (nodes i))]
    (is (and (string? n) (re-find #"[^\s ]" n) (not (re-find #"^[\s ]|[\s ]$" n))) (str i " " (pr-str n)))))

(deftest an-overlay-is-placed-absolutely
  (is (some #(get-in % [:self :absolute]) (nodes 4))))

(deftest the-sample-holds-nothing-unsupported
  (doseq [i (range (count (desks)))]
    (is (= {} (:unsupported (frame i))) i)))

(deftest an-inline-pseudo-element-continues-the-text-of-its-block
  (let [th (first (filter #(= "th.s" (:name %)) (nodes 0)))]
    (is (= ["text"] (kinds th)))
    (is (= "Code ↕" (apply str (map :text (get-in th [:children 0 :runs])))))
    (is (= "400" (get-in th [:children 0 :runs 1 :style :fontWeight])))))

(deftest an-svg-is-kept-as-markup
  (let [doc  (Jsoup/parse ^String (slurp (io/resource "html/sayvibe-section.bundle.html")))
        host (.selectFirst doc "#__bundler_thumbnail")
        svg  (first (filter #(= "svg" (:kind %))
                            (tree-seq :children :children
                                      (:node (tree/frame host (cascade/compute doc {:viewport sample/viewport})
                                                         {:viewport sample/viewport})))))]
    (is (some? svg))
    (is (= (.outerHtml (.selectFirst host "svg")) (:markup svg)))))
