(ns penpot.mcp.design.render
  (:require
   [penpot.mcp.design.render.css :as css]
   [penpot.mcp.design.render.dtcg :as dtcg]
   [penpot.mcp.design.render.kotlin :as kotlin]
   [penpot.mcp.design.render.naming :as naming]
   [penpot.mcp.design.render.options :as options]
   [penpot.mcp.design.render.scss :as scss]
   [penpot.mcp.design.render.swiftui :as swiftui]
   [penpot.mcp.design.render.tailwind :as tailwind]
   [penpot.mcp.design.render.typescript :as typescript]))

(def ^:private renderers
  {:css css/render
   :scss scss/render
   :tailwind tailwind/render
   :typescript typescript/render
   :dtcg dtcg/render
   :kotlin kotlin/render
   :swiftui swiftui/render})

(def platforms (set (keys renderers)))

(defn render [model platform options]
  (if-let [f (get renderers platform)]
    (do (options/check platform options)
        (f model options))
    (throw (ex-info (str "Unknown platform " (name platform)) {:type ::unknown-platform :platform platform}))))

(def ^:private reserved-type-names
  #{"Color" "Font" "Text" "View" "Gradient" "Shadow" "Image" "Shape" "String" "Int" "Long" "Float" "Double" "Boolean" "List"
    "FontWeightToken" "TypographyToken" "ShadowToken" "GradientToken" "GradientStop"})

(defn type-name [file-name]
  (let [n (naming/pascal [file-name])]
    (if (and (re-matches #"[A-Z][A-Za-z0-9]*" n) (not (contains? reserved-type-names n))) n "DesignTokens")))
