(ns penpot.mcp.tools.canvas-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.create :as create]
   [penpot.mcp.tools.layout :as layout]
   [penpot.mcp.tools.modify :as modify]
   [penpot.mcp.tools.text :as text]))

(def fid (str fx/file-id))
(def sid (str fx/rect-id))
(def shape-result {:id sid :name "Submit Button" :type "rectangle" :x 1 :y 2 :width 3 :height 4})

(defn- call [tools tool-name args]
  (let [ctx (fx/plugin-ctx shape-result)
        res (fx/call (fx/find-tool tools tool-name) ctx args)]
    {:result res :args (some-> (first @(:scripts ctx)) fx/script-args) :code (first @(:scripts ctx)) :ctx ctx}))

(deftest create-rect-passes-geometry-and-parent
  (let [{:keys [result args code]} (call create/tools "create_rect"
                                         {"file_id" fid "x" 10 "y" 20 "width" 30 "height" 40 "name" "Card"
                                          "parent_id" (str fx/board-id) "border_radius" 6})]
    (is (= {"fileId" fid "x" 10 "y" 20 "width" 30 "height" 40 "name" "Card" "parentId" (str fx/board-id)
            "borderRadius" 6}
           args))
    (is (str/includes? code "penpot.createRectangle()"))
    (is (= {"shape" {"id" sid "name" "Submit Button" "type" "rectangle" "x" 1 "y" 2 "width" 3 "height" 4}} result))))

(deftest create-tools-take-no-paint
  (doseq [[tool-name extra] [["create_board" {"width" 10 "height" 10}]
                             ["create_rect" {"width" 10 "height" 10}]
                             ["create_ellipse" {"width" 10 "height" 10}]
                             ["create_text" {"text" "Hello"}]
                             ["create_path" {"d" "M0 0 L10 10"}]]
          paint    [{"fills" [{"color" "#FF0000"}]} {"strokes" [{"color" "#000000"}]}]]
    (is (contains? (:result (call create/tools tool-name (merge {"file_id" fid "x" 0 "y" 0} extra paint))) :error)
        (str tool-name " " (ffirst paint)))))

(deftest create-tools-use-matching-factories
  (doseq [[tool-name factory extra] [["create_board" "penpot.createBoard()" {"width" 10 "height" 10}]
                                     ["create_ellipse" "penpot.createEllipse()" {"width" 10 "height" 10}]
                                     ["create_text" "penpot.createText(args.text)" {"text" "Hello"}]
                                     ["create_path" "penpot.createPath()" {"d" "M0 0 L10 10"}]]]
    (is (str/includes? (:code (call create/tools tool-name (merge {"file_id" fid "x" 0 "y" 0} extra))) factory) tool-name)))

(deftest gradient-fill-is-converted
  (let [{:keys [args]} (call modify/tools "set_fills"
                             {"file_id" fid "shape_id" sid
                              "fills" [{"gradient" {"type" "linear" "start_x" 0 "start_y" 0 "end_x" 1 "end_y" 1
                                                    "stops" [{"color" "#FF0000" "offset" 0} {"color" "#0000FF" "opacity" 0.5 "offset" 1}]}}]})]
    (is (= [{"fillColorGradient" {"type" "linear" "startX" 0 "startY" 0 "endX" 1 "endY" 1 "width" 1
                                  "stops" [{"color" "#FF0000" "opacity" 1 "offset" 0}
                                           {"color" "#0000FF" "opacity" 0.5 "offset" 1}]}}]
           (get args "fills")))))

(deftest rejects-invalid-color
  (is (contains? (:result (call modify/tools "set_fills" {"file_id" fid "shape_id" sid "fills" [{"color" "red"}]})) :error)))

(deftest group-and-component-take-shape-ids
  (let [{:keys [args code]} (call create/tools "create_group" {"file_id" fid "shape_ids" [sid (str fx/text-id)] "name" "G"})]
    (is (= [sid (str fx/text-id)] (get args "shapeIds")))
    (is (str/includes? code "penpot.group(")))
  (is (str/includes? (:code (call create/tools "create_component" {"file_id" fid "shape_ids" [sid]}))
                     "penpot.library.local.createComponent(")))

