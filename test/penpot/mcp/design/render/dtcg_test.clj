(ns penpot.mcp.design.render.dtcg-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [penpot.mcp.design.render.dtcg :as dtcg]
   [penpot.mcp.design.fixture :as fixture]))

(defn- run [model]
  (let [{:keys [files problems]} (dtcg/render model {})]
    {:files files
     :by-path (into {} (map (juxt :path :content)) files)
     :problems problems}))

(defn- dtcg [c]
  (run (fixture/model c)))

(defn- as-doubles [doc]
  (walk/postwalk #(if (number? %) (double %) %) doc))

(defn- parsed [c path]
  (as-doubles (json/read-str (get (:by-path (dtcg c)) path))))

(defn- raw [& tokens]
  {:combinations [{:id "default" :themes {} :default? true :tokens (vec tokens)}]
   :uniform (mapv :name tokens)
   :library {:colors [] :typographies []}})

(defn- raw-token
  ([name value] (raw-token name value nil))
  ([name value description]
   (cond-> {:name name :path (str/split name #"\.") :type :other :value value}
     description (assoc :description description))))

(defn- first-doc [model]
  (json/read-str (:content (first (:files (run model))))))

(def ^:private light "tokens/brand-a_mode-light.tokens.json")

(deftest one-file-per-combination-named-after-its-themes
  (let [{:keys [files problems]} (dtcg fixture/catalog)]
    (is (= [] problems))
    (is (= ["tokens/brand-a_mode-light.tokens.json" "tokens/brand-a_mode-dark.tokens.json"
            "tokens/brand-b_mode-light.tokens.json" "tokens/brand-b_mode-dark.tokens.json"]
           (map :path files)))))

(deftest a-catalog-without-themes-renders-the-default-file
  (let [{:keys [by-path]} (dtcg (assoc fixture/catalog :themes []))]
    (is (contains? by-path "tokens/default.tokens.json"))))

(deftest a-group-with-an-empty-name-is-called-theme
  (let [c (assoc fixture/catalog :themes [{:group "" :name "Main Theme" :active true :sets ["core" "light"]}])]
    (is (contains? (:by-path (dtcg c)) "tokens/theme-main-theme.tokens.json"))))

(deftest tokens-keep-type-value-and-description-in-order
  (let [content (get (:by-path (dtcg fixture/catalog)) light)]
    (is (str/includes? content "\"$type\": \"dimension\",\n      \"$value\": {\n        \"value\": 4,\n        \"unit\": \"px\"\n      },\n      \"$description\": \"Base step\""))
    (is (str/starts-with? content "{\n  \"space\": {"))
    (is (str/ends-with? content "}\n"))))

(deftest colors-carry-srgb-components-alpha-and-hex
  (let [doc (parsed fixture/catalog light)]
    (is (= {"$type" "color"
            "$value" {"colorSpace" "srgb" "components" [(/ 51.0 255.0) (/ 102.0 255.0) 1.0] "alpha" 1.0 "hex" "#3366ff"}}
           (get-in doc ["color" "accent"])))
    (is (= "#ffffff" (get-in doc ["color" "bg" "$value" "hex"])))))

(deftest dimensions-numbers-weights-and-typography-use-their-dtcg-types
  (let [doc (parsed fixture/catalog light)]
    (is (= {"$type" "dimension" "$value" {"value" 8.0 "unit" "px"}} (get-in doc ["radius" "card"])))
    (is (= {"$type" "number" "$value" 0.5} (get-in doc ["opacity" "muted"])))
    (is (= {"$type" "fontWeight" "$value" 700.0} (get-in doc ["font" "weight" "strong"])))
    (is (= {"$type" "typography"
            "$value" {"fontFamily" ["Inter" "Segoe UI"] "fontSize" {"value" 16.0 "unit" "px"} "fontWeight" 400.0 "lineHeight" 1.5}}
           (get-in doc ["type" "body"])))))

(deftest shadows-are-arrays-of-layers
  (let [doc (parsed fixture/catalog light)]
    (is (= {"$type" "shadow"
            "$value" [{"color" {"colorSpace" "srgb" "components" [0.0 0.0 0.0] "alpha" 0.25 "hex" "#000000"}
                       "offsetX" {"value" 0.0 "unit" "px"}
                       "offsetY" {"value" 2.0 "unit" "px"}
                       "blur" {"value" 4.0 "unit" "px"}
                       "spread" {"value" 0.0 "unit" "px"}
                       "inset" false}]}
           (get-in doc ["shadow" "lift"])))))

(deftest percent-units-become-pixels-for-font-sizes-and-numbers-otherwise
  (let [model (raw (assoc (raw-token "size.font" {:kind :dimension :value 150.0 :unit "%"}) :type :font-size)
                   (assoc (raw-token "size.ratio" {:kind :dimension :value 50.0 :unit "%"}) :type :sizing))
        doc (as-doubles (first-doc model))]
    (is (= {"$type" "dimension" "$value" {"value" 24.0 "unit" "px"}} (get-in doc ["size" "font"])))
    (is (= {"$type" "number" "$value" 0.5} (get-in doc ["size" "ratio"])))))

(deftest text-tokens-have-no-type
  (let [doc (first-doc (raw (raw-token "label.title" {:kind :text :value "Hello"})))]
    (is (= {"$value" "Hello"} (get-in doc ["label" "title"])))))

(deftest library-colors-live-under-the-library-group
  (let [doc (parsed fixture/catalog light)]
    (is (= "#3366ff" (get-in doc ["library" "color" "Brand" "Primary" "$value" "hex"])))
    (is (= "color" (get-in doc ["library" "color" "Brand" "Primary" "$type"])))))

(deftest library-gradients-are-arrays-of-color-and-position
  (let [c (assoc fixture/catalog :colors [{:name "Fade" :gradient {:type "linear" :start-x 0 :start-y 0 :end-x 1 :end-y 0
                                                                   :stops [{:color "#000000" :opacity 1 :offset 0}
                                                                           {:color "#ffffff" :opacity 1 :offset 1}]}}])
        gradient (get-in (parsed c light) ["library" "color" "Fade"])]
    (is (= "gradient" (get gradient "$type")))
    (is (= [0.0 1.0] (map #(get % "position") (get gradient "$value"))))
    (is (= ["#000000" "#ffffff"] (map #(get-in % ["color" "hex"]) (get gradient "$value"))))))

(deftest values-differ-between-combinations
  (is (= "#ffffff" (get-in (parsed fixture/catalog light) ["color" "bg" "$value" "hex"])))
  (is (= "#111111" (get-in (parsed fixture/catalog "tokens/brand-a_mode-dark.tokens.json") ["color" "bg" "$value" "hex"])))
  (is (= "#ff3366" (get-in (parsed fixture/catalog "tokens/brand-b_mode-light.tokens.json") ["color" "accent" "$value" "hex"]))))

(deftest each-file-holds-the-tokens-of-its-own-combination
  (let [c (update-in fixture/catalog [:sets 3 :tokens] conj {:name "only.brand-a" :type :spacing :value "1"})
        {:keys [files problems]} (dtcg c)
        docs (into {} (map (fn [f] [(:path f) (json/read-str (:content f))])) files)]
    (is (= 4 (count docs)))
    (is (contains? (docs "tokens/brand-a_mode-light.tokens.json") "only"))
    (is (contains? (docs "tokens/brand-a_mode-dark.tokens.json") "only"))
    (is (not (contains? (docs "tokens/brand-b_mode-light.tokens.json") "only")))
    (is (not-any? #(= :not-in-every-combination (:code %)) problems))))

(deftest hostile-text-is-json-escaped-and-reserved-name-characters-are-replaced
  (let [model (raw (raw-token "$weird.{a}.b" {:kind :text :value "x\"y\\z\n</script>"} "d\"q"))
        content (:content (first (:files (run model))))
        doc (json/read-str content)]
    (is (= {"$value" "x\"y\\z\n</script>" "$description" "d\"q"} (get-in doc ["_weird" "_a_" "b"])))
    (is (str/includes? content "\"$value\": \"x\\\"y\\\\z\\n<\\/script>\""))
    (is (str/includes? content "\"$description\": \"d\\\"q\""))))

(deftest names-that-collide-after-cleaning-merge-only-when-values-agree
  (let [model (raw (raw-token "grp.{a}" {:kind :number :value 4.0})
                   (raw-token "grp._a_" {:kind :number :value 4.0})
                   (raw-token "grp.{b}" {:kind :number :value 1.0})
                   (raw-token "grp._b_" {:kind :number :value 2.0}))
        {:keys [files problems]} (run model)
        doc (json/read-str (:content (first files)))]
    (is (= 4 (get-in doc ["grp" "_a_" "$value"])))
    (is (not (contains? (get doc "grp") "_b_")))
    (is (= [{:code :name-collision :identifier "grp._b_" :tokens ["grp.{b}" "grp._b_"]}] problems))))

(deftest a-token-that-is-also-a-group-is-reported-and-dropped
  (let [model (raw (raw-token "space" {:kind :number :value 1.0})
                   (raw-token "space.base" {:kind :number :value 4.0}))
        {:keys [files problems]} (run model)
        doc (json/read-str (:content (first files)))]
    (is (= 4 (get-in doc ["space" "base" "$value"])))
    (is (= [{:code :name-collision :identifier "space" :tokens ["space"]}] problems))))
