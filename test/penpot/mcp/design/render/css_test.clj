(ns penpot.mcp.design.render.css-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.real-model :as real]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.render.naming :as naming]))

(defn- css [options]
  (:content (first (:files (render/render (fixture/model) :css options)))))

(defn- blocks [content]
  (into {} (for [[_ selector body] (re-seq #"(?m)^([^\s@}][^{\n]*) \{\n((?:  [^\n]*\n)*)\}" content)]
             [selector (into {} (map (fn [[_ k v]] [k v])) (re-seq #"  (--[^:]+): ([^\n]*);" body))])))

(defn- default-tokens []
  (:tokens (first (filter :default? (:combinations (fixture/model))))))

(deftest every-token-of-the-default-combination-is-in-root
  (let [root (get (blocks (css {:prefix "ds"})) ":root")]
    (doseq [{:keys [path]} (default-tokens)
            :let [base (str "--ds-" (naming/kebab path))]]
      (is (some #(or (= base %) (str/starts-with? % (str base "-"))) (keys root)) (str/join "." path)))))

(deftest every-other-combination-has-its-own-selector-with-only-what-differs
  (let [all  (blocks (css {:prefix "ds"}))
        root (get all ":root")]
    (doseq [{:keys [themes default?]} (:combinations (fixture/model))
            :when (not default?)
            :let [[_ theme] (first themes)
                  selector (str ":root[data-scheme=\"" theme "\"]")
                  block (get all selector)]]
      (is (seq block) selector)
      (doseq [[k v] block]
        (is (not= (get root k) v) (str selector " " k))))))

(deftest the-library-color-is-a-variable
  (is (str/includes? (css {:prefix "ds"}) "--ds-library-color-")))

(deftest without-a-prefix-names-start-with-the-token-path
  (let [{:keys [path]} (first (default-tokens))]
    (is (str/includes? (css {}) (str "  --" (naming/kebab path) ": ")))))

(deftest the-css-prefix-is-checked
  (doseq [prefix ["Bad" (apply str (repeat 32 "a"))]]
    (is (= :penpot.mcp.design.render/invalid-option
           (:type (ex-data (try (render/render (fixture/model) :css {:prefix prefix}) (catch Exception e e))))))))

(defn- dark-pair []
  (let [default (real/default-combination)
        [group theme] (first (:themes default))
        dark (str/replace theme #"(?i)\blight\b" "dark")]
    {:group group :default theme
     :dark (first (filter #(= dark (get (:themes %) group)) (:combinations (fixture/model))))}))

(deftest the-system-dark-scheme-takes-the-dark-theme-of-the-default-palette
  (let [{:keys [group dark]} (dark-pair)
        content (:content (first (:files (render/render (fixture/model) :css {:color-scheme-group group}))))
        media   (second (re-find #"(?s)@media \(prefers-color-scheme: dark\) \{\n(.*?)\n\}\n?$" content))
        surface (first (filter #(= ["surface"] (:path %)) (:tokens dark)))]
    (is (some? media))
    (is (str/includes? media (str ":root:not([data-" (str/lower-case group) "])")))
    (is (str/includes? media (str "--surface: " (real/hex (get-in surface [:value :rgba])) ";")))
    (is (= 1 (count (re-seq #"prefers-color-scheme" content))))))

