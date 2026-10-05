(ns penpot.mcp.design.render.scss
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.render.css :as css]
   [penpot.mcp.design.render.naming :as naming]
   [penpot.mcp.design.render.table :as table]))

(defn- scss-text [s]
  (str/replace s "#{" "\\#{"))

(defn- joined [& parts]
  (str/join "-" (remove str/blank? parts)))

(defn- top-level-comma? [s]
  (let [step (fn [{:keys [quote escaped depth found] :as state} c]
               (cond
                 found (reduced state)
                 escaped (assoc state :escaped false)
                 (and quote (= c \\)) (assoc state :escaped true)
                 quote (cond-> state (= c quote) (assoc :quote nil))
                 (#{\" \'} c) (assoc state :quote c)
                 (= c \() (update state :depth inc)
                 (= c \)) (update state :depth dec)
                 (and (= c \,) (zero? depth)) (assoc state :found true)
                 :else state))]
    (boolean (:found (reduce step {:quote nil :escaped false :depth 0 :found false} s)))))

(defn- rows [entries id]
  (for [{:keys [ident values]} entries
        :let [v (get values id)]
        :when v
        [suffix css-value] (css/declarations v)
        :when css-value]
    {:name (joined ident (naming/kebab suffix))
     :value (scss-text css-value)
     :list? (top-level-comma? css-value)}))

(defn- variable-line [prefix {:keys [name value]}]
  (str "$" (joined prefix name) ": " value ";"))

(defn- map-line [{:keys [name value list?]}]
  (str "    " (scss-text (css/quoted name)) ": " (if list? (str "(" value ")") value)))

(defn- theme-map [entries {:keys [id]}]
  (str "  " (scss-text (css/quoted id)) ": (\n" (str/join ",\n" (map map-line (rows entries id))) "\n  )"))

(defn render [model {:keys [prefix]}]
  (let [default (table/default-combination model)
        named   (naming/resolve-collisions (concat (table/tokens model) (table/library model))
                                           #(naming/kebab (:path %)) :values)
        entries (:entries named)
        lines   (map #(variable-line prefix %) (rows entries (:id default)))
        themes  (str "$" (joined prefix "themes") ": (\n"
                     (str/join ",\n" (map #(theme-map entries %) (:combinations model))) "\n);\n")]
    {:files [{:path "_tokens.scss" :content (str (str/join "\n" lines) "\n\n" themes)}]
     :problems (:problems named)}))
