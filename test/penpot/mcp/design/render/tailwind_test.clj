(ns penpot.mcp.design.render.tailwind-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.render.tailwind :as tailwind]))

(defn- rendered
  ([options] (rendered fixture/catalog options))
  ([catalog options]
   (let [{:keys [files problems]} (tailwind/render (fixture/model catalog) options)]
     {:files (into {} (map (juxt :path :content)) files) :problems problems})))

(deftest version-4-declares-tailwind-namespaces-in-a-theme-block
  (let [{:keys [files]} (rendered {})
        content (get files "tokens.css")]
    (is (= ["tokens.css"] (keys files)))
    (is (str/starts-with? content "@theme {\n  --spacing-base: 4px;\n  --radius-card: 8px;\n  --font-weight-strong: 700;\n"))
    (doseq [line ["  --text-type-body: 16px;" "  --text-type-body--line-height: 1.5;"
                  "  --text-type-body--font-weight: 400;" "  --font-type-body: Inter, \"Segoe UI\";"
                  "  --shadow-lift: 0px 2px 4px 0px rgba(0, 0, 0, 0.25);"
                  "  --color-bg: #ffffff;" "  --color-accent: #3366ff;"
                  "  --color-brand-primary: #3366ff;"]]
      (is (str/includes? content (str line "\n")) line))
    (is (str/includes? content "}\n\n:root {\n  --opacity-muted: 0.5;\n}\n"))))

(deftest the-prefix-follows-the-namespace
  (let [content (get (:files (rendered {:prefix "sv"})) "tokens.css")]
    (is (str/includes? content "  --spacing-sv-base: 4px;"))
    (is (str/includes? content "  --text-sv-type-body--line-height: 1.5;"))
    (is (str/includes? content ":root {\n  --sv-opacity-muted: 0.5;\n}"))))

(deftest gradients-are-plain-root-variables
  (let [c (assoc fixture/catalog :colors [{:name "Hero" :path "Brand" :opacity 1
                                           :gradient {:type "linear" :start-x 0 :start-y 0 :end-x 1 :end-y 1
                                                      :stops [{:color "#000000" :opacity 1 :offset 0} {:color "#ffffff" :opacity 1 :offset 1}]}}])
        content (get (:files (rendered c {:prefix "sv"})) "tokens.css")]
    (is (re-find #"(?m)^  --sv-gradient-brand-hero: linear-gradient\(" content))
    (is (not (str/includes? content "--color-sv-brand-hero")))))

(deftest other-combinations-override-only-what-differs
  (let [content (get (:files (rendered {})) "tokens.css")]
    (is (str/includes? content "\n:root:is([data-brand=\"a\"], :not([data-brand]))[data-mode=\"dark\"] {\n  --color-bg: #111111;\n}\n"))
    (is (str/includes? content "\n:root[data-brand=\"b\"]:is([data-mode=\"light\"], :not([data-mode])) {\n  --color-accent: #ff3366;\n}\n"))
    (is (str/includes? content "\n:root[data-brand=\"b\"][data-mode=\"dark\"] {\n  --color-bg: #111111;\n  --color-accent: #ff3366;\n}\n"))
    (is (not (str/includes? content "@custom-variant")))))

(deftest a-color-scheme-group-adds-the-dark-variant
  (let [content (get (:files (rendered {:color-scheme-group "mode"})) "tokens.css")]
    (is (str/ends-with? content "@custom-variant dark (&:where([data-mode=dark], [data-mode=dark] *));\n"))))

(deftest names-that-collide-merge-only-when-values-agree
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj
                     {:name "spacing.base" :type :spacing :value "4"}
                     {:name "radii.card" :type :border-radius :value "3"})
        {:keys [files problems]} (rendered c {})
        content (get files "tokens.css")]
    (is (= 1 (count (re-seq #"--spacing-base:" content))))
    (is (not (str/includes? content "--radius-card")))
    (is (= [{:code :name-collision :identifier "radius/card" :tokens ["radius.card" "radii.card"]}] problems))))

(deftest a-namespace-word-alone-is-kept
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj {:name "shadow" :type :shadow :value [{:offset-x "0" :offset-y "1" :blur "1" :spread "0" :color "#000000" :inset false}]})]
    (is (str/includes? (get (:files (rendered c {})) "tokens.css") "  --shadow-shadow: 0px 1px 1px 0px #000000;"))))

