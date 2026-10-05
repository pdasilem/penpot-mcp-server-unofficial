(ns penpot.mcp.design.tokens.catalog
  (:require
   [app.common.data :as d]
   [clojure.tools.logging :as log]
   [penpot.mcp.design.budget :as budget]
   [penpot.mcp.design.sd :as sd]
   [penpot.mcp.design.tokens.admission :as admission]
   [penpot.mcp.design.tokens.result :as result]
   [penpot.mcp.design.tokens.sd-input :as sd-input]))

(defn- resolve-set [tokens]
  (admission/check-count! tokens)
  (let [rejected   (into {} (keep (fn [t] (some->> (admission/rejection t) (vector (:name t))))) tokens)
        accepted   (remove #(contains? rejected (:name %)) tokens)
        resolution (sd/resolve-tree (sd-input/tree (sd-input/valid-tokens accepted)))]
    {:tokens (into (d/ordered-map)
                   (map (fn [token] [(:name token) (result/token-result resolution rejected token)]))
                   tokens)
     :warnings (:warnings resolution)}))

(def ^:private failure-codes
  {:penpot.mcp.design.sd/circular :circular-references
   :penpot.mcp.design.tokens.admission/too-many-tokens :too-many-tokens})

(defn- failure [e]
  (if-let [code (and (budget/own-failure? e) (get failure-codes (:type (ex-data e))))]
    {:failure {:code code :message (ex-message e)}}
    (do (log/warn e "Resolving a theme combination failed")
        {:failure {:code :resolution-failed :message "Resolving this theme combination failed"}})))

(defn- resolve-members [members]
  (try
    (resolve-set members)
    (catch Exception e
      (when (budget/exceeded? e)
        (throw e))
      (failure e))))

(defn resolve-combinations [combinations]
  (let [cache (atom {})]
    (mapv (fn [{:keys [themes tokens]}]
            (let [members (vec (vals tokens))
                  outcome (or (get @cache members)
                              (let [computed (resolve-members members)]
                                (swap! cache assoc members computed)
                                computed))]
              (assoc outcome :themes themes)))
          combinations)))
