(ns penpot.mcp.penpot.uuid
  (:refer-clojure :exclude [next]))

(def zero #uuid "00000000-0000-0000-0000-000000000000")

(defn next []
  (random-uuid))
