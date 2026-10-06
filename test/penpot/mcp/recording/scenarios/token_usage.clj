(ns penpot.mcp.recording.scenarios.token-usage
  (:require
   [penpot.mcp.recording.data :as d]))

(defn- file-only [f] {"file_id" (:fid f)})

(def scenarios
  [{:name "token-usage/saved" :tool "token_usage" :args file-only}
   {:name "token-usage/saved-page" :tool "token_usage" :args #(assoc (file-only %) "page_id" (d/page-id % "Model"))}
   {:name "token-usage/saved-absent-page" :tool "token_usage" :args #(assoc (file-only %) "page_id" (:absent-page-id %))}
   {:name "token-usage/editor" :tool "token_usage" :editor true :args file-only}
   {:name "token-usage/editor-paged" :tool "token_usage" :editor true :args #(assoc (file-only %) "limit" 7 "cursor" "7")}
   {:name "token-usage/editor-sections" :tool "token_usage" :editor true :args #(assoc (file-only %) "sections" ["unused" "missing"])}
   {:name "token-usage/editor-usage-only" :tool "token_usage" :editor true :args #(assoc (file-only %) "sections" ["usage"])}])
