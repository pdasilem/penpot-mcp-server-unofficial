(ns penpot.mcp.penpot.transit
  (:require
   [app.common.geom.matrix]
   [app.common.geom.point]
   [app.common.geom.rect]
   [app.common.transit :as t]
   [app.common.types.fills.impl]
   [app.common.types.objects-map]
   [app.common.types.path.impl]
   [app.common.types.shape]
   [app.common.types.tokens-lib]
   [app.common.types.tokens-status]))

(defn encode [value]
  (t/encode-str value))

(defn decode [^String text]
  (t/decode-str text))
