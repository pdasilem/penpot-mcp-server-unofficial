(ns penpot.mcp.design.sd.pipeline
  (:require
   [penpot.mcp.design.sd.references :as references]
   [penpot.mcp.design.sd.transforms :as transforms]))

(defn- transform-pass [{:keys [order transformed] :as state}]
  (reduce (fn [acc k]
            (let [token (get-in acc [:tokens k])]
              (if (references/uses-references? (get token "value"))
                (update acc :deferred conj k)
                (-> acc
                    (assoc-in [:tokens k] (transforms/transform-token token))
                    (update :deferred disj k)
                    (update :transformed conj k)))))
          state
          (remove transformed order)))

(defn run-passes [{:keys [order tokens]}]
  (loop [state {:order order :tokens tokens :deferred #{} :transformed #{}}
         previous 0]
    (let [state    (transform-pass state)
          resolved (references/resolve-token-map (:tokens state) order (:deferred state))
          state    (assoc state :tokens resolved)
          n        (count (:deferred state))]
      (cond
        (zero? n) (:tokens state)
        (= n previous) (references/resolve-token-map (:tokens state) order #{})
        :else (recur state n)))))
