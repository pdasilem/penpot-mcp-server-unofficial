(ns penpot.mcp.design.render.scss-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.render.scss :as scss]))

(defn- scss-of
  ([options] (scss-of fixture/catalog options))
  ([catalog options]
   (let [{:keys [files problems]} (scss/render (fixture/model catalog) options)]
     {:path (:path (first files)) :content (:content (first files)) :problems problems})))

(deftest the-default-combination-becomes-variables
  (let [{:keys [content path]} (scss-of {:prefix "sv"})]
    (is (= "_tokens.scss" path))
    (is (str/starts-with? content "$sv-space-base: 4px;\n$sv-radius-card: 8px;\n"))
    (doseq [line ["$sv-font-weight-strong: 700;" "$sv-font-weight-strong-style: italic;"
                  "$sv-type-body-font-family: Inter, \"Segoe UI\";" "$sv-type-body-font-size: 16px;"
                  "$sv-type-body-line-height: 1.5;" "$sv-shadow-lift: 0px 2px 4px 0px rgba(0, 0, 0, 0.25);"
                  "$sv-opacity-muted: 0.5;" "$sv-color-bg: #ffffff;" "$sv-color-accent: #3366ff;"
                  "$sv-library-color-brand-primary: #3366ff;"]]
      (is (some #{line} (str/split-lines content)) line))))

(deftest every-combination-becomes-a-map-entry
  (let [{:keys [content]} (scss-of {:prefix "sv"})]
    (is (str/includes? content "$sv-themes: (\n  \"brand=a;mode=light\": (\n    \"space-base\": 4px,\n"))
    (is (str/includes? content "  \"brand=b;mode=dark\": (\n"))
    (is (str/includes? content "  \"brand=a;mode=dark\": (\n    \"space-base\": 4px,"))
    (is (str/includes? content "    \"color-bg\": #111111,\n    \"color-text\": #111111,\n    \"color-accent\": #3366ff,\n"))
    (is (str/includes? content "    \"color-accent\": #ff3366,\n"))
    (is (str/ends-with? content "  )\n);\n"))))

(deftest comma-separated-values-are-wrapped-in-map-entries
  (let [{:keys [content]} (scss-of {:prefix "sv"})]
    (is (str/includes? content "    \"type-body-font-family\": (Inter, \"Segoe UI\"),\n"))
    (is (str/includes? content "    \"shadow-lift\": 0px 2px 4px 0px rgba(0, 0, 0, 0.25),\n"))))

(deftest without-a-prefix-names-start-with-the-token-path
  (let [{:keys [content]} (scss-of {})]
    (is (str/includes? content "$space-base: 4px;"))
    (is (str/includes? content "$themes: ("))))

(deftest names-that-collide-merge-only-when-values-agree
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj
                     {:name "space-base" :type :spacing :value "4"}
                     {:name "radius-card" :type :border-radius :value "3"})
        {:keys [content problems]} (scss-of c {:prefix "sv"})]
    (is (= 1 (count (re-seq #"\$sv-space-base: " content))))
    (is (not (str/includes? content "radius-card")))
    (is (= [{:code :name-collision :identifier "radius-card" :tokens ["radius.card" "radius-card"]}] problems))))

(deftest hostile-text-is-escaped
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj
                     {:name "font.family.odd" :type :font-family :value ["Evil\"; } body { x" "x\\y"]})
        {:keys [content]} (scss-of c {})]
    (is (str/includes? content "$font-family-odd: \"Evil\\22 ; } body { x\", \"x\\5c y\";"))
    (is (str/includes? content "\"font-family-odd\": (\"Evil\\22 ; } body { x\", \"x\\5c y\"),"))))

(deftest an-invalid-prefix-is-rejected
  (doseq [prefix ["Bad" "1x" "a b" ""]]
    (is (= :penpot.mcp.design.render/invalid-option
           (:type (ex-data (try (render/render (fixture/model) :scss {:prefix prefix}) (catch Exception e e))))))))
