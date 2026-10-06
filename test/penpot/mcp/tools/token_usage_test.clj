(ns penpot.mcp.tools.token-usage-test
  (:require
   [penpot.mcp.penpot.tokens-lib :as ctob]
   [app.common.uuid :as uuid]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools :as tools]
   [penpot.mcp.tools.token-usage :as token-usage]))

(def ^:private tool (first token-usage/tools))

(defn- run [scenario]
  (let [replayed (replay/run tool scenario)]
    (is (empty? (:left replayed)) (str scenario " left recorded requests unused"))
    (replay/data replayed)))

(defn- args [scenario]
  (:args (replay/recording scenario)))

(def ^:private file
  (delay (first (replay/penpot-answers "token-usage/saved" :get-file))))

(defn- shapes []
  (for [p (vals (get-in @file [:data :pages-index]))
        s (vals (:objects p))
        :when (not= uuid/zero (:id s))]
    (assoc s :page p)))

(defn- token-names []
  (let [lib (get-in @file [:data :tokens-lib])]
    (set (for [s (ctob/get-sets lib) t (vals (ctob/get-tokens lib (ctob/get-id s)))] (:name t)))))

(defn- applied-names []
  (set (mapcat (comp vals :applied-tokens) (shapes))))

(deftest token-usage-is-a-read-tool
  (is (= "read" (:toolset (first (filter #(= "token_usage" (:name %)) tools/all))))))

(deftest the-summary-counts-the-file
  (let [summary (get (run "token-usage/saved") "summary")]
    (is (= (count (token-names)) (get summary "tokens")))
    (is (= (count (filter (token-names) (applied-names))) (get summary "applied")))
    (is (= (count (remove (token-names) (applied-names))) (get summary "missing")))
    (is (= (count (shapes)) (get summary "shapes_checked")))
    (is (true? (get summary "values_compared")))))

(deftest every-used-token-counts-the-shapes-that-apply-it
  (doseq [{:strs [name copies pages] shape-count "shapes"} (get (run "token-usage/saved") "usage")]
    (let [applying (filter #(some #{name} (vals (:applied-tokens %))) (shapes))]
      (is (= (count applying) shape-count) name)
      (is (= (count (filter :shape-ref applying)) copies) name)
      (is (= (set (map (comp :name :page) applying)) (set (map #(get % "name") pages))) name))))

(deftest unused-tokens-are-applied-to-no-shape
  (let [unused (map #(get % "name") (get (run "token-usage/saved") "unused"))]
    (is (seq unused))
    (is (not-any? (applied-names) unused))))

(deftest raw-values-are-attributes-not-bound-to-a-token
  (let [by-id (into {} (map (juxt (comp str :id) identity)) (shapes))]
    (doseq [frame (get (run "token-usage/saved") "raw_values")
            {:strs [shape_id values]} (get frame "shapes")]
      (is (not (:shape-ref (by-id shape_id))) "copies are not checked")
      (is (seq values) shape_id))))

(deftest raw-values-page-with-limit-and-cursor
  (let [result (run "token-usage/editor-paged")]
    (is (= "7" (get (args "token-usage/editor-paged") "cursor")))
    (is (= 7 (count (mapcat #(get % "values") (mapcat #(get % "shapes") (get result "raw_values"))))))
    (is (= "14" (get result "next_cursor")))))

(deftest page-id-narrows-raw-values-to-that-page
  (let [page (get (args "token-usage/saved-page") "page_id")
        result (run "token-usage/saved-page")]
    (is (every? #(= page (get % "page_id")) (get result "raw_values")))
    (is (= (get-in (run "token-usage/saved") ["summary" "applied"]) (get-in result ["summary" "applied"])))))

(deftest an-unknown-page-is-an-error
  (is (re-find #"not found" (:error (run "token-usage/saved-absent-page")))))

(deftest sections-limit-the-answer
  (is (= #{"summary" "unused" "missing"} (set (keys (run "token-usage/editor-sections")))))
  (let [result (run "token-usage/editor-usage-only")]
    (is (= #{"summary" "usage"} (set (keys result))))
    (is (not (contains? (get result "summary") "values_compared")))))

(deftest without-the-editor-the-file-is-read-once
  (is (= [:get-file-stats :get-file] (replay/requests "token-usage/saved"))))

(deftest the-editor-gives-the-same-audit
  (is (= (run "token-usage/saved") (run "token-usage/editor"))))
