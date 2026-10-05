(ns penpot.mcp.design.tokens
  (:require
   [penpot.mcp.design.budget :as budget]
   [penpot.mcp.design.tokens.admission :as admission]
   [penpot.mcp.design.tokens.catalog :as catalog]
   [penpot.mcp.design.tokens.themes :as themes]))

(def ^:private timeout-ms 30000)

(defn resolve-catalog [catalog]
  (admission/check-catalog! catalog)
  (let [combinations (themes/combinations catalog)]
    {:combinations (budget/run timeout-ms #(catalog/resolve-combinations combinations))
     :warnings (mapv (fn [set-name] {:code :set-outside-themes :set set-name}) (themes/unused-sets catalog))}))