(deftest modify-tools-send-their-values
  (doseq [[tool-name extra expected snippet]
          [["set_position" {"x" 5 "y" 6} {"x" 5 "y" 6} "s.x = args.x"]
           ["resize" {"width" 50 "height" 60} {"width" 50 "height" 60} "s.resize(args.width, args.height)"]
           ["rotate" {"angle" 45} {"angle" 45} "s.rotate(args.angle)"]
           ["rename_shape" {"name" "New"} {"name" "New"} "s.name = args.name"]
           ["set_opacity" {"opacity" 0.3} {"opacity" 0.3} "s.opacity = args.opacity"]
           ["set_visible" {"visible" false} {"visible" false} "s.visible = args.visible"]
           ["set_blocked" {"blocked" true} {"blocked" true} "s.blocked = args.blocked"]
           ["set_parent_index" {"index" 2} {"index" 2} "s.setParentIndex(args.index)"]
           ["set_radius" {"top_left" 1 "bottom_right" 3} {"topLeft" 1 "bottomRight" 3} "borderRadiusTopLeft"]]]
    (let [{:keys [args code]} (call modify/tools tool-name (merge {"file_id" fid "shape_id" sid} extra))]
      (is (= (merge {"fileId" fid "shapeId" sid} expected) args) tool-name)
      (is (str/includes? code snippet) tool-name))))

(deftest rejects-non-positive-size
  (is (contains? (:result (call modify/tools "resize" {"file_id" fid "shape_id" sid "width" 0 "height" 10})) :error)))

(deftest move-to-parent-and-delete
  (is (= {"fileId" fid "shapeId" sid "parentId" (str fx/board-id) "index" 0}
         (:args (call modify/tools "move_to_parent" {"file_id" fid "shape_id" sid "parent_id" (str fx/board-id) "index" 0}))))
  (let [{:keys [args code]} (call modify/tools "delete_shapes" {"file_id" fid "shape_ids" [sid]})]
    (is (= [sid] (get args "shapeIds")))
    (is (str/includes? code ".remove()"))))

(deftest flex-layout-maps-options
  (let [{:keys [args code]} (call layout/tools "set_flex_layout"
                                  {"file_id" fid "board_id" (str fx/board-id) "dir" "column" "row_gap" 8
                                   "align_items" "center" "justify_content" "space-between" "wrap" "wrap"
                                   "padding" {"top" 1 "right" 2 "bottom" 3 "left" 4}
                                   "horizontal_sizing" "auto"})]
    (is (= {"fileId" fid "boardId" (str fx/board-id) "dir" "column" "rowGap" 8 "alignItems" "center"
            "justifyContent" "space-between" "wrap" "wrap"
            "padding" {"top" 1 "right" 2 "bottom" 3 "left" 4} "horizontalSizing" "auto"}
           args))
    (is (str/includes? code "addFlexLayout()"))))

(deftest grid-layout-takes-tracks
  (let [{:keys [args code]} (call layout/tools "set_grid_layout"
                                  {"file_id" fid "board_id" (str fx/board-id)
                                   "columns" [{"type" "flex" "value" 1} {"type" "fixed" "value" 120}]
                                   "rows" [{"type" "auto"}]})]
    (is (= [{"type" "flex" "value" 1} {"type" "fixed" "value" 120}] (get args "columns")))
    (is (str/includes? code "addGridLayout()"))))

(deftest remove-layout
  (is (str/includes? (:code (call layout/tools "remove_layout" {"file_id" fid "board_id" (str fx/board-id)})) ".remove()")))

(deftest text-content-and-style
  (is (= {"fileId" fid "shapeId" (str fx/text-id) "text" "Hi"}
         (:args (call text/tools "set_text_content" {"file_id" fid "shape_id" (str fx/text-id) "text" "Hi"}))))
  (let [{:keys [args]} (call text/tools "set_text_style" {"file_id" fid "shape_id" (str fx/text-id)
                                                          "font_family" "Inter" "font_size" 18 "font_weight" "700"
                                                          "align" "center" "grow_type" "auto-height"})]
    (is (= {"fontFamily" "Inter" "fontSize" "18" "fontWeight" "700" "align" "center" "growType" "auto-height"}
           (get args "style")))))

(deftest text-style-requires-a-value
  (is (contains? (:result (call text/tools "set_text_style" {"file_id" fid "shape_id" sid})) :error)))

(deftest create-rejects-page-and-parent-together
  (is (= {:error "Give page_id or parent_id, not both"}
         (:result (call create/tools "create_rect" {"file_id" fid "x" 0 "y" 0 "width" 1 "height" 1
                                                    "page_id" (str fx/page-id) "parent_id" (str fx/board-id)})))))

(deftest create-body-removes-shape-on-failure
  (is (str/includes? (:code (call create/tools "create_rect" {"file_id" fid "x" 0 "y" 0 "width" 1 "height" 1}))
                     "catch (e) { s.remove(); throw e; }")))

(deftest group-rejects-mixed-pages
  (is (str/includes? (:code (call create/tools "create_group" {"file_id" fid "shape_ids" [sid]})) "fail('mixed-pages'")))

(deftest delete-skips-already-removed-shapes
  (is (str/includes? (:code (call modify/tools "delete_shapes" {"file_id" fid "shape_ids" [sid]})) "if (!locateShape(id)) continue;")))

