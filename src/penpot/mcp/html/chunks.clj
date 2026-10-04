(ns penpot.mcp.html.chunks)

(defn- size [node]
  (inc (reduce + (map size (:children node)))))

(defn- add-unit [{:keys [calls room budget] :as state} unit n]
  (if (and (seq (peek calls)) (> n room))
    (recur (assoc state :calls (conj calls []) :room budget) unit n)
    (-> state
        (update :calls #(conj (pop %) (conj (peek %) unit)))
        (update :room - n))))

(declare emit)

(defn- emit-children [state parent-key children path]
  (reduce (fn [st [i child]] (emit st parent-key child (str path "." i))) state (map-indexed vector children)))

(defn- emit [state parent-key node path]
  (let [n    (size node)
        root (nil? parent-key)
        base (cond-> {:parent parent-key} root (assoc :root true))]
    (if (<= n (:budget state))
      (add-unit state (assoc base :node node) n)
      (-> state
          (add-unit (assoc base :node (assoc node :children [] :key path)) 1)
          (emit-children path (:children node) path)))))

(defn split [node budget]
  (:calls (emit {:calls [[]] :room budget :budget budget} nil node "k0")))
