(ns penpot.mcp.design.usage-test
  (:require
   [penpot.mcp.penpot.tokens-lib :as ctob]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.usage :as usage]
   [penpot.mcp.replay :as replay]))

(def ^:private file
  (delay (first (replay/penpot-answers "token-usage/saved" :get-file))))

(defn- pages []
  (map #(get-in @file [:data :pages-index %]) (get-in @file [:data :pages])))

(defn- tokens []
  (let [lib (get-in @file [:data :tokens-lib])]
    (for [s (ctob/get-sets lib) t (vals (ctob/get-tokens lib (ctob/get-id s)))]
      {:set (ctob/get-name s) :name (:name t) :type (:type t) :value (:value t)})))

(def ^:private facts (delay (mapv usage/page-facts (pages))))

(def ^:private report (delay (usage/report {:tokens (tokens) :facts @facts})))

(defn- shapes-by-id []
  (into {} (for [p (pages) [id s] (:objects p)] [id s])))

(defn- raw-entries []
  (for [f @facts g (:raw f) v (:values g)] (merge (dissoc g :values) v)))

(defn- references [value]
  (set (map second (re-seq #"\{([^}]*)\}" (pr-str value)))))

(deftest unused-tokens-are-applied-nowhere-and-referenced-by-no-used-token
  (let [applied (set (mapcat (comp vals :applied-tokens) (vals (shapes-by-id))))
        unused  (set (map :name (:unused @report)))
        live    (remove (comp unused :name) (tokens))]
    (is (seq unused))
    (is (not-any? applied unused))
    (is (not-any? #(some unused (references (:value %))) live))))

(deftest tokens-used-only-through-references-are-applied-nowhere
  (let [applied (set (mapcat (comp vals :applied-tokens) (vals (shapes-by-id))))]
    (is (seq (:referenced-only @report)))
    (is (not-any? applied (:referenced-only @report)))))

(deftest a-raw-value-is-never-bound-to-a-token-on-its-shape
  (let [by-id (shapes-by-id)]
    (doseq [{:keys [shape-id attribute]} (raw-entries)
            :let [applied (:applied-tokens (by-id shape-id))]]
      (is (not (contains? applied attribute)) (pr-str [shape-id attribute]))
      (when (= :font-size attribute)
        (is (not (contains? applied :typography)) (str shape-id))))))

(deftest copies-zeros-and-library-styles-are-not-raw
  (let [by-id (shapes-by-id)
        raw   (raw-entries)]
    (is (seq raw))
    (is (not-any? #(:shape-ref (by-id (:shape-id %))) raw))
    (is (not-any? #(and (number? (:value %)) (zero? (:value %))) raw))))

(deftest only-the-gaps-a-flex-layout-uses-are-raw
  (let [by-id (shapes-by-id)]
    (doseq [{:keys [shape-id attribute]} (raw-entries)
            :when (#{:row-gap :column-gap} attribute)
            :let [{:keys [layout layout-flex-dir layout-wrap-type]} (by-id shape-id)]
            :when (and (= :flex layout) (not= :wrap layout-wrap-type))]
      (is (= attribute (if (#{:row :row-reverse} layout-flex-dir) :column-gap :row-gap)) (str shape-id)))))

(deftest the-raw-values-of-each-board-stay-together
  (doseq [f @facts]
    (let [frames (map :frame-id (:raw f))]
      (is (= (count (distinct frames)) (count (partition-by identity frames))) (str (:page f))))))

(deftest the-raw-window-keeps-only-the-asked-slice-of-every-value
  (let [flat   (raw-entries)
        window (reduce usage/add-raw (usage/raw-window 10 25 nil) @facts)
        sliced (for [g (:groups window) v (:values g)] (merge (dissoc g :values :page :page-id) v))]
    (is (= (count flat) (:count window)))
    (is (= (map #(dissoc % :page :page-id) (take 25 (drop 10 flat))) sliced))))

(deftest the-window-of-one-page-counts-only-that-page
  (let [page   (first @facts)
        window (reduce usage/add-raw (usage/raw-window 0 1000000 (:page-id page)) @facts)]
    (is (= (reduce + (map (comp count :values) (:raw page))) (:count window)))))
