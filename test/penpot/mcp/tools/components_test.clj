(ns penpot.mcp.tools.components-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.tools.edit-support :as e]))

(deftest an-instance-is-placed-into-the-given-board
  (let [shape (get (e/run "edits/instance") "shape")]
    (is (= (get (e/args "edits/instance") "parent_id") (get shape "parentId")))
    (is (some? (get shape "id")))))

(deftest a-copy-switches-to-another-variant
  (let [shape (get (e/run "edits/switch-variant") "shape")]
    (is (some? (get shape "id")))))

(deftest a-copy-swaps-to-another-component-and-keeps-its-place
  (is (some? (get-in (e/run "edits/swap-component") ["shape" "id"]))))

(deftest detaching-and-resetting-return-the-shape
  (is (some? (get-in (e/run "edits/detach") ["shape" "id"])))
  (is (some? (get-in (e/run "edits/reset-overrides") ["shape" "id"]))))

(deftest a-variant-property-value-is-set
  (let [variants (get (e/run "edits/set-variant-property") "variants")
        prop     (get (e/args "edits/set-variant-property") "property")]
    (is (some #{prop} (get variants "properties")))))

(deftest a-variant-property-is-renamed
  (is (nil? (:error (e/run "edits/rename-variant-property")))))
