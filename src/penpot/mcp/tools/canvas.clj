(ns penpot.mcp.tools.canvas
  (:require
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.tool :as tool]))

(def shape-ids-param
  [:shape_ids {:description "Shape ids"} [:vector {:min 1} :uuid]])

(def editor-note
  " [editor]")

(defn plugin-tool [{:keys [name description input-schema body args result-key annotations] :or {result-key :shape}}]
  {:name name
   :description (str description editor-note)
   :annotations annotations
   :input-schema input-schema
   :handler (fn [ctx params]
              (let [file-id (:file_id params)
                    result  (revision/mutate! ctx file-id body (args params))]
                (tool/json-result (if result-key {result-key result} result))))})

(def focus-shape
  "const s = await focusShape(args.shapeId);\n")

(def finish
  "await settle();\nmarkChanged();\nreturn info(s);")

(def track-change
  "const before = fingerprint(s);\nconst beforeInfo = info(s);\n")

(def finish-tracked
  "await settle();\nif (fingerprint(s) !== before) markChanged();\nreturn changes(beforeInfo, s);")
