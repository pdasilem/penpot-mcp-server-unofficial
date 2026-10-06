(ns penpot.mcp.design.export-test
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]))

(defn- catalog-tokens [set-names]
  (let [by-name (into {} (map (juxt :name identity)) (:sets (fixture/catalog)))]
    (mapcat (comp :tokens by-name) set-names)))

(deftest combinations-follow-the-themes-and-the-active-one-is-the-default
  (let [themes (:themes (fixture/catalog))]
    (is (= (map #(str (:group %) "=" (:name %)) themes) (map :id (:combinations (fixture/model)))))
    (is (= (map :active themes) (map :default? (:combinations (fixture/model)))))))

(deftest every-combination-holds-the-tokens-of-its-sets
  (doseq [{:keys [themes tokens]} (:combinations (fixture/model))
          :let [[group theme-name] (first themes)
                theme (some #(when (and (= group (:group %)) (= theme-name (:name %))) %) (:themes (fixture/catalog)))]]
    (is (= (set (map :name (catalog-tokens (:sets theme)))) (set (map :name tokens))) theme-name)))

(deftest values-are-normalized-per-kind
  (let [kinds (frequencies (map (comp :kind :value) (:tokens (first (:combinations (fixture/model))))))]
    (is (every? kinds [:color :dimension :number :font-weight :font-family :typography :shadow]))))

(deftest colors-keep-the-css-penpot-shows
  (doseq [{:keys [value name]} (:tokens (first (:combinations (fixture/model))))
          :when (= :color (:kind value))]
    (is (re-matches #"#[0-9a-f]{6}|rgba\(.*\)" (:css value)) name)))

(deftest typed-platforms-get-the-tokens-present-everywhere
  (let [per-combination (map (comp set (partial map :name) :tokens) (:combinations (fixture/model)))]
    (is (= (apply set/intersection per-combination) (set (:uniform (fixture/model)))))))

(deftest library-colors-are-normalized
  (let [{:keys [name value]} (first (get-in (fixture/model) [:library :colors]))
        source (first (:colors (fixture/catalog)))]
    (is (str/ends-with? name (str (:name source))))
    (is (= (str/lower-case (:color source)) (:css value)))))
