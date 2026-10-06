(ns penpot.mcp.tools.structure-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.tools.edit-support :as e]))

(deftest shapes-are-aligned-and-distributed
  (let [aligned (get (e/run "edits/align") "shapes")]
    (is (= (count (get (e/args "edits/align") "shape_ids")) (count aligned)))
    (is (= 1 (count (set (map #(get % "x") aligned))))))
  (is (= (count (get (e/args "edits/distribute") "shape_ids")) (count (get (e/run "edits/distribute") "shapes")))))

(deftest shapes-on-different-pages-are-refused
  (is (re-find #"same page" (:error (e/run "edits/align-across-pages"))))
  (is (re-find #"same page" (:error (e/run "edits/distribute-across-pages")))))

(deftest a-boolean-shape-is-created-in-place-of-its-sources
  (let [shape (get (e/run "edits/boolean") "shape")]
    (is (= "boolean" (get shape "type")))
    (is (= "Union" (get shape "name")))
    (is (seq (get shape "id")))
    (is (seq (get shape "parentId")))))

(deftest boards-are-not-combined-into-a-boolean
  (let [boards (get (e/args "edits/boolean-of-boards") "shape_ids")]
    (is (= (str "Boards cannot be combined into a boolean shape: " (first boards) ", " (second boards))
           (:error (e/run "edits/boolean-of-boards"))))))

(deftest flatten-mask-and-ungroup
  (is (seq (get (e/run "edits/flatten") "shapes")))
  (is (true? (get-in (e/run "edits/mask") ["shape" "isMask"])))
  (is (seq (get (e/run "edits/ungroup") "shapeIds"))))

(deftest an-icon-exported-by-penpot-is-imported-into-the-board-at-its-size
  (let [svg   (get (e/args "edits/import-svg") "svg")
        shape (get (e/run "edits/import-svg") "shape")]
    (is (= (get (e/args "edits/import-svg") "parent_id") (get shape "parentId")))
    (is (= [(parse-long (second (re-find #"width=\"(\d+)\"" svg))) (parse-long (second (re-find #"height=\"(\d+)\"" svg)))]
           [(get shape "width") (get shape "height")]))))