(deftest radius-requires-a-value
  (is (= {:error "Give radius or at least one corner"}
         (:result (call modify/tools "set_radius" {"file_id" fid "shape_id" sid})))))

(deftest text-style-none-clears-property
  (is (= {"textTransform" nil "textDecoration" nil}
         (get (:args (call text/tools "set_text_style" {"file_id" fid "shape_id" sid "text_transform" "none" "text_decoration" "none"}))
              "style"))))

(deftest rejects-huge-numbers
  (is (contains? (:result (call modify/tools "set_radius" {"file_id" fid "shape_id" sid "radius" 1e12})) :error)))

(deftest modify-marks-change-only-when-fingerprint-differs
  (let [code (:code (call modify/tools "set_opacity" {"file_id" fid "shape_id" sid "opacity" 0.5}))]
    (is (str/includes? code "const before = fingerprint(s);"))
    (is (str/includes? code "if (fingerprint(s) !== before) markChanged();"))))

(deftest unchanged-plugin-result-keeps-file-clean
  (let [dirty (atom #{})
        ctx   (assoc (fx/plugin-ctx shape-result) :persistence {:dirty dirty}
                     :execute (fn [_] {:result shape-result :changed false}))]
    (fx/call (fx/find-tool modify/tools "set_opacity") ctx {"file_id" fid "shape_id" sid "opacity" 0.5})
    (is (empty? @dirty))))

(deftest changed-plugin-result-marks-file-dirty
  (let [dirty (atom #{})
        ctx   (assoc (fx/plugin-ctx shape-result) :persistence {:dirty dirty})]
    (fx/call (fx/find-tool modify/tools "set_opacity") ctx {"file_id" fid "shape_id" sid "opacity" 0.5})
    (is (= #{fx/file-id} @dirty))))

(deftest fills-are-limited-like-penpot
  (is (contains? (:result (call modify/tools "set_fills" {"file_id" fid "shape_id" sid "fills" (vec (repeat 9 {"color" "#000000"}))})) :error))
  (is (not (contains? (:result (call modify/tools "set_fills" {"file_id" fid "shape_id" sid "fills" (vec (repeat 8 {"color" "#000000"}))})) :error))))

(deftest gradient-stops-are-limited-like-penpot
  (let [stops (vec (repeat 17 {"color" "#000000" "offset" 0}))]
    (is (contains? (:result (call modify/tools "set_fills" {"file_id" fid "shape_id" sid
                                                            "fills" [{"gradient" {"type" "linear" "start_x" 0 "start_y" 0 "end_x" 1 "end_y" 1 "stops" stops}}]}))
                   :error))))

(deftest edits-return-only-changes
  (doseq [[tools tool-name args] [[modify/tools "set_opacity" {"opacity" 0.5}]
                                  [modify/tools "rename_shape" {"name" "X"}]]]
    (is (str/includes? (:code (call tools tool-name (merge {"file_id" fid "shape_id" sid} args)))
                       "return changes(beforeInfo, s);")
        tool-name)))

(deftest prelude-leaves-out-defaults-and-diffs-states
  (let [code (:code (call modify/tools "set_opacity" {"file_id" fid "shape_id" sid "opacity" 0.5}))]
    (is (str/includes? code "const defaults = { rotation: 0, opacity: 1, visible: true, blocked: false };"))
    (is (str/includes? code "return { id, changed };"))))

(deftest text-is-created-with-typography-and-color-tokens-in-one-call
  (let [typo "88888888-0000-0000-0000-0000000000a1"
        color "88888888-0000-0000-0000-0000000000a2"
        {:keys [code args]} (call create/tools "create_text" {"file_id" fid "x" 0 "y" 0 "text" "Hi"
                                                              "typography_token_id" typo "color_token_id" color})]
    (is (= [typo color] [(get args "typographyTokenId") (get args "colorTokenId")]))
    (is (str/includes? code "bindToken(args.typographyTokenId, 'typography', 'typography');"))
    (is (str/includes? code "bindToken(args.colorTokenId, 'color', 'fill');"))))

(deftest shapes-can-be-created-out-of-the-layout-flow-in-one-call
  (let [{:keys [code args]} (call create/tools "create_rect" {"file_id" fid "x" 5 "y" 6 "width" 10 "height" 10
                                                              "parent_id" (str fx/board-id) "absolute" true
                                                              "constraint_horizontal" "right" "constraint_vertical" "top"})]
    (is (= [true "right" "top"] [(get args "absolute") (get args "constraintHorizontal") (get args "constraintVertical")]))
    (is (str/includes? code "(s.layoutChild ?? fail('not-in-layout', s.id)).absolute = true;"))
    (is (< (str/index-of code "parent.appendChild(s);") (str/index-of code ".absolute = true;")))))
