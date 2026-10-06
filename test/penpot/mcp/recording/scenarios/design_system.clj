(ns penpot.mcp.recording.scenarios.design-system)

(defn- export-args [platform & kvs]
  (fn [f] (merge {"file_id" (:fid f) "platform" platform} (when (seq kvs) {"options" (apply hash-map kvs)}))))

(def scenarios
  [{:name "design-system/css" :tool "export_design_system" :editor true :args (export-args "css" "prefix" "ds" "color_scheme_group" "Scheme")}
   {:name "design-system/scss" :tool "export_design_system" :editor true :args (export-args "scss")}
   {:name "design-system/tailwind-4" :tool "export_design_system" :editor true :args (export-args "tailwind")}
   {:name "design-system/tailwind-3" :tool "export_design_system" :editor true :args (export-args "tailwind" "version" 3)}
   {:name "design-system/typescript" :tool "export_design_system" :editor true :args (export-args "typescript")}
   {:name "design-system/dtcg" :tool "export_design_system" :editor true :args (export-args "dtcg")}
   {:name "design-system/kotlin" :tool "export_design_system" :editor true :args (export-args "kotlin" "package" "com.recorded.design")}
   {:name "design-system/swiftui" :tool "export_design_system" :editor true :args (export-args "swiftui")}
   {:name "design-system/closed-editor" :tool "export_design_system" :args (export-args "css")}])
