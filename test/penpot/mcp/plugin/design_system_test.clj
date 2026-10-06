(ns penpot.mcp.plugin.design-system-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.plugin.design-system :as design-system]
   [penpot.mcp.replay :as replay]))

(def ^:private snapshot
  (delay (:result (first (replay/editor-answers "design-system/css")))))

(deftest the-editor-snapshot-becomes-a-catalog
  (let [c (design-system/catalog @snapshot)]
    (is (= 9 (count (:sets c))))
    (is (= 6 (count (:themes c))))
    (is (= "Design test data" (:file-name c)))
    (is (= 1 (count (:colors c))))
    (is (every? keyword? (map :type (mapcat :tokens (:sets c)))))))

(deftest one-script-reads-tokens-colors-and-typographies-together
  (is (= 1 (count (filter #(.contains ^String % design-system/collect-body) (replay/editor-scripts "design-system/css"))))))

(deftest a-closed-editor-gives-nothing-and-reads-nothing
  (let [ctx (replay/context "design-system/closed-editor")]
    (is (nil? (design-system/collect ctx (parse-uuid (get (:args (replay/recording "design-system/closed-editor")) "file_id")))))))

(deftest an-unexpected-shape-is-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (#'design-system/validated (assoc (design-system/catalog @snapshot) :sets "not a list")))))
