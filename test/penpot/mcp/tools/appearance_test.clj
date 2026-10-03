(ns penpot.mcp.tools.appearance-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.appearance :as appearance]))

(def fid (str fx/file-id))
(def sid (str fx/rect-id))

(defn- run [tool-name args]
  (let [ctx (fx/plugin-ctx {:shape {:id sid}})]
    {:ctx    ctx
     :result (fx/call (fx/find-tool appearance/tools tool-name) ctx (merge {"file_id" fid "shape_id" sid} args))
     :args   (when (seq @(:scripts ctx)) (fx/last-script-args ctx))
     :script (last @(:scripts ctx))}))

(deftest layout-child-sets-only-given-properties
  (let [{:keys [args script]} (run "set_layout_child" {"horizontal_sizing" "fill" "margin" {"top" 4 "right" 8 "bottom" 4 "left" 8}
                                                       "min_width" 40})]
    (is (= {"fileId" fid "shapeId" sid "horizontalSizing" "fill"
            "margin" {"top" 4 "right" 8 "bottom" 4 "left" 8} "minWidth" 40}
           args))
    (is (str/includes? script "fail('not-in-layout'"))))

(deftest layout-child-rejects-unknown-sizing-and-null-limits
  (is (contains? (:result (run "set_layout_child" {"horizontal_sizing" "stretch"})) :error))
  (is (contains? (:result (run "set_layout_child" {"min_width" nil})) :error)))

(deftest grid-cell-places-shape
  (let [{:keys [args script]} (run "set_grid_cell" {"row" 2 "column" 3 "row_span" 1 "column_span" 2})]
    (is (= {"fileId" fid "shapeId" sid "row" 2 "column" 3 "rowSpan" 1 "columnSpan" 2} args))
    (is (str/includes? script "fail('not-in-grid'"))))

(deftest grid-cell-rows-start-at-one
  (is (contains? (:result (run "set_grid_cell" {"row" 0 "column" 1})) :error)))

(deftest shadows-are-converted-to-plugin-format
  (let [{:keys [args]} (run "set_shadows" {"shadows" [{"style" "inner-shadow" "offset_x" 0 "offset_y" 2 "blur" 6 "spread" 1
                                                       "color" "#000000" "opacity" 0.3}]})]
    (is (= [{"style" "inner-shadow" "offsetX" 0 "offsetY" 2 "blur" 6 "spread" 1 "hidden" false
             "color" {"color" "#000000" "opacity" 0.3}}]
           (get args "shadows")))))

(deftest shadow-defaults
  (let [{:keys [args]} (run "set_shadows" {"shadows" [{"offset_y" 4 "blur" 8 "color" "#112233"}]})]
    (is (= [{"style" "drop-shadow" "offsetX" 0 "offsetY" 4 "blur" 8 "spread" 0 "hidden" false
             "color" {"color" "#112233" "opacity" 1}}]
           (get args "shadows")))))

(deftest empty-shadow-list-removes-shadows
  (is (= [] (get (:args (run "set_shadows" {"shadows" []})) "shadows"))))

(deftest blur-null-removes-and-absent-keeps
  (let [{:keys [args]} (run "set_blur" {"layer_blur" nil})]
    (is (= {"fileId" fid "shapeId" sid "layerBlur" nil} args)))
  (let [{:keys [args]} (run "set_blur" {"background_blur" 12})]
    (is (= {"fileId" fid "shapeId" sid "backgroundBlur" 12} args))))

(deftest blend-mode-follows-penpot-list
  (is (= "multiply" (get (:args (run "set_blend_mode" {"mode" "multiply"})) "mode")))
  (is (contains? (:result (run "set_blend_mode" {"mode" "dissolve"})) :error)))

(deftest constraints-and-proportion-lock
  (is (= {"fileId" fid "shapeId" sid "horizontal" "leftright"}
         (:args (run "set_constraints" {"horizontal" "leftright"}))))
  (is (contains? (:result (run "set_constraints" {"horizontal" "top"})) :error))
  (is (= {"fileId" fid "shapeId" sid "locked" true} (:args (run "set_proportion_lock" {"locked" true})))))

(deftest flip-sets-state-instead-of-toggling
  (let [{:keys [args script]} (run "set_flip" {"horizontal" true})]
    (is (= {"fileId" fid "shapeId" sid "horizontal" true} args))
    (is (str/includes? script "s.flipX !== args.horizontal"))))

(deftest duplicate-returns-the-copy
  (let [{:keys [args script]} (run "duplicate_shape" {})]
    (is (= {"fileId" fid "shapeId" sid} args))
    (is (str/includes? script "s.clone()"))))

(deftest shadows-and-blur-skip-writes-that-change-nothing
  (is (str/includes? (:script (run "set_shadows" {"shadows" []})) "if (JSON.stringify(state()) !== JSON.stringify(args.shadows)) s.shadows = args.shadows;"))
  (is (= "#aabbcc" (get-in (:args (run "set_shadows" {"shadows" [{"color" "#AABBCC"}]})) ["shadows" 0 "color" "color"])))
  (is (str/includes? (:script (run "set_blur" {"layer_blur" 3})) "if ('layerBlur' in args && state().layerBlur !== args.layerBlur)")))
