(ns penpot.mcp.design.reference
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]))

(def ^:private dir "test/token-reference")

(defn run-script
  ([script input] (run-script script input identity))
  ([script input key-fn]
   (when-not (.exists (io/file dir "node_modules"))
     (throw (ex-info (str "Run npm ci in " dir " first") {})))
   (let [{:keys [exit out err]} (sh/sh "node" script :in (json/write-str input) :dir dir)]
     (when-not (zero? exit)
       (throw (ex-info (str "Reference script " script " failed: " err) {})))
     (json/read-str out :key-fn key-fn))))

(defn resolve-cases [cases]
  (get (run-script "resolve.mjs" {:cases cases}) "results"))
