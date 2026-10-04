(ns penpot.mcp.html.shapes-test
  (:require
   [app.common.files.changes :as cpc]
   [app.common.files.changes-builder :as pcb]
   [app.common.schema :as sm]
   [app.common.types.shape :as cts]
   [app.common.uuid :as uuid]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.html.cascade :as cascade]
   [penpot.mcp.html.shapes :as shapes]
   [penpot.mcp.html.tree :as tree])
  (:import
   (org.jsoup Jsoup)))

(def ^:private self-auto {:horizontalSizing "auto" :verticalSizing "auto" :margin [0.0 0.0 0.0 0.0]})

(def ^:private style
  {:fontFamily "Inter, sans-serif" :fontSize "14" :fontWeight "700" :fontStyle "normal" :lineHeight "1.2"
   :letterSpacing "0" :textTransform "none" :textDecoration "none" :fills [{:fillColor "#111111" :fillOpacity 1.0}]})

(defn- text [s] {:kind "text" :name s :runs [{:text s :style style}] :grow "auto-width" :self self-auto})

(def ^:private flex-layout
  {:type "flex" :dir "column" :wrap "nowrap" :alignItems "stretch" :justifyContent "start" :alignContent "start"
   :rowGap 4.0 :columnGap 0.0 :padding [1.0 2.0 3.0 4.0]})

(defn- board [name children & {:as extra}]
  (merge {:kind "board" :name name :fills [] :strokes [] :lines [] :shadows [] :radius [0.0 0.0 0.0 0.0]
          :opacity 1.0 :clip false :layout flex-layout :children children :self self-auto}
         extra))

(defn- root [children & {:as extra}]
  (-> (apply board "Screen" children (mapcat identity extra))
      (dissoc :self)
      (assoc :width 640.0 :height nil :sizing {:horizontal "fix" :vertical "auto"})))

(def ^:private fonts
  {:fonts {"Inter" {:font-id "gfont-inter" :font-family "Inter"
                    :variants [{:id "regular" :weight "400" :style "normal"} {:id "700" :weight "700" :style "normal"}
                               {:id "700italic" :weight "700" :style "italic"}]}}
   :fallback {:font-id "sourcesanspro" :font-family "sourcesanspro"
              :variants [{:id "regular" :weight "400" :style "normal"} {:id "bold" :weight "700" :style "normal"}]}})

(defn- build [node] (shapes/frame-objects node (merge {:x 10.0 :y 20.0} fonts)))

