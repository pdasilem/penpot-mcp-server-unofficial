(ns penpot.mcp.transform.geometry)

(defn- field [shape k]
  (or (get shape k) (get-in shape [:selrect k])))

(defn x [shape] (field shape :x))

(defn y [shape] (field shape :y))

(defn width [shape] (field shape :width))

(defn height [shape] (field shape :height))

(defn bounds [shape]
  {:x (x shape) :y (y shape) :width (width shape) :height (height shape)})
