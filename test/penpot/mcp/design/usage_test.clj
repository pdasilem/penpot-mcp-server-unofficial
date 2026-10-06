(ns penpot.mcp.design.usage-test
  (:require
   [app.common.types.shape :as cts]
   [app.common.uuid :as uuid]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.design.usage :as usage]))

(def ^:private screen-id (parse-uuid "a0000000-0000-0000-0000-000000000001"))
(def ^:private card-id (parse-uuid "a0000000-0000-0000-0000-000000000002"))
(def ^:private button-id (parse-uuid "a0000000-0000-0000-0000-000000000003"))
(def ^:private label-id (parse-uuid "a0000000-0000-0000-0000-000000000004"))
(def ^:private copy-id (parse-uuid "a0000000-0000-0000-0000-000000000005"))
(def ^:private loose-id (parse-uuid "a0000000-0000-0000-0000-000000000006"))

(defn- shape [attrs]
  (cts/setup-shape (merge {:x 0 :y 0 :width 10 :height 10} attrs)))

(defn- root [children]
  (shape {:id uuid/zero :type :frame :name "Root Frame" :frame-id uuid/zero :parent-id uuid/zero :shapes children}))

(defn- text-content [leaf]
  {:type "root"
   :children [{:type "paragraph-set"
               :children [{:type "paragraph" :children [(merge {:text "Hi" :font-family "Inter"} leaf)]}]}]})

(def ^:private screen
  (shape {:id screen-id :type :frame :name "Home" :width 1440 :height 900
          :frame-id uuid/zero :parent-id uuid/zero :shapes [card-id copy-id]}))

(def ^:private card
  (shape {:id card-id :type :frame :name "Card" :width 320 :height 200
          :frame-id screen-id :parent-id screen-id :shapes [button-id label-id]
          :layout :flex :layout-flex-dir :column
          :layout-padding-type :multiple :layout-padding {:p1 16 :p2 0 :p3 24 :p4 0}
          :layout-gap {:row-gap 16 :column-gap 0}
          :r1 8 :r2 8 :r3 8 :r4 8
          :fills [{:fill-color "#FFFFFF" :fill-opacity 1}]
          :applied-tokens {:r1 "radius.md" :r2 "radius.md" :r3 "radius.md" :r4 "radius.md"}}))

(def ^:private button
  (shape {:id button-id :type :rect :name "Button" :frame-id card-id :parent-id card-id
          :r1 0 :r2 0 :r3 0 :r4 0
          :fills [{:fill-color "#3366FF" :fill-opacity 1}]
          :strokes [{:stroke-color "#000000" :stroke-opacity 0.5 :stroke-width 2 :stroke-style :solid :stroke-alignment :inner}]
          :applied-tokens {:fill "color.primary"}}))

(def ^:private label
  (shape {:id label-id :type :text :name "Label" :frame-id card-id :parent-id card-id
          :content (text-content {:font-size "15" :fills [{:fill-color "#111111" :fill-opacity 1 :fill-color-ref-id (uuid/next)}]})}))

(def ^:private copy
  (shape {:id copy-id :type :rect :name "Button copy" :frame-id screen-id :parent-id screen-id
          :shape-ref button-id :component-id (uuid/next) :component-root true
          :r1 6 :r2 6 :r3 6 :r4 6
          :fills [{:fill-color "#3366FF" :fill-opacity 1}]
          :applied-tokens {:fill "color.primary"}}))

(def ^:private loose
  (shape {:id loose-id :type :frame :name "Loose" :width 100 :height 50
          :frame-id uuid/zero :parent-id uuid/zero :shapes []
          :fills [{:fill-color "#3366FF" :fill-opacity 1}]}))

(def ^:private home
  {:id (parse-uuid "b0000000-0000-0000-0000-000000000001") :name "Home"
   :objects {uuid/zero (root [screen-id loose-id]) screen-id screen card-id card button-id button
             label-id label copy-id copy loose-id loose}})

(def ^:private library-page
  {:id (parse-uuid "b0000000-0000-0000-0000-000000000002") :name "Components"
   :objects {uuid/zero (root [])}})

(defn- token [set-name name type value]
  {:set set-name :name name :type type :value value})

(def ^:private tokens
  [(token "core" "space.base" :spacing "4")
   (token "core" "space.lg" :spacing "{space.base} * 4")
   (token "core" "radius.base" :border-radius "{space.base}")
   (token "core" "radius.md" :border-radius "{radius.base} * 2")
   (token "core" "radius.orphan" :border-radius "{space.lg}")
   (token "light" "color.primary" :color "#3366FF")
   (token "dark" "color.primary" :color "#88AAFF")
   (token "core" "color.unused" :color "#FF0000")
   (token "core" "type.body" :typography {:font-size "{space.lg}" :font-family ["Inter"]})])

