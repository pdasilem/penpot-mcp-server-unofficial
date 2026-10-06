(ns penpot.mcp.tools.edit-support
  (:require
   [clojure.test :refer [is]]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools :as all]))

(def tools (into {} (map (juxt :name identity)) all/all))

(defn run [scenario]
  (let [r (replay/run (tools (:tool (replay/recording scenario))) scenario)]
    (is (empty? (:left r)) (str scenario " left recorded requests unused"))
    (replay/data r)))

(defn args [scenario]
  (:args (replay/recording scenario)))
