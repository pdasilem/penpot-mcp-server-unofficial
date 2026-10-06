(ns penpot.mcp.penpot.contract
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(def data
  (edn/read-string (slurp (io/resource "penpot/contract.edn"))))

(def tokens (:tokens data))

(def supported-features (:supported-features data))

(def max-fills (:max-fills data))

(def max-gradient-stops (:max-gradient-stops data))
