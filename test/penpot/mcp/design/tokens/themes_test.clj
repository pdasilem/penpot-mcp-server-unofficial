(ns penpot.mcp.design.tokens.themes-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.tokens.themes :as themes]))

(def ^:private catalog
  {:sets [{:name "core" :active true :tokens [{:name "space" :type :spacing :value "4"} {:name "bg" :type :color :value "#ffffff"}]}
          {:name "mode/light" :active true :tokens [{:name "bg" :type :color :value "#fafafa"}]}
          {:name "mode/dark" :active false :tokens [{:name "bg" :type :color :value "#111111"}]}
          {:name "brand/a" :active true :tokens [{:name "accent" :type :color :value "#ff0000"}]}
          {:name "brand/b" :active false :tokens [{:name "accent" :type :color :value "#0000ff"}]}]
   :themes [{:group "mode" :name "light" :sets ["core" "mode/light"]}
            {:group "mode" :name "dark" :sets ["mode/dark" "core"]}
            {:group "brand" :name "a" :sets ["brand/a"]}
            {:group "brand" :name "b" :sets ["brand/b"]}]})

(deftest every-combination-takes-one-theme-per-group
  (is (= [{"brand" "a" "mode" "light"} {"brand" "a" "mode" "dark"} {"brand" "b" "mode" "light"} {"brand" "b" "mode" "dark"}]
         (map :themes (themes/combinations catalog)))))

(deftest later-sets-in-library-order-override-earlier-ones
  (let [by-themes (into {} (map (juxt :themes :tokens)) (themes/combinations catalog))]
    (is (= "#111111" (get-in by-themes [{"brand" "a" "mode" "dark"} "bg" :value])))
    (is (= "#fafafa" (get-in by-themes [{"brand" "a" "mode" "light"} "bg" :value])))
    (is (= "#0000ff" (get-in by-themes [{"brand" "b" "mode" "dark"} "accent" :value])))
    (is (= "4" (get-in by-themes [{"brand" "b" "mode" "dark"} "space" :value])))))

(deftest without-themes-the-active-sets-make-one-combination
  (let [[only :as all] (themes/combinations (assoc catalog :themes []))]
    (is (= 1 (count all)))
    (is (= {} (:themes only)))
    (is (= "#fafafa" (get-in only [:tokens "bg" :value])))
    (is (= "#ff0000" (get-in only [:tokens "accent" :value])))))

(deftest more-than-sixty-four-combinations-are-refused
  (let [themes (for [g (range 3) t (range 5)] {:group (str "g" g) :name (str "t" t) :sets []})]
    (is (= 125 (:count (ex-data (try (doall (themes/combinations {:sets [] :themes themes}))
                                     (catch clojure.lang.ExceptionInfo e e))))))
    (is (= 64 (count (themes/combinations {:sets [] :themes (for [g (range 3) t (range 4)] {:group (str "g" g) :name (str "t" t) :sets []})}))))))
