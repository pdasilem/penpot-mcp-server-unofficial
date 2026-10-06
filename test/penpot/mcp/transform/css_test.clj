(ns penpot.mcp.transform.css-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.real-file :as real]
   [penpot.mcp.transform.css :as css]
   [penpot.mcp.transform.geometry :as geometry]))

(defn- props [{:keys [shape objects]}]
  (into {} (:properties (css/shape->css objects shape))))

(defn- px [n]
  (let [r (/ (Math/round (* 100.0 (double n))) 100.0)]
    (str (if (== r (Math/floor r)) (long r) r) "px")))

(defn- first-leaf [shape]
  (first (filter :text (tree-seq :children :children (:content shape)))))

(deftest every-shape-of-the-file-gets-a-clean-rule
  (doseq [{:keys [shape objects]} (real/shapes)
          :let [{:keys [selector css]} (css/shape->css objects shape)]]
    (is (re-matches #"\.[a-z0-9-]+" selector) (str (:id shape)))
    (is (not (re-find #"null|NaN|nilpx|Infinity" css)) (str (:id shape)))))

(deftest a-shape-outside-a-layout-is-placed-against-its-board
  (doseq [{:keys [shape objects] :as entry} (real/having #(not= :group (:type %)) "shape")
          :let [parent (get objects (:parent-id shape))
                frame  (get objects (:frame-id shape))]
          :when (not (:layout parent))]
    (let [p (props entry)]
      (is (= (px (- (geometry/x shape) (or (some-> frame geometry/x) 0))) (get p "left")) (str (:id shape)))
      (is (= (px (- (geometry/y shape) (or (some-> frame geometry/y) 0))) (get p "top")) (str (:id shape))))))

(deftest layout-boards-get-their-display
  (doseq [entry (real/having #(= :flex (:layout %)) "flex board")]
    (is (= "flex" (get (props entry) "display")) (str (:id (:shape entry)))))
  (doseq [entry (real/having #(= :grid (:layout %)) "grid board")]
    (is (= "grid" (get (props entry) "display")) (str (:id (:shape entry))))))

(deftest text-takes-its-font-size-from-the-content
  (doseq [{:keys [shape] :as entry} (real/having #(and (= :text (:type %)) (:font-size (first-leaf %))) "text with a font size")]
    (is (= (px (let [v (:font-size (first-leaf shape))] (if (number? v) v (parse-double v))))
           (get (props entry) "font-size"))
        (str (:id shape)))))

(deftest ellipses-are-rounded-and-strokes-become-borders
  (doseq [entry (real/having #(= :circle (:type %)) "ellipse")]
    (is (= "50%" (get (props entry) "border-radius")) (str (:id (:shape entry)))))
  (doseq [entry (real/having #(some (fn [s] (and (pos? (or (:stroke-width s) 0)) (:stroke-color s))) (:strokes %)) "visible stroke")]
    (is (some? (get (props entry) "border")) (str (:id (:shape entry))))))

(deftest shadows-become-box-shadows
  (doseq [entry (real/having #(and (not= :text (:type %)) (some (fn [s] (not (:hidden s))) (:shadow %))) "visible shadow")]
    (is (some? (get (props entry) "box-shadow")) (str (:id (:shape entry))))))

(defn- with-leaf [{:keys [shape] :as entry} attrs]
  (assoc entry :shape (assoc-in shape [:content :children 0 :children 0 :children 0]
                                (merge (get-in shape [:content :children 0 :children 0 :children 0]) attrs))))

(def ^:private a-text
  (delay (real/one #(and (= :text (:type %)) (get-in % [:content :children 0 :children 0 :children 0 :text])) "text")))

(deftest numeric-and-string-text-values-give-the-same-css
  (let [leaf (first-leaf (:shape @a-text))
        as   (fn [f] (props (with-leaf @a-text (into {} (map (fn [k] [k (f (get leaf k))])) [:font-size :font-weight :line-height :letter-spacing]))))
        num  #(if (string? %) (or (some-> % parse-double) %) %)
        text #(if (number? %) (let [d (double %)] (if (== d (Math/rint d)) (str (long d)) (str d))) %)]
    (is (= (as text) (as num)))))

(deftest class-names-of-the-file-are-kebab-case
  (doseq [{:keys [shape]} (real/shapes)]
    (is (re-matches #"[a-z0-9-]+" (css/class-name (:name shape))) (:name shape))))
