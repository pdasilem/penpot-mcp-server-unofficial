(ns penpot.mcp.design.render.scss-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.real-model :as real]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.render.naming :as naming]))

(defn- scss [options]
  (:content (first (:files (render/render (fixture/model) :scss options)))))

(deftest every-token-of-the-default-combination-becomes-a-variable
  (let [content (scss {})]
    (doseq [{:keys [path]} (real/default-tokens)]
      (is (re-find (re-pattern (str "(?m)^\\$" (java.util.regex.Pattern/quote (naming/kebab path)) "[-:]")) content) (str/join "." path)))))

(deftest colors-keep-their-hex-values
  (let [content (scss {})]
    (doseq [{:keys [path value]} (real/colors)]
      (is (str/includes? content (str "$" (naming/kebab path) ": " (real/hex (:rgba value)) ";")) (str/join "." path)))))

(deftest every-combination-is-an-entry-of-the-themes-map
  (let [content (scss {})]
    (doseq [{:keys [id]} (:combinations (fixture/model))]
      (is (str/includes? content (str "  \"" id "\": (")) id))))

(deftest a-prefix-starts-every-variable
  (is (re-find #"(?m)^\$ds-" (scss {:prefix "ds"})))
  (is (not (re-find #"(?m)^\$(?!ds-)[a-z]" (scss {:prefix "ds"})))))

(deftest an-invalid-prefix-is-rejected
  (is (= :penpot.mcp.design.render/invalid-option
         (:type (ex-data (try (render/render (fixture/model) :scss {:prefix "Bad"}) (catch Exception e e)))))))
