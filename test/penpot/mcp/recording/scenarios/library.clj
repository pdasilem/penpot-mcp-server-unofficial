(ns penpot.mcp.recording.scenarios.library
  (:require
   [penpot.mcp.recording.data :as d]))

(defn- file-only [f] {"file_id" (:fid f)})

(def scenarios
  [{:name "library/components" :tool "list_components" :args file-only}
   {:name "library/components-query" :tool "list_components" :args #(assoc (file-only %) "query" "button")}
   {:name "library/instances" :tool "get_component_instances"
    :args #(assoc (file-only %) "component_id" (str (:id (d/component-with-copies %))))}
   {:name "library/colors" :tool "get_colors" :args file-only}
   {:name "library/typographies" :tool "get_typographies" :args file-only}
   {:name "library/tokens" :tool "get_design_tokens" :args file-only}
   {:name "library/tokens-query" :tool "get_design_tokens" :args #(assoc (file-only %) "query" "surface")}
   {:name "library/tokens-type" :tool "get_design_tokens" :args #(assoc (file-only %) "type" "color")}
   {:name "library/components-large-file" :tool "list_components" :file :source :args file-only}
   {:name "library/components-editor" :tool "list_components" :editor true :args file-only}
   {:name "library/instances-editor" :tool "get_component_instances" :editor true
    :args #(assoc (file-only %) "component_id" (str (:id (d/component-with-copies %))))}
   {:name "library/colors-editor" :tool "get_colors" :editor true :args file-only}
   {:name "library/typographies-editor" :tool "get_typographies" :editor true :args file-only}
   {:name "library/tokens-editor" :tool "get_design_tokens" :editor true :args file-only}
   {:name "library/tokens-query-editor" :tool "get_design_tokens" :editor true :args #(assoc (file-only %) "query" "surface")}
   {:name "library/tokens-type-editor" :tool "get_design_tokens" :editor true :args #(assoc (file-only %) "type" "color")}])
