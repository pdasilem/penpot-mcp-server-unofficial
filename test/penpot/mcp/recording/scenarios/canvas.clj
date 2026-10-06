(ns penpot.mcp.recording.scenarios.canvas
  (:require
   [penpot.mcp.recording.data :as d]))

(defn- own? [s] (and (not (:shape-ref s)) (not (:hidden s))))

(defn- plain-board [f _] (d/fresh-shape f #(and (own? %) (= :frame (:type %)) (not (:layout %)) (not (d/in-component? f %))) "own boards without layout outside components"))

(defn- top-board [f] (d/nth-shape f #(and (own? %) (= :frame (:type %)) (not (:layout %)) (seq (:shapes %))) "own boards without layout and with children" 0))

(defn- layout-board [f _] (d/fresh-shape f #(and (own? %) (= :frame (:type %)) (= :flex (:layout %))) "own flex boards"))

(defn- grid-board [f _] (d/fresh-shape f #(and (own? %) (= :grid (:layout %))) "own grid boards"))

(defn- rect [f _] (d/fresh-shape f #(and (own? %) (= :rect (:type %))) "own rectangles"))

(defn- free-rect [f _] (d/fresh-shape f #(and (own? %) (= :rect (:type %)) (let [p (d/parent f %)] (and (= :frame (:type p)) (not (:layout p))))) "own rectangles directly in a board without layout"))

(defn- text [f _] (d/fresh-shape f #(and (own? %) (= :text (:type %))) "own texts"))

(defn- base [f] {"file_id" (:fid f)})

(defn- at [f shape & kvs] (merge (base f) {"shape_id" (str (:id shape))} (apply hash-map kvs)))

(defn- into-board [f board & kvs]
  (merge (base f) {"parent_id" (str (:id board)) "x" 10 "y" 10} (apply hash-map kvs)))

(defn- s [name tool args] {:name (str "canvas/" name) :tool tool :file :scratch :editor true :args args})

(def scenarios
  [(s "create-rect" "create_rect" #(into-board % (top-board %) "width" 120 "height" 40 "name" "Created rect" "border_radius" 6))
   (s "create-ellipse" "create_ellipse" #(into-board % (top-board %) "width" 50 "height" 50))
   (s "create-board" "create_board" #(into-board % (top-board %) "width" 200 "height" 100 "clip_content" false))
   (s "create-text-tokens" "create_text" #(into-board % (top-board %) "text" "Created text"
                                                    "typography_token_id" (str (:id (d/token % :typography)))
                                                    "color_token_id" (str (:id (d/token % :color)))))
   (s "create-path" "create_path" #(into-board % (top-board %) "d" "M0 0 L40 40 L80 0 Z"))
   (s "create-absolute-in-layout" "create_rect" #(into-board % (layout-board % 0) "width" 30 "height" 30 "absolute" true))
   (s "create-page-and-parent" "create_rect" #(merge (into-board % (top-board %) "width" 10 "height" 10) {"page_id" (str (:id (d/page-of % (top-board %))))}))
   (s "group" "create_group" #(merge (base %) {"shape_ids" [(str (:id (rect % 0))) (str (:id (rect % 1)))] "name" "Created group"}))
   (s "group-across-pages" "create_group" #(merge (base %) {"shape_ids" [(str (:id (d/shape % "Model" (fn [x] (= :text (:type x))) "text"))) (str (:id (d/shape % "Icons" (fn [x] (= :path (:type x))) "path")))]}))
   (s "component" "create_component" #(merge (base %) {"shape_ids" [(str (:id (d/nth-shape % (fn [x] (and (own? x) (= :rect (:type x)) (not (d/in-component? % x)))) "rectangles outside components" 0)))] "name" "Recorded / Component"}))
   (s "set-position" "set_position" #(at % (free-rect % 0) "x" 15 "y" 25))
   (s "resize" "resize" #(at % (free-rect % 1) "width" 77 "height" 33))
   (s "rotate" "rotate" #(at % (free-rect % 2) "angle" 30))
   (s "rename" "rename_shape" #(at % (rect % 6) "name" "Renamed rect"))
   (s "set-fills-gradient" "set_fills" #(at % (rect % 7) "fills" [{"gradient" {"type" "linear" "start_x" 0 "start_y" 0 "end_x" 1 "end_y" 1
                                                                          "stops" [{"color" "#112233" "opacity" 1 "offset" 0} {"color" "#445566" "opacity" 1 "offset" 1}]}}]))
   (s "set-strokes" "set_strokes" #(at % (rect % 8) "strokes" [{"color" "#223344" "opacity" 1 "width" 2 "style" "solid" "alignment" "inner"}]))
   (s "set-opacity" "set_opacity" #(at % (rect % 9) "opacity" 0.5))
   (s "set-opacity-same" "set_opacity" #(let [r (rect % 10)] (at % r "opacity" (or (:opacity r) 1))))
   (s "set-radius" "set_radius" #(at % (rect % 11) "radius" 9))
   (s "set-visible" "set_visible" #(at % (rect % 12) "visible" false))
   (s "set-blocked" "set_blocked" #(at % (rect % 13) "blocked" true))
   (s "set-parent-index" "set_parent_index" #(at % (rect % 14) "index" 0))
   (s "move-to-parent" "move_to_parent" #(merge (at % (rect % 15)) {"parent_id" (str (:id (plain-board % 0)))}))
   (s "move-to-parent-same-page" "move_to_parent" #(let [r (rect % 18)
                                                         p (:id (d/page-of % r))
                                                         b (d/nth-shape % (fn [x] (and (own? x) (= :frame (:type x)) (not (:layout x)) (not (d/in-component? % x)) (not= (:id x) (:parent-id r)) (= p (:id (d/page-of % x))))) "boards on the page of the rectangle" 0)]
                                                     (merge (at % r) {"parent_id" (str (:id b))})))
   (s "delete" "delete_shapes" #(merge (base %) {"shape_ids" [(str (:id (rect % 16))) (str (:id (rect % 17)))]}))
   (s "delete-absent" "delete_shapes" #(merge (base %) {"shape_ids" [(:absent-shape-id %)]}))
   (s "flex-layout" "set_flex_layout" #(merge (base %) {"board_id" (str (:id (plain-board % 1))) "dir" "column" "row_gap" 8 "padding" {"top" 4 "left" 6} "align_items" "center"}))
   (s "grid-layout" "set_grid_layout" #(merge (base %) {"board_id" (str (:id (plain-board % 2))) "rows" [{"type" "flex" "value" 1} {"type" "fixed" "value" 40}] "columns" [{"type" "auto"}]}))
   (s "grid-tracks-in-place" "set_grid_layout" #(merge (base %) {"board_id" (str (:id (grid-board % 0))) "columns" [{"type" "flex" "value" 1}]}))
   (s "remove-layout" "remove_layout" #(merge (base %) {"board_id" (str (:id (layout-board % 1)))}))
   (s "text-content" "set_text_content" #(at % (text % 0) "text" "Recorded text"))
   (s "text-style" "set_text_style" #(at % (text % 1) "font_size" 19 "font_weight" "700" "text_transform" "uppercase"))
   (s "text-style-none" "set_text_style" #(at % (text % 2) "text_transform" "none"))])
