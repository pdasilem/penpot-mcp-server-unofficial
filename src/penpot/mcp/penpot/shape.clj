(ns penpot.mcp.penpot.shape
  (:require
   [penpot.mcp.penpot.types :as types]
   [penpot.mcp.penpot.uuid :as uuid]))

(def ^:private default-color "#B1B2B5")

(def ^:private minimal
  {:rect {:type :rect :name "Rectangle" :fills [{:fill-color default-color :fill-opacity 1}] :strokes [] :r1 0 :r2 0 :r3 0 :r4 0}
   :frame {:frame-id uuid/zero :fills [{:fill-color "#FFFFFF" :fill-opacity 1}] :strokes [] :name "Board" :shapes []
           :r1 0 :r2 0 :r3 0 :r4 0 :hide-fill-on-export false}
   :circle {:type :circle :name "Ellipse" :fills [{:fill-color default-color :fill-opacity 1}] :strokes []}
   :group {:type :group :name "Group" :fills [] :strokes [] :shapes []}
   :text {:type :text :name "Text"}})

(def identity-matrix (types/->Matrix 1.0 0.0 0.0 1.0 0.0 0.0))

(defn- without-nils [m]
  (into {} (remove (comp nil? val)) m))

(defn- rect [x y w h]
  (if (every? number? [x y w h])
    (let [w (max (double w) 0.01) h (max (double h) 0.01) x (double x) y (double y)]
      (types/->Rect x y w h x y (+ x w) (+ y h)))
    (types/->Rect 0.0 0.0 0.01 0.01 0.0 0.0 0.01 0.01)))

(defn- points [{:keys [x y width height]}]
  [(types/->Point x y) (types/->Point (+ x width) y) (types/->Point (+ x width) (+ y height)) (types/->Point x (+ y height))])

(defn- proportions [{:keys [type fills selrect] :as shape}]
  (cond
    (= :text type) shape
    (and (seq fills) (every? #(some? (:fill-image %)) fills))
    (assoc shape :proportion (float (/ (:width selrect) (:height selrect))) :proportion-lock true)
    :else (assoc shape :proportion 1.0 :proportion-lock false)))

(defn setup-shape [{:keys [type] :as props}]
  (let [base    (merge (get minimal type) {:x 0 :y 0 :width 0.01 :height 0.01}
                       {:id (uuid/next) :frame-id uuid/zero :parent-id uuid/zero :rotation 0})
        shape   (merge (types/map->Shape base) (without-nils (into {} props)))
        selrect (or (:selrect shape) (rect (:x shape) (:y shape) (:width shape) (:height shape)))
        shape   (assoc shape :selrect selrect :points (or (:points shape) (points selrect)))]
    (proportions
     (cond-> shape
       (nil? (:transform shape)) (assoc :transform identity-matrix)
       (nil? (:transform-inverse shape)) (assoc :transform-inverse identity-matrix)))))

(defn add-page [id name] {:type :add-page :id id :name name})

(defn mod-page [{:keys [id]} attrs] (merge {:type :mod-page :id id} attrs))

(defn del-page [{:keys [id]}] {:type :del-page :id id})

(defn add-objects [page-id objects]
  (mapv (fn [obj]
          {:type :add-obj :id (:id obj) :page-id page-id :parent-id (:parent-id obj) :frame-id (:frame-id obj)
           :index nil :ignore-touched false :obj obj})
        objects))
