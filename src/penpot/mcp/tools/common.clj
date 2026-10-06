(ns penpot.mcp.tools.common
  (:require
   [app.common.uuid :as uuid]
   [app.common.types.fills.impl :as fills-impl]
   [penpot.mcp.transform.geometry :as geometry]))

(def plugin-type
  {:frame "board"
   :rect "rectangle"
   :circle "ellipse"
   :text "text"
   :path "path"
   :group "group"
   :bool "boolean"
   :image "image"
   :svg-raw "svg-raw"})

(def plugin-types
  (into [:enum] (sort (vals plugin-type))))

(def safe-number
  [:and number? [:fn {:error/message "should be between -2147483648 and 2147483647"}
                 #(<= -2147483648 % 2147483647)]])

(def short-text
  [:string {:min 1 :max 250}])

(def shape-id-param
  [:shape_id {:description "Shape id"} :uuid])

(def file-id-param
  [:file_id {:description "Penpot file id"} :uuid])

(def page-id-param
  [:page_id {:optional true :description "Page id; defaults to the first page of the file"} :uuid])

(def shape-page-param
  [:page_id {:optional true :description "Page the shape is on; required when the file is not open in the Penpot editor"} :uuid])

(defn shape-type [shape]
  (get plugin-type (:type shape) (some-> (:type shape) name)))

(defn root? [shape]
  (= uuid/zero (:id shape)))

(defn brief [shape]
  {:id (:id shape)
   :name (:name shape)
   :type (shape-type shape)
   :parent_id (:parent-id shape)
   :x (geometry/x shape)
   :y (geometry/y shape)
   :width (geometry/width shape)
   :height (geometry/height shape)})

(defn page-shapes [page]
  (remove root? (vals (:objects page))))

(def hex-color
  [:re {:error/message "should be a #RRGGBB color"} #"^#[0-9a-fA-F]{6}$"])

(def unit-interval
  [:and number? [:>= 0] [:<= 1]])

(def positive-size
  [:and safe-number [:> 0]])

(def non-negative
  [:and safe-number [:>= 0]])

(def gradient
  [:map {:closed true}
   [:type {:description "Gradient kind"} [:enum "linear" "radial"]]
   [:start_x {:description "Start X relative to the shape, 0..1"} unit-interval]
   [:start_y {:description "Start Y relative to the shape, 0..1"} unit-interval]
   [:end_x {:description "End X relative to the shape, 0..1"} unit-interval]
   [:end_y {:description "End Y relative to the shape, 0..1"} unit-interval]
   [:stops [:vector {:min 1 :max fills-impl/MAX-GRADIENT-STOPS}
            [:map {:closed true}
             [:color {:description "Stop color #RRGGBB"} hex-color]
             [:opacity {:optional true :description "0..1, default 1"} unit-interval]
             [:offset {:description "Position along the gradient, 0..1"} unit-interval]]]]])

(def fill
  [:and
   [:map {:closed true}
    [:color {:optional true :description "Solid color #RRGGBB"} hex-color]
    [:opacity {:optional true :description "0..1, default 1"} unit-interval]
    [:gradient {:optional true :description "Linear or radial gradient; coordinates are relative to the shape (0..1)"} gradient]]
   [:fn {:error/message "should have either color or gradient"} #(not= (contains? % :color) (contains? % :gradient))]])

(def stroke
  [:map {:closed true}
   [:color {:description "Stroke color #RRGGBB"} hex-color]
   [:opacity {:optional true :description "0..1, default 1"} unit-interval]
   [:width {:optional true :description "Width in pixels, default 1"} non-negative]
   [:style {:optional true :description "Line style, default solid"} [:enum "solid" "dotted" "dashed" "mixed"]]
   [:alignment {:optional true :description "Position relative to the shape edge, default center"} [:enum "center" "inner" "outer"]]])

(def fills
  [:vector {:max fills-impl/MAX-FILLS} fill])

(defn ->plugin-fill [{:keys [color opacity gradient]}]
  (if gradient
    {:fill-color-gradient {:type (:type gradient)
                           :start-x (:start_x gradient) :start-y (:start_y gradient)
                           :end-x (:end_x gradient) :end-y (:end_y gradient)
                           :width 1
                           :stops (mapv (fn [s] {:color (:color s) :opacity (or (:opacity s) 1) :offset (:offset s)})
                                        (:stops gradient))}}
    {:fill-color color :fill-opacity (or opacity 1)}))

(defn ->plugin-stroke [{:keys [color opacity width style alignment]}]
  {:stroke-color color
   :stroke-opacity (or opacity 1)
   :stroke-width (or width 1)
   :stroke-style (or style "solid")
   :stroke-alignment (or alignment "center")})

(def default-page-size 100)

(def page-params
  [[:limit {:optional true :description "Maximum number of items to return, default 100"} [:int {:min 1 :max 500}]]
   [:cursor {:optional true :description "next_cursor from the previous call, to get the next items"} [:re #"^[0-9]{1,9}$"]]])

(defn paged [k items {:keys [limit cursor]}]
  (let [offset (if cursor (parse-long cursor) 0)
        limit  (or limit default-page-size)
        items  (vec items)]
    (cond-> {k (subvec items (min offset (count items)) (min (+ offset limit) (count items)))}
      (> (count items) (+ offset limit)) (assoc :next_cursor (str (+ offset limit))))))

(defn compact [m]
  (into {} (remove (comp nil? val)) m))
