(ns penpot.mcp.html.shapes-test
  (:require
   [app.common.files.changes :as cpc]
   [app.common.schema :as sm]
   [app.common.transit :as ct]
   [app.common.types.shape]
   [app.common.uuid :as uuid]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.jobs :as jobs]
   [penpot.mcp.html.sample :as sample]
   [penpot.mcp.html.shapes :as shapes]
   [penpot.mcp.html.tree :as tree]
   [penpot.mcp.penpot.shape :as shape]
   [penpot.mcp.penpot.transit :as transit]
   [penpot.mcp.replay :as replay]))

(def ^:private fallback
  (delay (@#'jobs/font-entry (some #(get-in % [:result :fallback]) (replay/editor-answers "import/sections")))))

(defn- node [i]
  (:node (tree/frame (nth (.select (sample/doc) ".desk") i) (sample/computed) {:viewport sample/viewport})))

(defn- build [i]
  (shapes/frame-objects (node i) {:x 10.0 :y 20.0 :fonts {} :fallback @fallback}))

(defn- by-name [objects n] (first (filter #(= n (:name %)) objects)))

(defn- children-of [objects parent]
  (filter #(= (:id parent) (:parent-id %)) objects))

(defn- valid-changes? [objects]
  (let [as-penpot-reads-them (ct/decode-str (transit/encode (shape/add-objects (uuid/next) objects)))]
    ((sm/validator [:vector cpc/schema:change]) as-penpot-reads-them)))

(deftest the-root-is-placed-at-the-given-point-with-the-frame-size
  (let [{:keys [objects root-id]} (build 0)
        r (first objects)
        n (node 0)]
    (is (= root-id (:id r)))
    (is (= [:frame uuid/zero uuid/zero] [(:type r) (:parent-id r) (:frame-id r)]))
    (is (= [10.0 20.0 (:width n) (:height n)] [(:x r) (:y r) (:width r) (:height r)]))))

(deftest flex-children-are-added-last-first-so-the-layout-shows-them-in-document-order
  (let [{:keys [objects]} (build 0)]
    (is (= (reverse (map :name (:children (node 0)))) (map :name (children-of objects (first objects)))))))

(deftest table-cells-keep-document-order-and-own-their-cells
  (let [{:keys [objects]} (build 0)
        table (by-name objects "table")
        kids  (children-of objects table)
        cells (vals (:layout-grid-cells table))]
    (is (= :grid (:layout table)))
    (is (= (map :name (:children (first (filter #(= "table" (:name %)) (tree-seq :children :children (node 0))))))
           (map :name kids)))
    (is (= (set (map (juxt (comp :row :cell) (comp :column :cell)) (filter :cell (tree-seq :children :children (node 0)))))
           (set (map (juxt :row :column) cells))))
    (is (every? #(= 1 (count (:shapes %))) cells))))

(deftest layout-padding-gap-and-child-sizing-are-written
  (let [{:keys [objects]} (build 0)
        main (by-name objects "div.main")
        nav  (by-name objects "div.nav")]
    (is (= {:p1 18.0 :p2 20.0 :p3 18.0 :p4 20.0} (:layout-padding main)))
    (is (= 12.0 (get-in main [:layout-gap :row-gap])))
    (is (= :column (:layout-flex-dir main)))
    (is (= [:fill :fill] [(:layout-item-h-sizing main) (:layout-item-v-sizing main)]))
    (is (= [:fix 210.0] [(:layout-item-h-sizing nav) (:width nav)]))))

(deftest generic-font-stacks-fall-back-to-the-editor-font-and-are-reported
  (let [{:keys [objects substituted]} (build 0)
        leaf (-> (by-name objects "SayVibe Console") :content :children first :children first :children first)]
    (is (= ["sourcesanspro" "sourcesanspro" "bold" "700"]
           ((juxt :font-id :font-family :font-variant-id :font-weight) leaf)))
    (is (= "SayVibe Console" (:text leaf)))
    (is (= [{:fill-color "#1a1d20" :fill-opacity 1.0}] (:fills leaf)))
    (is (= #{"ui-sans-serif,system-ui,sans-serif"} substituted))))

(deftest a-one-sided-border-becomes-an-absolute-line-above-the-children
  (let [{:keys [objects]} (build 0)
        top  (by-name objects "div.top")
        line (first (filter #(= "border-bottom" (:name %)) (children-of objects top)))]
    (is (= "border-bottom" (:name (last (children-of objects top)))))
    (is (:layout-item-absolute line))
    (is (= [:leftright :bottom] [(:constraints-h line) (:constraints-v line)]))
    (is (= 1.0 (:height line)))
    (is (= [{:fill-color "#e3e6e8" :fill-opacity 1.0}] (:fills line)))))

(deftest a-spacer-is-a-one-pixel-filling-board
  (let [s (by-name (:objects (build 0)) "spacer")]
    (is (= [1.0 1.0 :fill] [(:width s) (:height s) (:layout-item-h-sizing s)]))))

(deftest decoration-is-written-in-penpot-form
  (let [r (first (:objects (build 0)))]
    (is (= [{:fill-color "#ffffff" :fill-opacity 1.0}] (:fills r)))
    (is (= [:inner 1.0 "#c9cdd0"] ((juxt :stroke-alignment :stroke-width :stroke-color) (first (:strokes r)))))
    (is (= [10.0 10.0 10.0 10.0] ((juxt :r1 :r2 :r3 :r4) r)))
    (is (false? (:show-content r))))
  (let [dialog (first (filter #(seq (:shadow %)) (:objects (build 4))))]
    (is (= [:drop-shadow 8.0 28.0] ((juxt :style :offset-y :blur) (first (:shadow dialog)))))))

(deftest every-real-frame-builds-schema-valid-changes-with-unique-ids
  (doseq [i (range (count (.select (sample/doc) ".desk")))
          :let [{:keys [objects]} (build i)]]
    (is (= (count objects) (count (distinct (map :id objects)))) i)
    (is (valid-changes? objects) i)))
