(ns penpot.mcp.html.tree
  (:require
   [clojure.string :as str]
   [penpot.mcp.html.box :as box]
   [penpot.mcp.html.css.values :as v]
   [penpot.mcp.html.sizing :as sizing]
   [penpot.mcp.html.text :as text])
  (:import
   (org.jsoup.nodes Element TextNode)))

(defn- style-of [ctx el] (get-in (:computed ctx) [el :style]))

(defn- px-ctx [style ctx]
  {:font-size (or (v/px (get style "font-size" "16px") {}) 16.0) :root-font-size 16.0 :viewport (:viewport ctx)})

(defn- note! [ctx feature]
  (swap! (:unsupported ctx) update feature (fnil inc 0)))

(defn- set-value? [style prop]
  (not (contains? #{nil "none"} (get style prop))))

(defn- check-unsupported! [ctx ^Element el style]
  (when (set-value? style "float") (note! ctx "float"))
  (when (#{"fixed" "sticky"} (get style "position")) (note! ctx (str "position:" (get style "position"))))
  (when (set-value? style "transform") (note! ctx "transform"))
  (when (set-value? style "filter") (note! ctx "filter"))
  (when (set-value? style "animation-name") (note! ctx "animation"))
  (when (some-> (get style "background-image") (str/includes? "url(")) (note! ctx "background-image url"))
  (when (some-> (get style "background-image") (str/includes? "radial-gradient")) (note! ctx "radial-gradient"))
  (when (= "iframe" (.tagName el)) (note! ctx "iframe")))

(defn- hidden? [style]
  (or (= "none" (get style "display")) (#{"hidden" "collapse"} (get style "visibility"))))

(defn- inline? [style] (= "inline" (get style "display")))

(defn- flex? [style] (#{"flex" "inline-flex"} (get style "display")))

(defn- table? [style] (= "table" (get style "display")))

(defn- boxy? [style ctx]
  (let [pc (px-ctx style ctx)
        d  (box/decoration style pc)
        sp (box/spacing style pc)]
    (boolean (or (seq (:fills d)) (seq (:strokes d)) (seq (:lines d)) (seq (:shadows d)) (:clip d)
                 (some pos? (:padding sp))
                 (v/px (get style "width" "auto") pc) (v/px (get style "height" "auto") pc)
                 (flex? style) (table? style) (= "grid" (get style "display"))
                 (not= 1.0 (:opacity d))))))

(defn- text-content? [ctx node]
  (or (instance? TextNode node)
      (and (instance? Element node)
           (let [style (style-of ctx node)]
             (or (= "br" (.tagName ^Element node))
                 (and (inline? style) (not (hidden? style)) (not (boxy? style ctx))
                      (not (#{"svg" "img"} (.tagName ^Element node)))))))))

(defn- node-name [^Element el]
  (str (.tagName el) (some->> (first (.classNames el)) (str "."))))

(defn- text-name [{:keys [runs]}]
  (let [t (str/trim (apply str (map :text runs)))]
    (subs t 0 (min 40 (count t)))))

(defn- row? [dir] (str/starts-with? (str dir) "row"))

(defn- text-node [content self parent]
  (let [fill? (= "fill" (:horizontalSizing self))]
    {:kind "text"
     :name (text-name content)
     :runs (:runs content)
     :align (:align content)
     :grow (cond (:nowrap content) "auto-width" fill? "auto-height" (row? (:dir parent)) "auto-width" :else "auto-height")
     :self (if (and (:nowrap content) fill?) (assoc self :horizontalSizing "auto") self)}))

(defn- anonymous-self [parent]
  {:horizontalSizing (if (and (not (row? (:dir parent))) (= "stretch" (:alignItems parent))) "fill" "auto")
   :verticalSizing "auto"
   :margin [0.0 0.0 0.0 0.0]})

(declare element-node)

(def ^:private spacer
  {:kind "board" :name "spacer" :spacer true :fills [] :strokes [] :lines [] :shadows [] :radius [0.0 0.0 0.0 0.0]
   :opacity 1.0 :clip false :children []
   :self {:horizontalSizing "fill" :verticalSizing "auto" :margin [0.0 0.0 0.0 0.0]}})

(defn- with-spacers [nodes]
  (vec (mapcat (fn [n] (if (get-in n [:self :push-right]) [spacer (update n :self dissoc :push-right)] [n])) nodes)))

(defn- block-margins [nodes style ctx]
  (let [pc       (px-ctx style ctx)
        {:keys [padding]} (box/spacing style pc)
        border   #(or (v/px (get style (str "border-" % "-width") "0") pc) 0.0)
        open-top (and (zero? (first padding)) (zero? (border "top")))
        open-bot (and (zero? (nth padding 2)) (zero? (border "bottom")))
        margin   (fn [n i] (get-in n [:self :margin i] 0.0))
        collapsed (reduce (fn [out n]
                            (if-let [prev (peek out)]
                              (let [gap (max (margin prev 2) (margin n 0))]
                                (conj out (assoc-in n [:self :margin 0] (- gap (margin prev 2)))))
                              (conj out n)))
                          []
                          nodes)]
    (cond-> collapsed
      (and open-top (seq collapsed) (:self (first collapsed))) (assoc-in [0 :self :margin 0] 0.0)
      (and open-bot (seq collapsed) (:self (peek collapsed))) (assoc-in [(dec (count collapsed)) :self :margin 2] 0.0))))

(defn- children [^Element el style ctx parent]
  (let [item?    (if (or (flex? style) (= "grid" (get style "display")))
                   #(instance? TextNode %)
                   #(text-content? ctx %))
        segments (partition-by item? (.childNodes el))
        pseudo   (fn [which] (when-let [p (get-in (:computed ctx) [el :pseudo which])]
                               (some-> (text/pseudo p) (text-node (anonymous-self parent) parent))))
        items    (mapcat (fn [seg]
                           (if (item? (first seg))
                             (some-> (text/segment seg style (:computed ctx)) (text-node (anonymous-self parent) parent) vector)
                             (keep #(when (instance? Element %) (element-node % ctx parent)) seg)))
                         segments)
        nodes    (with-spacers (remove nil? (concat [(pseudo "before")] items [(pseudo "after")])))]
    (if (or (flex? style) (= "grid" (get style "display")))
      nodes
      (block-margins nodes style ctx))))

(defn- inline-flow? [^Element el ctx]
  (every? #(or (text-content? ctx %)
               (and (instance? Element %) (#{"inline" "inline-block"} (get (style-of ctx %) "display"))))
          (.childNodes el)))

(defn- layout-for [^Element el style ctx]
  (let [pc     (px-ctx style ctx)
        layout (sizing/container style pc)]
    (cond
      (flex? style) layout
      (and (#{"inline" "inline-block"} (get style "display")) (inline-flow? el ctx))
      (assoc layout :dir "row" :alignItems "center" :wrap "nowrap")
      (and (inline-flow? el ctx) (some #(and (instance? Element %) (not (text-content? ctx %))) (.childNodes el)))
      (assoc layout :dir "row" :wrap "wrap" :alignItems "center" :columnGap 4.0 :rowGap 4.0)
      :else layout)))

(defn- decorated [style ctx]
  (dissoc (box/decoration style (px-ctx style ctx)) :padding))

(defn- board-node [^Element el style ctx parent self]
  (let [layout (layout-for el style ctx)
        fixed  (or (#{"fix" "fill"} (:verticalSizing self)) (:fixed-height parent))
        info   {:dir (:dir layout) :alignItems (:alignItems layout) :block (not (flex? style))
                :fixed-height (boolean (and fixed (#{"fix" "fill"} (:verticalSizing self))))}]
    (merge {:kind "board" :name (node-name el) :layout layout :self self
            :children (children el style ctx info)}
           (decorated style ctx))))

(defn- table-rows [^Element table]
  (mapcat (fn [^Element c]
            (case (.tagName c)
              "tr" [c]
              ("thead" "tbody" "tfoot") (filter #(= "tr" (.tagName ^Element %)) (.children c))
              nil))
          (.children table)))

(defn- span-of [^Element cell attr]
  (max 1 (or (parse-long (str/trim (.attr cell ^String attr))) 1)))

(defn- cell-node [^Element cell ctx row col]
  (let [style (style-of ctx cell)
        self  {:horizontalSizing "fill" :verticalSizing "fill" :margin [0.0 0.0 0.0 0.0]}
        node  (board-node cell style ctx {:dir "row" :alignItems "stretch"} self)]
    (assoc node :cell {:row row :column col :rowSpan (span-of cell "rowspan") :columnSpan (span-of cell "colspan")})))

(defn- table-node [^Element el style ctx self]
  (let [rows   (table-rows el)
        cells  (map-indexed (fn [ri ^Element tr]
                              (:cells (reduce (fn [{:keys [col cells]} ^Element td]
                                                {:col (+ col (span-of td "colspan"))
                                                 :cells (conj cells (cell-node td ctx (inc ri) col))})
                                              {:col 1 :cells []}
                                              (filter #(#{"td" "th"} (.tagName ^Element %)) (.children tr)))))
                            rows)
        ncols  (reduce max 1 (map (fn [cs] (reduce + (map #(get-in % [:cell :columnSpan]) cs))) cells))]
    (merge {:kind "board" :name (node-name el) :self self
            :layout {:type "grid" :dir "row"
                     :columns (vec (repeat ncols {:type "flex" :value 1}))
                     :rows (vec (repeat (count rows) {:type "auto"}))
                     :rowGap 0.0 :columnGap 0.0 :padding (:padding (box/spacing style (px-ctx style ctx)))}
            :children (vec (apply concat cells))}
           (decorated style ctx))))

(defn- element-node [^Element el ctx parent]
  (let [style (style-of ctx el)]
    (when-not (hidden? style)
      (check-unsupported! ctx el style)
      (let [self (sizing/child style parent (px-ctx style ctx))]
        (case (.tagName el)
          "svg" {:kind "svg" :name "svg" :markup (.outerHtml el) :self self}
          "img" {:kind "image" :name (or (not-empty (.attr el "alt")) "image") :src (.attr el "src") :self self}
          "iframe" nil
          (cond
            (table? style) (table-node el style ctx self)
            (or (boxy? style ctx) (not (every? #(text-content? ctx %) (.childNodes el)))) (board-node el style ctx parent self)
            :else (some-> (text/content el (:computed ctx)) (text-node self parent))))))))

(defn frame [^Element el computed {:keys [viewport]}]
  (let [ctx    {:computed computed :viewport viewport :unsupported (atom {})}
        style  (get-in computed [el :style])
        pc     (px-ctx style ctx)
        width  (or (v/px (get style "width" "auto") pc) (sizing/frame-width el computed viewport))
        height (or (v/px (get style "height" "auto") pc) (sizing/aspect-height style width))
        node   (board-node el style ctx nil {:horizontalSizing "fix" :verticalSizing (if height "fix" "auto")})]
    {:node (-> node
               (dissoc :self)
               (assoc :width width :height height
                      :sizing {:horizontal "fix" :vertical (if height "fix" "auto")}))
     :unsupported @(:unsupported ctx)}))
