(ns penpot.mcp.design.sd-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.sd :as sd]
   [penpot.mcp.design.sd.trees :as trees]))

(defn- default-sets []
  (let [wanted (set (:sets (first (filter :active (:themes (fixture/catalog))))))]
    (filter #(wanted (:name %)) (:sets (fixture/catalog)))))

(defn- simple-tokens []
  (let [all (reduce (fn [m t] (assoc m (:name t) t)) {} (mapcat :tokens (default-sets)))]
    (filter (comp string? :value) (vals all))))

(def ^:private result
  (delay (sd/resolve-tree (trees/tree (simple-tokens)))))

(deftest real-tokens-resolve-without-failures
  (is (seq (simple-tokens)))
  (is (empty? (:failures @result)))
  (is (empty? (:warnings @result))))

(deftest references-in-real-tokens-are-resolved
  (doseq [{:keys [name value]} (simple-tokens)
          :when (str/includes? value "{")]
    (is (not (str/includes? (str (get (:values @result) name)) "{")) name)))

(deftest real-spacing-and-sizing-get-pixels
  (doseq [{:keys [name type]} (simple-tokens)
          :when (#{:spacing :sizing} type)]
    (is (re-matches #"-?[0-9.]+px" (str (get (:values @result) name))) name)))

(deftest real-colors-are-normalized
  (doseq [{:keys [name type]} (simple-tokens)
          :when (= :color type)]
    (is (re-matches #"#[0-9a-f]{6}|rgba\(.*\)" (str (get (:values @result) name))) name)))
