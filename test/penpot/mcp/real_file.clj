(ns penpot.mcp.real-file
  (:require
   [app.common.uuid :as uuid]
   [penpot.mcp.replay :as replay]))

(def ^:private recorded
  (delay (first (replay/penpot-answers "token-usage/saved" :get-file))))

(defn file []
  @recorded)

(defn pages []
  (map #(get-in (file) [:data :pages-index %]) (get-in (file) [:data :pages])))

(defn shapes []
  (for [p (pages)
        s (vals (:objects p))
        :when (not= uuid/zero (:id s))]
    {:shape s :objects (:objects p) :page p}))

(defn having [pred description]
  (let [found (filter #(pred (:shape %)) (shapes))]
    (when (empty? found)
      (throw (ex-info (str "The recorded file has no " description) {})))
    found))

(defn one [pred description]
  (first (sort-by (comp str :id :shape) (having pred description))))
