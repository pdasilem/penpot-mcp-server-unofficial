(ns penpot.mcp.design.render.css-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.fixture :as fixture]))

(defn- css [options]
  (let [{:keys [files problems]} (render/render (fixture/model) :css options)]
    {:content (:content (first files)) :path (:path (first files)) :problems problems}))

(deftest the-default-combination-goes-to-root
  (let [{:keys [content path]} (css {:prefix "sv"})]
    (is (= "tokens.css" path))
    (is (str/starts-with? content ":root {\n"))
    (doseq [line ["  --sv-space-base: 4px;" "  --sv-radius-card: 8px;" "  --sv-font-weight-strong: 700;"
                  "  --sv-font-weight-strong-style: italic;" "  --sv-type-body-font-family: Inter, \"Segoe UI\";"
                  "  --sv-type-body-font-size: 16px;" "  --sv-type-body-line-height: 1.5;" "  --sv-type-body-font-weight: 400;"
                  "  --sv-shadow-lift: 0px 2px 4px 0px rgba(0, 0, 0, 0.25);" "  --sv-opacity-muted: 0.5;"
                  "  --sv-color-bg: #ffffff;" "  --sv-color-accent: #3366ff;" "  --sv-library-color-brand-primary: #3366ff;"]]
      (is (str/includes? content line) line))))

(deftest other-combinations-list-only-what-differs
  (let [{:keys [content]} (css {:prefix "sv"})]
    (is (str/includes? content ":root:is([data-brand=\"a\"], :not([data-brand]))[data-mode=\"dark\"] {\n  --sv-color-bg: #111111;\n}"))
    (is (str/includes? content ":root[data-brand=\"b\"]:is([data-mode=\"light\"], :not([data-mode])) {\n  --sv-color-accent: #ff3366;\n}"))
    (is (not (str/includes? content "--sv-color-text: #111111;\n}\n:root[")))))

(deftest the-color-scheme-group-follows-the-system-setting
  (let [{:keys [content]} (css {:prefix "sv" :color-scheme-group "mode"})]
    (is (str/includes? content "@media (prefers-color-scheme: dark) {\n:root:not([data-mode]):is([data-brand=\"a\"], :not([data-brand])) {\n  --sv-color-bg: #111111;\n}\n}"))))

(deftest without-a-prefix-names-start-with-the-token-path
  (is (str/includes? (:content (css {})) "  --space-base: 4px;")))

(deftest names-that-collide-merge-only-when-values-agree
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj
                     {:name "space-base" :type :spacing :value "4"}
                     {:name "radius-card" :type :border-radius :value "3"})
        {:keys [files problems]} (render/render (fixture/model c) :css {:prefix "sv"})
        content (:content (first files))]
    (is (str/includes? content "--sv-space-base: 4px;"))
    (is (not (str/includes? content "--sv-radius-card")))
    (is (= [{:code :name-collision :identifier "radius-card" :tokens ["radius.card" "radius-card"]}] problems))))

(deftest text-that-could-break-css-is-quoted
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj {:name "font.family.odd" :type :font-family :value ["Evil\"; } body { x"]})
        content (:content (first (:files (render/render (fixture/model c) :css {}))))]
    (is (str/includes? content "--font-family-odd: \"Evil\\22 ; } body { x\";"))))

(deftest theme-names-cannot-break-out-of-a-selector
  (let [c       (assoc-in fixture/catalog [:themes 1 :name] "x\n}\nbody{background:red}\n:root{\f")
        content (:content (first (:files (render/render (fixture/model c) :css {}))))]
    (is (str/includes? content "[data-mode=\"x\\a }\\a body{background:red}\\a :root{\\c \"]"))
    (is (not (re-find #"(?m)^body\{" content)))))

(deftest the-css-prefix-is-checked-like-the-other-css-formats
  (doseq [prefix ["Bad" (apply str (repeat 32 "a"))]]
    (is (= :penpot.mcp.design.render/invalid-option
           (:type (ex-data (try (render/render (fixture/model) :css {:prefix prefix}) (catch Exception e e))))))))

(deftest an-unknown-color-scheme-group-is-reported
  (is (some #{{:code :unknown-color-scheme-group :group "nope"}}
            (:problems (render/render (fixture/model) :css {:color-scheme-group "nope"})))))
