(ns penpot.mcp.html.chunks-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.chunks :as chunks]))

(defn- text [n] {:kind "text" :name n})
(defn- board [n & kids] {:kind "board" :name n :children (vec kids)})

(defn- names [unit] (mapv :name (tree-seq :children :children (:node unit))))

(deftest small-frame-is-one-call
  (let [calls (chunks/split (board "root" (text "a") (text "b")) 10)]
    (is (= 1 (count calls)))
    (is (= [{:root true :parent nil}] (mapv #(select-keys % [:root :parent]) (first calls))))
    (is (= ["root" "a" "b"] (names (ffirst calls))))))

(deftest large-frame-is-split-in-document-order
  (let [tree  (board "root" (board "nav" (text "n1") (text "n2")) (board "main" (text "m1") (text "m2") (text "m3")) (text "foot"))
        calls (chunks/split tree 3)]
    (is (every? #(<= (reduce + (map (fn [u] (count (names u))) %)) 3) calls))
    (is (= ["root" "nav" "n1" "n2" "main" "m1" "m2" "m3" "foot"] (mapcat #(mapcat names %) calls)))
    (is (= [nil] (map :parent (filter :root (first calls)))))
    (is (= #{"k0" "k0.1"} (set (keep #(get-in % [:node :key]) (apply concat calls)))))
    (is (every? (fn [u] (or (:root u) (:parent u))) (apply concat calls)))))

(deftest split-boards-keep-their-cell-and-own-props
  (let [tree  (board "root" (assoc (board "cell" (text "a") (text "b") (text "c")) :cell {:row 1 :column 2}))
        calls (chunks/split tree 2)
        shell (some #(when (= "cell" (get-in % [:node :name])) %) (apply concat calls))]
    (is (= {:row 1 :column 2} (get-in shell [:node :cell])))
    (is (= [] (get-in shell [:node :children])))))

(deftest lines-are-taken-out-and-their-boards-keyed
  (let [line  {:side "bottom" :width 1.0 :color "#eee" :opacity 1.0}
        tree  (assoc (board "root" (assoc (board "top" (text "t")) :lines [line]) (text "x")) :lines [(assoc line :side "top")])
        {:keys [node lines]} (chunks/extract-lines tree)]
    (is (= [{:key "k0" :side "top" :width 1.0 :color "#eee" :opacity 1.0} {:key "k0.0" :side "bottom" :width 1.0 :color "#eee" :opacity 1.0}] lines))
    (is (= ["k0" "k0.0"] (keep :key (tree-seq :children :children node))))
    (is (not-any? :lines (tree-seq :children :children node)))))

(deftest split-keeps-existing-keys
  (let [tree  (assoc (board "root" (text "a") (text "b") (text "c")) :key "k0")
        calls (chunks/split tree 2)]
    (is (= "k0" (get-in (ffirst calls) [:node :key])))))
