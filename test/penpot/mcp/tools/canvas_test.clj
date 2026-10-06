(ns penpot.mcp.tools.canvas-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools :as all]))

(def ^:private tools (into {} (map (juxt :name identity)) all/all))

(defn- replayed [scenario]
  (let [r (replay/run (tools (:tool (replay/recording scenario))) scenario)]
    (is (empty? (:left r)) (str scenario " left recorded requests unused"))
    r))

(defn- run [scenario]
  (replay/data (replayed scenario)))

(defn- args [scenario]
  (:args (replay/recording scenario)))

(defn- changed [scenario]
  (get-in (run scenario) ["shape" "changed"]))

(defn- refused [tool-name scenario changes]
  (:error (replay/data (replay/run-without-penpot (tools tool-name) (merge (args scenario) changes)))))

(deftest created-shapes-take-their-parent-and-geometry
  (doseq [[scenario type] [["canvas/create-rect" "rectangle"] ["canvas/create-ellipse" "ellipse"] ["canvas/create-board" "board"]]]
    (let [shape (get (run scenario) "shape")
          a     (args scenario)]
      (is (= type (get shape "type")) scenario)
      (is (= (get a "parent_id") (get shape "parentId")) scenario)
      (is (= [(get a "width") (get a "height")] [(get shape "width") (get shape "height")]) scenario))))

(deftest a-path-is-created-from-its-d
  (let [shape (get (run "canvas/create-path") "shape")]
    (is (= "path" (get shape "type")))
    (is (= [80 40] [(get shape "width") (get shape "height")]))))

(deftest text-is-created-with-typography-and-color-tokens-in-one-call
  (let [shape (get (run "canvas/create-text-tokens") "shape")]
    (is (= "text" (get shape "type")))
    (is (= #{"typography" "fill"} (set (keys (get shape "tokens")))))))

(deftest a-shape-can-be-created-out-of-the-layout-flow
  (let [shape (get (run "canvas/create-absolute-in-layout") "shape")]
    (is (= (get (args "canvas/create-absolute-in-layout") "parent_id") (get shape "parentId")))))

(deftest groups-and-components-take-shape-ids
  (is (= "group" (get-in (run "canvas/group") ["shape" "type"])))
  (let [component (run "canvas/component")]
    (is (= ["Recorded" "Component"] [(get component "path") (get component "name")]))
    (is (some? (get component "componentId")))))

(deftest shapes-on-different-pages-cannot-be-grouped
  (is (re-find #"same page" (:error (run "canvas/group-across-pages")))))

(deftest edits-return-only-what-changed
  (is (= {"x" 15 "y" 25} (changed "canvas/set-position")))
  (is (= {"width" 77 "height" 33} (select-keys (changed "canvas/resize") ["width" "height"])))
  (is (= 30 (get (changed "canvas/rotate") "rotation")))
  (is (= {"name" "Renamed rect"} (changed "canvas/rename")))
  (is (= {"opacity" 0.5} (changed "canvas/set-opacity")))
  (is (= {"visible" false} (changed "canvas/set-visible")))
  (is (= {"parentIndex" 0} (changed "canvas/set-parent-index")))
  (is (= "linear" (get-in (changed "canvas/set-fills-gradient") ["fills" 0 "fillColorGradient" "type"])))
  (is (= "#223344" (get-in (changed "canvas/set-strokes") ["strokes" 0 "strokeColor"]))))

(deftest a-new-radius-is-reported-as-a-change
  (is (seq (changed "canvas/set-radius"))))

(deftest setting-the-same-opacity-changes-nothing
  (is (= {} (changed "canvas/set-opacity-same"))))

(deftest an-edit-marks-the-file-unsaved
  (let [{:keys [ctx]} (replayed "canvas/set-opacity")]
    (is (contains? @(get-in ctx [:persistence :dirty]) (parse-uuid (get (args "canvas/set-opacity") "file_id"))))))

(deftest a-shape-moves-to-a-board-on-its-page
  (is (= (get (args "canvas/move-to-parent-same-page") "parent_id") (get (changed "canvas/move-to-parent-same-page") "parentId"))))

(deftest a-move-to-a-board-on-another-page-is-explained
  (is (re-find #"(?i)another page" (:error (run "canvas/move-to-parent")))))

(deftest deleting-reports-what-was-deleted
  (is (= (set (get (args "canvas/delete") "shape_ids")) (set (get (run "canvas/delete") "deleted"))))
  (is (= [] (get (run "canvas/delete-absent") "deleted"))))

(deftest layouts-are-set-and-removed
  (is (= {"type" "flex" "dir" "column" "rowGap" 8 "alignItems" "center" "padding" [4 0 0 6]}
         (select-keys (get (run "canvas/flex-layout") "layout") ["type" "dir" "rowGap" "alignItems" "padding"])))
  (is (= [{"type" "flex" "value" 1} {"type" "fixed" "value" 40}] (get-in (run "canvas/grid-layout") ["layout" "rows"])))
  (is (= (get (args "canvas/remove-layout") "board_id") (get (run "canvas/remove-layout") "id"))))

(deftest grid-tracks-that-still-hold-shapes-are-not-dropped
  (is (re-find #"still holds shapes" (:error (run "canvas/grid-tracks-in-place")))))

(deftest text-content-and-style-change
  (is (= "Recorded text" (get (changed "canvas/text-content") "characters")))
  (is (= {"fontSize" "19" "fontWeight" "700" "textTransform" "uppercase"}
         (select-keys (changed "canvas/text-style") ["fontSize" "fontWeight" "textTransform"]))))

(deftest a-text-transform-can-be-cleared
  (is (nil? (:error (run "canvas/text-style-none")))))

(deftest arguments-are-checked-before-asking-penpot
  (is (= "Give page_id or parent_id, not both" (:error (run "canvas/create-page-and-parent"))))
  (is (refused "resize" "canvas/resize" {"width" 0}))
  (is (refused "resize" "canvas/resize" {"width" 1e12}))
  (is (refused "set_radius" "canvas/set-radius" {"radius" nil}))
  (is (refused "set_text_style" "canvas/text-style" {"font_size" nil "font_weight" nil "text_transform" nil}))
  (is (refused "set_fills" "canvas/set-fills-gradient" {"fills" (vec (repeat 1000 {"color" "#112233"}))})))
