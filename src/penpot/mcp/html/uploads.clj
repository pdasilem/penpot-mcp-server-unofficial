(ns penpot.mcp.html.uploads
  (:import
   (java.util UUID)))

(def ^:private ttl-ms (* 60 60 1000))

(defn store [{:keys [now]}]
  {:entries (atom {}) :now now})

(defn- expired? [now-ms {:keys [touched]}]
  (> (- now-ms touched) ttl-ms))

(defn- sweep [entries now-ms]
  (into {} (remove (fn [[_ e]] (expired? now-ms e))) entries))

(defn put! [{:keys [entries now]} text]
  (let [id     (str (UUID/randomUUID))
        now-ms (now)]
    (swap! entries #(assoc (sweep % now-ms) id {:text text :touched now-ms}))
    id))

(defn text [{:keys [entries now]} id]
  (let [now-ms (now)
        after  (swap! entries (fn [es]
                                (let [es (sweep es now-ms)]
                                  (cond-> es (contains? es id) (assoc-in [id :touched] now-ms)))))]
    (get-in after [id :text])))

(defn remove! [{:keys [entries]} id]
  (swap! entries dissoc id)
  nil)
