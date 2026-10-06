(ns penpot.mcp.recording.scenarios.export
  (:require
   [penpot.mcp.recording.data :as d]))

(defn- big-board [f] (d/shape f "Elements" #(and (= :frame (:type %)) (< 20 (count (:shapes %)))) "board with more than 20 children"))

(defn- image-fill [f] (d/any-shape f #(some :fill-image (:fills %)) "shape with an image fill"))

(defn- variant-icon [f]
  (let [variants (set (map :id (filter :variant-id (d/components f))))]
    (d/shape f "Icons" #(and (:main-instance %) (variants (:component-id %))) "main instance of a variant icon")))

(defn- export-args [f shape & kvs]
  (merge {"file_id" (:fid f) "shape_id" (str (:id shape))} (apply hash-map kvs)))

(def scenarios
  [{:name "export/png-board" :tool "export_shape" :editor true :args #(export-args % (big-board %))}
   {:name "export/png-small" :tool "export_shape" :editor true :args #(export-args % (big-board %) "max_size" 300)}
   {:name "export/svg-board" :tool "export_shape" :editor true :args #(export-args % (big-board %) "format" "svg")}
   {:name "export/svg-icon" :tool "export_shape" :editor true :args #(export-args % (variant-icon %) "format" "svg")}
   {:name "export/fill-image" :tool "export_shape" :editor true :args #(export-args % (image-fill %) "mode" "fill")}
   {:name "export/fill-svg" :tool "export_shape" :editor true :args #(export-args % (image-fill %) "mode" "fill" "format" "svg")}])