(deftest text-that-could-break-css-is-quoted
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj {:name "font.family.odd" :type :font-family :value ["Evil\"; } body { x"]})
        content (get (:files (rendered c {})) "tokens.css")]
    (is (str/includes? content "--font-odd: \"Evil\\22 ; } body { x\";"))))

(deftest version-3-maps-the-theme-to-css-variables
  (let [{:keys [files]} (rendered {:version 3 :prefix "sv"})
        js (get files "tailwind.theme.js")]
    (is (= #{"tailwind.theme.js" "tokens.css"} (set (keys files))))
    (is (str/includes? (get files "tokens.css") ":root {\n  --sv-space-base: 4px;\n"))
    (is (str/starts-with? js "module.exports = {\n  theme: {\n    extend: {\n      colors: {\n"))
    (is (str/ends-with? js "\n    }\n  }\n};\n"))
    (doseq [block ["      colors: {\n        \"bg\": \"var(--sv-color-bg)\",\n"
                   "        \"brand\": {\n          \"primary\": \"var(--sv-library-color-brand-primary)\"\n        }\n"
                   "      spacing: {\n        \"base\": \"var(--sv-space-base)\"\n      }"
                   "      borderRadius: {\n        \"card\": \"var(--sv-radius-card)\"\n      }"
                   "      fontFamily: {\n        \"type\": {\n          \"body\": \"var(--sv-type-body-font-family)\"\n        }\n      }"
                   "          \"body\": [\"var(--sv-type-body-font-size)\", { lineHeight: \"var(--sv-type-body-line-height)\", fontWeight: \"var(--sv-type-body-font-weight)\" }]"
                   "      fontWeight: {\n        \"strong\": \"var(--sv-font-weight-strong)\"\n"
                   "      boxShadow: {\n        \"lift\": \"var(--sv-shadow-lift)\"\n"
                   "      opacity: {\n        \"muted\": \"var(--sv-opacity-muted)\"\n"]]
      (is (str/includes? js block) block))))

(deftest version-3-keeps-theme-variants-in-the-css-file
  (let [css (get (:files (rendered {:version 3 :prefix "sv"})) "tokens.css")]
    (is (str/includes? css ":root:is([data-brand=\"a\"], :not([data-brand]))[data-mode=\"dark\"] {\n  --sv-color-bg: #111111;\n}"))))

(deftest version-3-nests-a-token-that-is-also-a-parent-under-default
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj {:name "space.Base.wide" :type :spacing :value "9"})
        js (get (:files (rendered c {:version 3})) "tailwind.theme.js")]
    (is (str/includes? js "\"base\": {\n          \"DEFAULT\": \"var(--space-base)\",\n          \"wide\": \"var(--space-base-wide)\"\n        }"))))

(deftest version-3-quotes-hostile-keys
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj {:name "space.a\"b" :type :spacing :value "9"})
        js (get (:files (rendered c {:version 3})) "tailwind.theme.js")]
    (is (not (str/includes? js "a\"b")))
    (is (str/includes? js "\"a-b\": \"var(--space-a-b)\""))))

(deftest invalid-options-are-rejected
  (doseq [options [{:prefix "Bad"} {:prefix "-x"} {:version 5} {:version "4"} {:version 3 :prefix "A"}]]
    (is (= :penpot.mcp.design.render/invalid-option
           (:type (ex-data (try (render/render (fixture/model) :tailwind options) (catch Exception e e))))))))

(deftest theme-names-cannot-break-out-of-a-selector
  (let [c       (assoc-in fixture/catalog [:themes 1 :name] "x\n}\nbody{background:red}")
        content (get (:files (rendered c {})) "tokens.css")]
    (is (not (re-find #"(?m)^body\{" content)))))

(deftest version-3-reports-two-tokens-that-share-a-config-key
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj {:name "colors.primary" :type :color :value "#000000"}
                     {:name "color.primary" :type :color :value "#ffffff"})
        {:keys [problems]} (rendered c {:version 3})]
    (is (some #{{:code :name-collision :identifier "colors.primary" :tokens ["colors.primary" "color.primary"]}} problems))))