(def ^:private scale
  [{:name "space.base" :type :spacing :value {:kind :dimension :value 4.0 :unit "px"}}
   {:name "space.lg" :type :spacing :value {:kind :dimension :value 16.0 :unit "px"}}
   {:name "radius.md" :type :border-radius :value {:kind :dimension :value 8.0 :unit "px"}}
   {:name "color.primary" :type :color :value {:kind :color :rgba {:r 51 :g 102 :b 255 :a 1.0}}}
   {:name "font.size.l" :type :font-size :value {:kind :dimension :value 16.0 :unit "px"}}])

(defn- with-raw [result facts scale-tokens]
  (let [window (reduce usage/add-raw (usage/raw-window 0 1000 nil) facts)]
    (assoc result :raw (usage/with-matches scale-tokens (:groups window)) :raw-count (:count window))))

(defn- entries [result]
  (for [g (:raw result) v (:values g)]
    (merge (dissoc g :values) v)))

(defn- report-on [pages]
  (let [facts (mapv usage/page-facts pages)]
    (with-raw (usage/report {:tokens tokens :facts facts}) facts scale)))

(defn- report []
  (report-on [home library-page]))

(defn- raw [result shape-id attribute]
  (filter #(and (= shape-id (:shape-id %)) (= attribute (:attribute %))) (entries result)))

(deftest a-token-is-unused-when-no-shape-applies-it-and-no-used-token-references-it
  (let [result (report)]
    (is (= ["color.unused" "radius.orphan" "space.lg" "type.body"] (mapv :name (:unused result))))
    (is (= [{:set "core" :value "{space.lg}"}] (:sets (first (filter #(= "radius.orphan" (:name %)) (:unused result))))))))

(deftest tokens-kept-alive-only-through-references-are-listed-apart
  (is (= ["radius.base" "space.base"] (:referenced-only (report)))))

(deftest references-list-every-token-that-contains-another
  (is (= [{:name "radius.base" :references ["space.base"] :live true}
          {:name "radius.md" :references ["radius.base"] :live true}
          {:name "radius.orphan" :references ["space.lg"] :live false}
          {:name "space.lg" :references ["space.base"] :live false}
          {:name "type.body" :references ["space.lg"] :live false}]
         (:references (report)))))

(deftest usage-counts-shapes-copies-pages-and-attributes
  (let [by-name (into {} (map (juxt :name identity)) (:usage (report)))]
    (is (= {:name "color.primary" :shapes 2 :copies 1 :attributes [:fill]
            :pages [{:id (:id home) :name "Home" :shapes 2}]}
           (by-name "color.primary")))
    (is (= [:r1 :r2 :r3 :r4] (:attributes (by-name "radius.md"))))
    (is (= 1 (:shapes (by-name "radius.md"))))
    (is (= ["color.primary" "radius.md"] (mapv :name (:usage (report)))))))

(deftest raw-values-skip-tokens-zeros-library-styles-and-copies
  (let [result (report)]
    (testing "a value bound to a token is not raw"
      (is (empty? (raw result card-id :r1)))
      (is (empty? (raw result button-id :fill))))
    (testing "zeros are not raw"
      (is (empty? (raw result card-id :p2)))
      (is (empty? (raw result card-id :column-gap)))
      (is (empty? (raw result button-id :r1))))
    (testing "a library color is not raw"
      (is (empty? (raw result label-id :fill))))
    (testing "copies take their values from the main component"
      (is (empty? (filter #(= copy-id (:shape-id %)) (entries result)))))
    (testing "top-level boards keep their size"
      (is (empty? (raw result screen-id :width)))
      (is (empty? (raw result loose-id :width))))))

(deftest raw-values-carry-the-frame-and-how-they-fit-the-scale
  (let [result (report)
        [p1]   (raw result card-id :p1)
        [p3]   (raw result card-id :p3)]
    (is (= {:page-id (:id home) :page "Home" :frame-id screen-id :frame "Home"
            :shape-id card-id :shape "Card" :attribute :p1 :value 16.0 :matches ["space.lg"]}
           p1))
    (is (= [] (:matches p3)))
    (is (= ["space.lg"] (:matches (first (raw result card-id :row-gap)))) "gaps are spacing")
    (is (= [] (:matches (first (raw result label-id :font-size)))) "15 is off the scale")))

(deftest colors-strokes-and-fixed-sizes-are-checked
  (let [result (report)]
    (is (= {:value "#ffffff" :matches []} (select-keys (first (raw result card-id :fill)) [:value :matches])))
    (is (= {:value "#000000" :opacity 0.5 :matches []}
           (select-keys (first (raw result button-id :stroke-color)) [:value :opacity :matches])))
    (is (= 2.0 (:value (first (raw result button-id :stroke-width)))))
    (is (= [320.0 200.0] (mapv #(:value (first (raw result card-id %))) [:width :height])))
    (is (= {:frame-id loose-id :frame "Loose" :matches ["color.primary"]}
           (select-keys (first (raw result loose-id :fill)) [:frame-id :frame :matches])))))

(deftest hugging-boards-and-library-typographies-are-not-raw
  (let [hug    (assoc card :layout-item-h-sizing :auto :layout-item-v-sizing :fill)
        styled (assoc label :content (text-content {:font-size "15" :typography-ref-id (uuid/next)}))
        page   (update home :objects assoc card-id hug label-id styled)
        result (report-on [page])]
    (is (empty? (raw result card-id :width)))
    (is (empty? (raw result card-id :height)))
    (is (empty? (raw result label-id :font-size)))))

(deftest without-a-scale-raw-values-are-reported-without-matches
  (let [facts  [(usage/page-facts home)]
        result (with-raw (usage/report {:tokens tokens :facts facts}) facts nil)]
    (is (seq (entries result)))
    (is (not-any? #(contains? % :matches) (entries result)))))

(deftest the-summary-counts-the-whole-file
  (is (= {:tokens 8 :applied 2 :missing 0 :referenced-only 2 :unused 4 :shapes 6}
         (:summary (report))))
  (is (= 11 (:raw-count (report)))))

(deftest raw-values-follow-the-layer-tree-so-each-frame-stays-together
  (let [frames (map :frame-id (entries (report)))]
    (is (= (count (distinct frames)) (count (partition-by identity frames))))
    (is (= [screen-id loose-id] (distinct frames)))))

(deftest a-layer-cycle-does-not-loop
  (let [page (assoc-in home [:objects card-id :shapes] [button-id card-id screen-id])]
    (is (= 5 (:shapes (usage/page-facts page))))))

(deftest tokens-applied-to-shapes-but-gone-from-the-catalog-are-missing
  (let [page   (assoc-in home [:objects button-id :applied-tokens] {:fill "color.primary" :stroke-color "color.gone"})
        result (report-on [page])]
    (is (= [{:name "color.gone" :shapes 1 :copies 0 :attributes [:stroke-color]
             :pages [{:id (:id home) :name "Home" :shapes 1}]}]
           (:missing result)))
    (is (= 1 (get-in result [:summary :missing])))
    (is (not-any? #(= "color.gone" (:name %)) (:usage result)))
    (is (empty? (raw result button-id :stroke-color)) "a bound attribute is not raw even when its token is gone")))

(defn- gap-attributes [layout]
  (let [page (update-in home [:objects card-id] merge layout {:layout-gap {:row-gap 8 :column-gap 10}})]
    (into #{} (comp (filter #(= card-id (:shape-id %))) (map :attribute) (filter #{:row-gap :column-gap}))
          (entries (report-on [page])))))

(deftest only-the-gaps-the-layout-uses-are-raw
  (is (= #{:column-gap} (gap-attributes {:layout :flex :layout-flex-dir :row :layout-wrap-type :nowrap})))
  (is (= #{:row-gap} (gap-attributes {:layout :flex :layout-flex-dir :column-reverse :layout-wrap-type :nowrap})))
  (is (= #{:row-gap :column-gap} (gap-attributes {:layout :flex :layout-flex-dir :row :layout-wrap-type :wrap})))
  (is (= #{:row-gap :column-gap} (gap-attributes {:layout :grid}))))

(deftest a-typography-or-font-size-token-on-a-text-covers-its-font-size
  (doseq [applied [{:typography "type.body"} {:font-size "space.lg"}]]
    (let [page (assoc-in home [:objects label-id :applied-tokens] applied)]
      (is (empty? (raw (report-on [page]) label-id :font-size)) (pr-str applied)))))

(deftest page-facts-name-their-page
  (is (= (:id home) (:page-id (usage/page-facts home)))))

(deftest raw-values-are-kept-once-per-shape
  (let [groups (:raw (report))]
    (is (= (count groups) (count (distinct (map :shape-id groups)))))
    (is (= 11 (reduce + (map (comp count :values) groups))))))

(defn- window [offset limit page-id]
  (let [facts [{:page-id :p1 :page "One" :raw [{:shape-id 1 :values [:a :b :c]} {:shape-id 2 :values [:d]}]}
               {:page-id :p2 :page "Two" :raw [{:shape-id 3 :values [:e :f]}]}]]
    (select-keys (reduce usage/add-raw (usage/raw-window offset limit page-id) facts) [:count :groups])))

(deftest the-raw-window-counts-every-value-and-keeps-only-the-asked-slice
  (is (= {:count 6 :groups [{:page-id :p1 :page "One" :shape-id 1 :values [:b :c]}
                            {:page-id :p1 :page "One" :shape-id 2 :values [:d]}
                            {:page-id :p2 :page "Two" :shape-id 3 :values [:e]}]}
         (window 1 4 nil)))
  (is (= {:count 6 :groups [{:page-id :p2 :page "Two" :shape-id 3 :values [:f]}]} (window 5 10 nil)))
  (is (= {:count 6 :groups []} (window 6 10 nil)))
  (is (= {:count 0 :groups []} (window 0 0 :p3)))
  (is (= {:count 2 :groups [{:page-id :p2 :page "Two" :shape-id 3 :values [:e :f]}]} (window 0 10 :p2))))
