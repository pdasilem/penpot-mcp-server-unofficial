(ns penpot.mcp.design.render.typescript
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.render.css :as css]
   [penpot.mcp.design.render.json :as json]
   [penpot.mcp.design.render.naming :as naming]
   [penpot.mcp.design.render.table :as table]
   [penpot.mcp.design.render.tree :as tree]))

(defn- weight-literal [{:keys [weight italic]}]
  (str "{ weight: " weight ", italic: " (boolean italic) " }"))

(declare literal)

(defn- typography-literal [{:keys [fields]}]
  (str "{ "
       (str/join ", " (map (fn [[k field]] (str (naming/camel [(name k)]) ": " (literal field)))
                           (sort-by (comp name key) fields)))
       " }"))

(defn literal [{:keys [kind] :as v}]
  (case kind
    :number (jsnum/to-string (:value v))
    :text (json/string (:value v))
    :font-weight (weight-literal v)
    :typography (typography-literal v)
    (json/string (css/simple v))))

(defn- indent [depth]
  (apply str (repeat depth "  ")))

(defn- node-lines [id depth {:keys [key entry children]}]
  (if entry
    [(str (indent depth) key ": " (literal (get (:values entry) id)) ",")]
    (concat [(str (indent depth) key ": {")]
            (mapcat #(node-lines id (inc depth) %) children)
            [(str (indent depth) "},")])))

(defn- theme-lines [nodes {:keys [id]}]
  (concat [(str "  " (json/string id) ": {")]
          (mapcat #(node-lines id 2 %) nodes)
          ["  },"]))

(defn- segments [{:keys [path]}]
  (mapv #(naming/camel [%]) path))

(defn- content [nodes combinations default]
  (str/join "\n"
            (concat ["export const themes = {"]
                    (mapcat #(theme-lines nodes %) combinations)
                    ["} as const;"
                     (str "export const defaultTheme = " (json/string (:id default)) ";")
                     "export type ThemeId = keyof typeof themes;"
                     "export type Tokens = (typeof themes)[ThemeId];"
                     ""])))

(defn render [model _options]
  (if-let [default (table/default-combination model)]
    (let [{:keys [entries problems]} (tree/entries model segments true)]
      {:files [{:path "tokens.ts" :content (content (tree/nest entries) (:combinations model) default)}]
       :problems problems})
    {:files [] :problems [{:code :no-combinations}]}))
