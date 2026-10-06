(ns penpot.mcp.design.render.tailwind-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.real-model :as real]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.render.naming :as naming]))

(defn- files [options]
  (into {} (map (juxt :path :content)) (:files (render/render (fixture/model) :tailwind options))))

(deftest version-4-declares-every-color-in-the-theme-block
  (let [content (get (files {}) "tokens.css")
        theme   (second (re-find #"(?s)@theme \{\n(.*?)\n\}" content))]
    (doseq [{:keys [path value]} (real/colors)]
      (is (str/includes? theme (str "--color-" (naming/kebab path) ": " (real/hex (:rgba value)) ";")) (str/join "." path)))))

(deftest version-4-overrides-in-other-combinations-list-only-what-differs
  (let [content (get (files {}) "tokens.css")
        blocks  (re-seq #"(?m)^:root\[data-scheme=\"([^\"]+)\"\] \{\n((?:  [^\n]*\n)*)\}" content)]
    (is (= (dec (count (:combinations (fixture/model)))) (count blocks)))
    (doseq [[_ _ body] blocks]
      (is (seq body)))))

(deftest version-3-maps-every-color-to-its-css-variable
  (let [{config "tailwind.theme.js" css "tokens.css"} (files {:version 3})]
    (doseq [{:keys [path]} (real/colors)
            :let [n   (naming/kebab path)
                  key (if (= 1 (count path)) n (naming/kebab [(last path)]))]]
      (is (str/includes? config (str "\"" key "\": \"var(--" n ")\"")) n)
      (is (str/includes? css (str "  --" n ": ")) n))))

(deftest invalid-options-are-rejected
  (doseq [options [{:version 2} {:prefix "Bad"}]]
    (is (= :penpot.mcp.design.render/invalid-option
           (:type (ex-data (try (render/render (fixture/model) :tailwind options) (catch Exception e e))))))))

(deftest the-dark-variant-covers-every-dark-theme-of-the-group
  (let [group   (key (first (:themes (first (:combinations (fixture/model))))))
        content (get (files {:color-scheme-group group}) "tokens.css")
        darks   (filter #(re-find #"(?i)\bdark\b" %) (map #(get (:themes %) group) (:combinations (fixture/model))))
        variant (second (re-find #"@custom-variant dark \(&:where\((.*)\)\);" content))]
    (is (seq darks))
    (doseq [d darks]
      (is (str/includes? variant (str "[data-" (str/lower-case group) "=\"" d "\"]")) d))))
