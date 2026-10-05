(ns penpot.mcp.design.color
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.color.colorjs :as colorjs]
   [penpot.mcp.design.color.tinycolor :as tinycolor]))

(def ^:private gradient-prefixes
  (for [kind ["linear" "radial" "conic"]
        prefix ["" "repeating-"]]
    (str prefix kind "-gradient")))

(defn- gradient? [s]
  (boolean (some #(str/starts-with? s %) gradient-prefixes)))

(defn css-color [s]
  (let [tc (tinycolor/parse-color s)]
    (when (and (:ok tc) (not (gradient? s)))
      (if (= 1.0 (:a tc)) (tinycolor/hex-string tc) (tinycolor/rgb-string tc)))))

(defn- penpot-color [s]
  (let [tc (tinycolor/parse-color s)]
    (when (and (:ok tc)
               (:format tc)
               (or (not (str/starts-with? (:format tc) "hex"))
                   (str/starts-with? s "#")))
      tc)))

(defn valid-color? [s]
  (some? (penpot-color s)))

(defn color-format [s]
  (:format (penpot-color s)))

(defn rgba [s]
  (some-> (penpot-color s) tinycolor/channels))

(defn hex-rgba [s]
  (colorjs/hex-rgba s))
