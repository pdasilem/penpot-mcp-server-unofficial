(ns penpot.mcp.design.tokens-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.tokens :as tokens]))

(def ^:private resolved
  (delay (tokens/resolve-catalog (fixture/catalog))))

(defn- theme-sets [{:keys [themes]}]
  (let [[group theme-name] (first themes)]
    (:sets (some #(when (and (= group (:group %)) (= theme-name (:name %))) %) (:themes (fixture/catalog))))))

(defn- source-tokens [combination]
  (let [wanted (set (theme-sets combination))]
    (mapcat :tokens (filter #(wanted (:name %)) (:sets (fixture/catalog))))))

(deftest every-real-token-resolves-without-errors
  (doseq [{:keys [tokens themes]} (:combinations @resolved)
          [token-name result] tokens]
    (is (empty? (:errors result)) (str themes " " token-name))))

(deftest references-resolve-to-plain-values
  (doseq [combination (:combinations @resolved)
          {:keys [name value]} (source-tokens combination)
          :when (and (string? value) (str/includes? value "{"))
          :let [result (get (:tokens combination) name)]]
    (is (not (str/includes? (pr-str (:value result)) "{")) name)))

(deftest dimensions-resolve-to-numbers
  (doseq [[token-name {:keys [type value unit]}] (:tokens (first (:combinations @resolved)))
          :when (#{:spacing :sizing :dimensions :border-radius :stroke-width :font-size} type)]
    (is (number? value) token-name)
    (is (or (nil? unit) (string? unit)) token-name)))

(deftest later-sets-override-earlier-ones
  (doseq [combination (:combinations @resolved)
          :let [last-value (reduce (fn [m t] (assoc m (:name t) (:value t))) {} (source-tokens combination))]
          [token-name raw] last-value
          :when (and (string? raw) (re-matches #"#[0-9A-Fa-f]{6}" raw))]
    (is (= (str/lower-case raw) (:resolved (get (:tokens combination) token-name))) token-name)))

(deftest every-combination-takes-one-theme-per-group
  (is (= (map #(hash-map (:group %) (:name %)) (:themes (fixture/catalog)))
         (map :themes (:combinations @resolved)))))

(deftest sets-outside-every-theme-are-reported
  (let [in-themes (set (mapcat :sets (:themes (fixture/catalog))))
        outside   (remove in-themes (map :name (:sets (fixture/catalog))))]
    (is (= (map (fn [s] {:code :set-outside-themes :set s}) outside) (:warnings @resolved)))))
