(ns penpot.mcp.tools.appearance-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.tools.edit-support :as e]))

(defn- state [scenario]
  (get (e/run scenario) "state"))

(deftest appearance-settings-return-the-resulting-state
  (is (= "multiply" (state "edits/blend-mode")))
  (is (= {"layerBlur" 4} (state "edits/blur")))
  (is (= {"horizontal" "right" "vertical" "bottom"} (state "edits/constraints")))
  (is (= {"horizontal" true "vertical" false} (state "edits/flip")))
  (is (true? (state "edits/proportion-lock")))
  (is (= [{"style" "drop-shadow" "offsetX" 0 "offsetY" 2 "blur" 4 "spread" 0 "hidden" false}]
         (map #(dissoc % "color" "id") (state "edits/shadows")))))

(deftest a-grid-child-takes-its-cell
  (is (= {"row" 1 "column" 1} (select-keys (state "edits/grid-cell") ["row" "column"]))))

(deftest a-layout-child-takes-its-sizing-and-margins
  (let [s (state "edits/layout-child")]
    (is (= "fill" (get s "horizontalSizing")))
    (is (= {"top" 4 "right" 0 "bottom" 4 "left" 0} (get s "margin")))))

(deftest a-duplicate-is-a-new-shape
  (is (not= (get (e/args "edits/duplicate") "shape_id") (get-in (e/run "edits/duplicate") ["shape" "id"]))))
