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

(deftest a-property-can-be-renamed-to-digits-only
  (let [variants (get (e/run "edits/rename-variant-property-numeric") "variants")]
    (is (some #{"1"} (get variants "properties")))
    (is (not-any? #{(get (e/args "edits/rename-variant-property-numeric") "property")} (get variants "properties")))))

(deftest a-set-with-a-digits-only-property-is-refused-instead-of-edited-at-a-guessed-position
  (is (re-find #"^Penpot does not report the order of variant properties while one is named with digits only \(1\)"
               (:error (e/run "edits/remove-variant-property-order-unknown")))))
