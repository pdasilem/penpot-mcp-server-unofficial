(ns penpot.mcp.html.uploads
  (:import
   (java.util UUID)))

(def ^:private ttl-ms (* 60 60 1000))
(def ^:private default-max-bytes (* 100 1024 1024))

(defn store [{:keys [now max-bytes]}]
  {:entries (atom {}) :now now :max-bytes (or max-bytes default-max-bytes)})

(defn- expired? [now-ms {:keys [touched]}]
  (> (- now-ms touched) ttl-ms))

(defn- sweep [entries now-ms]
  (into {} (remove (fn [[_ e]] (expired? now-ms e))) entries))

(defn- stored-bytes [entries]
  (reduce + 0 (map (comp count :text) (vals entries))))

(defn put! [{:keys [entries now max-bytes]} text]
  (let [id     (str (UUID/randomUUID))
        now-ms (now)
        after  (swap! entries (fn [es]
                                (let [es (sweep es now-ms)]
                                  (if (> (+ (stored-bytes es) (count text)) max-bytes)
                                    es
                                    (assoc es id {:text text :touched now-ms})))))]
    (when (contains? after id) id)))

(defn text [{:keys [entries now]} id]
  (let [now-ms (now)
        after  (swap! entries (fn [es]
                                (let [es (sweep es now-ms)]
                                  (cond-> es (contains? es id) (assoc-in [id :touched] now-ms)))))]
    (get-in after [id :text])))

(defn remove! [{:keys [entries]} id]
  (swap! entries dissoc id)
  nil)
