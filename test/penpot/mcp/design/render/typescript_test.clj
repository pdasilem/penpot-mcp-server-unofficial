(ns penpot.mcp.design.render.typescript-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.real-model :as real]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.render.naming :as naming]))

(def ^:private content
  (delay (:content (first (:files (render/render (fixture/model) :typescript {}))))))

(defn- theme-block [id]
  (second (re-find (re-pattern (str "(?s)\"" (java.util.regex.Pattern/quote id) "\": \\{\n(.*?)\n  \\}")) @content)))

(deftest every-combination-is-a-theme-with-the-same-keys
  (let [keys-of #(set (map second (re-seq #"(?m)^    ([A-Za-z_$][A-Za-z0-9_$]*):" %)))
        blocks  (map (comp theme-block :id) (:combinations (fixture/model)))]
    (is (every? some? blocks))
    (is (= 1 (count (set (map keys-of blocks)))))))

(deftest uniform-tokens-are-camel-cased-keys
  (let [block (theme-block (:id (real/default-combination)))]
    (doseq [{:keys [path]} (real/uniform-tokens)
            :let [top (if (= 1 (count path)) (naming/camel path) (naming/camel [(first path)]))]]
      (is (re-find (re-pattern (str "(?m)^    (" top "|\"" (java.util.regex.Pattern/quote (first path)) "\"):")) block) (str/join "." path)))))

(deftest colors-are-hex-strings
  (let [block (theme-block (:id (real/default-combination)))]
    (doseq [{:keys [path value]} (real/colors)
            :when (= 1 (count path))]
      (is (str/includes? block (str (naming/camel path) ": \"" (real/hex (:rgba value)) "\"")) (str/join "." path)))))

(deftest the-file-exports-the-theme-types
  (is (str/includes? @content "export type ThemeId = keyof typeof themes;")))
