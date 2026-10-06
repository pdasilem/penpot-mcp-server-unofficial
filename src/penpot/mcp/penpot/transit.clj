(ns penpot.mcp.penpot.transit
  (:require
   [cognitect.transit :as t]
   [linked.core :as linked]
   [penpot.mcp.penpot.tokens-lib :as tokens-lib]
   [penpot.mcp.penpot.types :as types])
  (:import
   (java.io ByteArrayInputStream ByteArrayOutputStream)
   (java.net URI)
   (java.time Duration Instant OffsetDateTime)
   (linked.map LinkedMap)
   (linked.set LinkedSet)
   (penpot.mcp.penpot.types Matrix PathData Point Rect Shape)))

(declare decode)

(defn- objects [data]
  (into {} (map (fn [[k v]] [k (if (string? v) (decode v) v)])) data))

(def ^:private read-handlers
  (t/read-handler-map
   {"shape" (t/read-handler types/map->Shape)
    "matrix" (t/read-handler types/map->Matrix)
    "point" (t/read-handler types/map->Point)
    "rect" (t/read-handler types/map->Rect)
    "penpot/fills" (t/read-handler identity)
    "penpot/path-data" (t/read-handler types/path-data)
    "penpot/objects-map/v2" (t/read-handler objects)
    "penpot/tokens-lib" (t/read-handler tokens-lib/tokens-lib)
    "penpot/token-set" (t/read-handler tokens-lib/token-set)
    "penpot/token-theme" (t/read-handler tokens-lib/token-theme)
    "penpot/token" (t/read-handler tokens-lib/token)
    "penpot/tokens-status" (t/read-handler identity)
    "penpot/pointer" (t/read-handler (fn [[id meta]] {:penpot/pointer id :meta meta}))
    "ordered-map" (t/read-handler #(into (linked/map) %))
    "ordered-set" (t/read-handler #(into (linked/set) %))
    "m" (t/read-handler #(Instant/ofEpochMilli (Long/parseLong (str %))))
    "duration" (t/read-handler #(Duration/ofMillis (long %)))
    "uri" (t/read-handler #(URI. (str %)))}))

(defn- map-writer [tag]
  (t/write-handler (constantly tag) #(into {} %)))

(def ^:private write-handlers
  (t/write-handler-map
   {Shape (map-writer "shape")
    Matrix (map-writer "matrix")
    Point (map-writer "point")
    Rect (map-writer "rect")
    PathData (t/write-handler (constantly "penpot/path-data") types/path-bytes)
    LinkedMap (t/write-handler (constantly "ordered-map") vec)
    LinkedSet (t/write-handler (constantly "ordered-set") vec)
    Instant (t/write-handler (constantly "m") #(str (.toEpochMilli ^Instant %)))
    OffsetDateTime (t/write-handler (constantly "m") #(str (.toEpochMilli (.toInstant ^OffsetDateTime %))))
    Duration (t/write-handler (constantly "duration") #(.toMillis ^Duration %))
    URI (t/write-handler (constantly "uri") str)}))

(defn encode [value]
  (let [out (ByteArrayOutputStream.)]
    (t/write (t/writer out :json {:handlers write-handlers}) value)
    (.toString out "UTF-8")))

(defn decode [^String text]
  (t/read (t/reader (ByteArrayInputStream. (.getBytes text "UTF-8")) :json {:handlers read-handlers})))