(defn- by-name [objects n] (first (filter #(= n (:name %)) objects)))

(defn- valid-changes? [objects]
  (let [page-root (cts/setup-shape {:id uuid/zero :type :frame :name "Root Frame" :x 0 :y 0 :width 0.01 :height 0.01
                                    :frame-id uuid/zero :parent-id uuid/zero})
        changes   (-> (pcb/empty-changes)
                      (pcb/with-page {:id (uuid/next) :objects {uuid/zero page-root}})
                      (pcb/with-objects {uuid/zero page-root})
                      (pcb/add-objects objects))]
    ((sm/validator [:vector cpc/schema:change]) (:redo-changes changes))))

(deftest root-is-placed-at-the-given-point-with-fixed-width
  (let [{:keys [objects root-id]} (build (root [(text "A")]))
        r (first objects)]
    (is (= root-id (:id r)))
    (is (= [:frame uuid/zero uuid/zero] [(:type r) (:parent-id r) (:frame-id r)]))
    (is (= [10.0 20.0 640.0] [(:x r) (:y r) (:width r)]))
    (is (= [:fix :auto] [(:layout-item-h-sizing r) (:layout-item-v-sizing r)]))))

(deftest flex-children-are-added-last-first-so-the-layout-shows-them-in-document-order
  (let [{:keys [objects]} (build (root [(text "First") (text "Second") (text "Third")]))
        kids (filter #(= (:id (first objects)) (:parent-id %)) objects)]
    (is (= ["Third" "Second" "First"] (map :name kids)))))

(deftest grid-children-keep-document-order-and-own-their-cells
  (let [grid {:type "grid" :dir "row" :columns [{:type "flex" :value 1.0} {:type "fixed" :value 80.0}]
              :rows [{:type "auto"}] :rowGap 0.0 :columnGap 8.0 :padding [0.0 0.0 0.0 0.0]}
        a    (assoc (text "A") :cell {:row 1 :column 1 :rowSpan 1 :columnSpan 1})
        b    (assoc (text "B") :cell {:row 1 :column 2 :rowSpan 1 :columnSpan 1})
        {:keys [objects]} (build (root [a b] :layout grid))
        r    (first objects)
        cells (vals (:layout-grid-cells r))]
    (is (= ["A" "B"] (map :name (filter #(= (:id r) (:parent-id %)) objects))))
    (is (= :grid (:layout r)))
    (is (= #{[1 1 [(:id (by-name objects "A"))]] [1 2 [(:id (by-name objects "B"))]]}
           (set (map (juxt :row :column :shapes) cells))))
    (is (= [{:type :flex :value 1.0} {:type :fixed :value 80.0}] (:layout-grid-columns r)))))

(deftest layout-padding-gap-and-child-sizing-are-written
  (let [child (board "Card" [] :self {:horizontalSizing "fill" :verticalSizing "fix" :height 48.0
                                      :margin [0.0 0.0 6.0 0.0] :maxWidth 300.0})
        {:keys [objects]} (build (root [child]))
        r (first objects)
        c (by-name objects "Card")]
    (is (= {:p1 1.0 :p2 2.0 :p3 3.0 :p4 4.0} (:layout-padding r)))
    (is (= {:row-gap 4.0 :column-gap 0.0} (:layout-gap r)))
    (is (= [:column :nowrap :stretch] [(:layout-flex-dir r) (:layout-wrap-type r) (:layout-align-items r)]))
    (is (= [:fill :fix 48.0] [(:layout-item-h-sizing c) (:layout-item-v-sizing c) (:height c)]))
    (is (= {:m1 0.0 :m2 0.0 :m3 6.0 :m4 0.0} (:layout-item-margin c)))
    (is (= 300.0 (:layout-item-max-w c)))))

(deftest text-runs-use-the-resolved-font-and-variant
  (let [{:keys [objects substituted]} (build (root [(text "Hi")]))
        leaf (-> (by-name objects "Hi") :content :children first :children first :children first)]
    (is (= ["gfont-inter" "Inter" "700" "700" "normal"]
           ((juxt :font-id :font-family :font-variant-id :font-weight :font-style) leaf)))
    (is (= "Hi" (:text leaf)))
    (is (= [{:fill-color "#111111" :fill-opacity 1.0}] (:fills leaf)))
    (is (empty? substituted))))

(deftest unknown-families-fall-back-and-are-reported
  (let [node (assoc-in (text "Hi") [:runs 0 :style :fontFamily] "Brand Sans, system-ui")
        {:keys [objects substituted]} (build (root [node]))
        leaf (-> (by-name objects "Hi") :content :children first :children first :children first)]
    (is (= ["sourcesanspro" "bold"] [(:font-id leaf) (:font-variant-id leaf)]))
    (is (= #{"Brand Sans, system-ui"} substituted))))

(deftest line-breaks-start-new-paragraphs
  (let [node {:kind "text" :name "two" :grow "auto-height" :self self-auto
              :runs [{:text "one\ntwo" :style style}]}
        {:keys [objects]} (build (root [node]))
        paras (-> (by-name objects "two") :content :children first :children)]
    (is (= ["one" "two"] (map #(-> % :children first :text) paras)))
    (is (= :auto-height (:grow-type (by-name objects "two"))))))

(deftest one-sided-borders-become-absolute-lines-above-the-children
  (let [{:keys [objects]} (build (root [(text "A")] :lines [{:side "bottom" :width 1.0 :color "#eeeeee" :opacity 1.0}]))
        r    (first objects)
        line (by-name objects "border-bottom")]
    (is (= (:id r) (:parent-id line)))
    (is (= "border-bottom" (:name (last (filter #(= (:id r) (:parent-id %)) objects)))))
    (is (:layout-item-absolute line))
    (is (= [:leftright :bottom] [(:constraints-h line) (:constraints-v line)]))
    (is (= 1.0 (:height line)))))

(deftest images-and-svgs-become-placeholders-for-the-editor
  (let [img {:kind "image" :name "logo" :src "data:image/png;base64,AAAA"
             :self {:horizontalSizing "fix" :verticalSizing "fix" :width 32.0 :height 32.0 :margin [0.0 0.0 0.0 0.0]}}
        svg {:kind "svg" :name "svg" :markup "<svg></svg>" :self self-auto}
        {:keys [objects media]} (build (root [img svg]))
        holder (by-name objects "logo")]
    (is (= :frame (:type holder)))
    (is (= [32.0 32.0 :fix] [(:width holder) (:height holder) (:layout-item-h-sizing holder)]))
    (is (= #{"image" "svg"} (set (map (comp :kind :node) media))))
    (is (= (:id holder) (:id (first (filter #(= "image" (get-in % [:node :kind])) media)))))))

(deftest spacers-are-one-pixel-fill-boards
  (let [{:keys [objects]} (build (root [(board "spacer" [] :spacer true :layout nil
                                               :self {:horizontalSizing "fill" :verticalSizing "auto" :margin [0.0 0.0 0.0 0.0]})]))
        s (by-name objects "spacer")]
    (is (= [1.0 1.0 :fill] [(:width s) (:height s) (:layout-item-h-sizing s)]))))

(deftest decoration-is-written-in-penpot-form
  (let [node (root [] :fills [{:fillColor "#ffffff" :fillOpacity 1.0}]
                   :strokes [{:strokeColor "#dddddd" :strokeOpacity 1.0 :strokeWidth 1.0 :strokeStyle "solid" :strokeAlignment "inner"}]
                   :shadows [{:style "drop-shadow" :offsetX 0.0 :offsetY 2.0 :blur 4.0 :spread 0.0 :hidden false
                              :color {:color "#000000" :opacity 0.1}}]
                   :radius [8.0 8.0 0.0 0.0] :clip true)
        r    (first (:objects (build node)))]
    (is (= [{:fill-color "#ffffff" :fill-opacity 1.0}] (:fills r)))
    (is (= [:inner 1.0] ((juxt :stroke-alignment :stroke-width) (first (:strokes r)))))
    (is (= [:drop-shadow 2.0] ((juxt :style :offset-y) (first (:shadow r)))))
    (is (= [8.0 8.0 0.0 0.0] ((juxt :r1 :r2 :r3 :r4) r)))
    (is (false? (:show-content r)))))

(deftest a-parsed-html-frame-builds-schema-valid-changes
  (testing "flex, grid, table, text and borders from real markup"
    (let [doc  (Jsoup/parse (str "<style>.s{width:600px;display:flex;flex-direction:column;gap:8px;padding:12px}"
                                 ".g{display:grid;grid-template-columns:1fr 2fr;gap:4px}.b{border-bottom:1px solid #ccc}</style>"
                                 "<div class=s><h1>Title</h1><div class=g><span>a</span><span>b</span></div>"
                                 "<p class=b>Hello <b>bold</b><br>next</p><table><tr><td>x</td><td>y</td></tr></table></div>"))
          comp (cascade/compute doc {:viewport 1440})
          node (:node (tree/frame (.selectFirst doc ".s") comp {:viewport 1440}))
          {:keys [objects]} (build node)]
      (is (< 5 (count objects)))
      (is (= (count objects) (count (distinct (map :id objects)))))
      (is (valid-changes? objects)))))
