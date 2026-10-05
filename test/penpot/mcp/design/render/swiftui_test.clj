(ns penpot.mcp.design.render.swiftui-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.render.swiftui :as swiftui]))

(def ^:private options {:type-name "Tokens"})

(defn- extra [& tokens]
  (update-in fixture/catalog [:sets 0 :tokens] into tokens))

(defn- synthetic-model [& tokens]
  {:combinations [{:id "default"
                   :themes {}
                   :default? true
                   :tokens (mapv (fn [[name type value]] {:name name :path [name] :type type :value value}) tokens)}]
   :uniform (mapv first tokens)
   :library {:colors [] :typographies []}
   :problems []})

(defn- lines [catalog]
  (let [{:keys [files problems]} (swiftui/render (fixture/model catalog) options)]
    {:lines (set (str/split-lines (:content (first files)))) :content (:content (first files)) :problems problems :files files}))

(deftest the-file-is-named-after-the-type-and-imports-swiftui
  (let [{:keys [files content]} (lines fixture/catalog)]
    (is (= ["Tokens.swift"] (map :path files)))
    (is (str/starts-with? content "import SwiftUI\n\n"))))

(deftest every-uniform-token-becomes-a-typed-property
  (let [{:keys [lines]} (lines fixture/catalog)]
    (doseq [line ["struct Tokens {"
                  "    let space: TokensSpace"
                  "    let color: TokensColor"
                  "    let library: TokensLibrary"
                  "struct TokensSpace {"
                  "    let base: CGFloat"
                  "    let weightStrong: FontWeightToken"
                  "    let body: TypographyToken"
                  "    let lift: [ShadowToken]"
                  "    let bg: Color"
                  "    let colorBrandPrimary: Color"]]
      (is (contains? lines line) line))))

(deftest colors-are-srgb-components-rounded-to-four-decimals
  (let [{:keys [lines]} (lines fixture/catalog)]
    (doseq [line ["            bg: Color(.sRGB, red: 1.0, green: 1.0, blue: 1.0, opacity: 1.0),"
                  "            text: Color(.sRGB, red: 0.0667, green: 0.0667, blue: 0.0667, opacity: 1.0),"
                  "            accent: Color(.sRGB, red: 0.2, green: 0.4, blue: 1.0, opacity: 1.0)"
                  "            base: 4.0"
                  "            muted: 0.5"
                  "            weightStrong: FontWeightToken(weight: 700, italic: true)"
                  "            lift: [ShadowToken(offsetX: 0.0, offsetY: 2.0, blur: 4.0, spread: 0.0, color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 0.25), inset: false)]"
                  "            body: TypographyToken(fontFamily: [\"Inter\", \"Segoe UI\"], fontSize: 16.0, fontWeight: FontWeightToken(weight: 400, italic: false), lineHeight: 1.5, letterSpacing: nil, textCase: nil, textDecoration: nil)"]]
      (is (contains? lines line) line))))

(deftest combinations-are-static-members-and-standard-is-the-default
  (let [{:keys [lines content]} (lines fixture/catalog)]
    (is (contains? lines "extension Tokens {"))
    (is (contains? lines "    static let brandAModeLight = Tokens("))
    (is (contains? lines "    static let brandBModeDark = Tokens("))
    (is (contains? lines "    static let standard = brandAModeLight"))
    (is (not (str/includes? content "static let default ")))
    (is (= 4 (count (re-seq #"bg: Color\(" content))))))

(deftest the-environment-key-and-value-are-declared
  (let [{:keys [lines]} (lines fixture/catalog)]
    (doseq [line ["struct TokensKey: EnvironmentKey {"
                  "    static let defaultValue = Tokens.standard"
                  "extension EnvironmentValues {"
                  "    var tokens: Tokens {"
                  "        get { self[TokensKey.self] }"
                  "        set { self[TokensKey.self] = newValue }"]]
      (is (contains? lines line) line))))

(deftest the-environment-property-is-camel-cased-from-the-type-name
  (let [{:keys [files]} (swiftui/render (fixture/model) {:type-name "SayVibeTokens"})]
    (is (str/includes? (:content (first files)) "    var sayVibeTokens: SayVibeTokens {"))))

(deftest only-the-helper-structs-in-use-are-declared
  (let [{:keys [content]} (lines fixture/catalog)]
    (is (str/includes? content "struct FontWeightToken {"))
    (is (str/includes? content "struct TypographyToken {"))
    (is (str/includes? content "struct ShadowToken {"))
    (is (not (str/includes? content "GradientToken")))))

(deftest rem-and-percent-units-become-floats
  (let [{:keys [files problems]} (swiftui/render (synthetic-model ["spaceRem" :spacing {:kind :dimension :value 1.5 :unit "rem"}] ["fontSizePct" :font-size {:kind :dimension :value 150.0 :unit "%"}]
                                                                  ["fontSizeRem" :font-size {:kind :dimension :value 2.0 :unit "rem"}] ["spaceHalf" :spacing {:kind :dimension :value 50.0 :unit "%"}])
                                                 options)
        lines (set (str/split-lines (:content (first files))))]
    (is (contains? lines "        spaceRem: 24.0,"))
    (is (contains? lines "        fontSizePct: 24.0,"))
    (is (contains? lines "        fontSizeRem: 32.0"))
    (is (not (str/includes? (:content (first files)) "spaceHalf")))
    (is (= [{:code :unsupported-unit :token "spaceHalf" :platform :swiftui}] problems))
    (is (contains? lines "    static let standard = base"))))

(deftest strings-never-keep-an-unescaped-interpolation-or-quote
  (let [{:keys [files]} (swiftui/render (synthetic-model ["textOdd" :string {:kind :text :value "a\"b\\c\\(d)\ne"}]) options)
        lines (set (str/split-lines (:content (first files))))]
    (is (contains? lines "        textOdd: \"a\\\"b\\\\c\\\\(d)\\ne\""))))

(deftest reserved-words-are-wrapped-in-backticks
  (let [c (extra {:name "default" :type :spacing :value "1"} {:name "class" :type :spacing :value "2"})
        {:keys [lines]} (lines c)]
    (is (contains? lines "    let `default`: CGFloat"))
    (is (contains? lines "        `class`: 2.0,"))))

(deftest identifiers-that-collide-are-dropped-with-a-problem
  (let [c (extra {:name "radius.CARD" :type :border-radius :value "3"})
        {:keys [content problems]} (lines c)]
    (is (not (str/includes? content "card")))
    (is (= [{:code :name-collision :identifier "card" :tokens ["radius.card" "radius.CARD"]}] problems))))

(deftest library-gradients-use-a-gradient-token
  (let [g {:name "Sky" :path "Brand" :gradient {:type "radial" :start-x 0.5 :start-y 0.5 :end-x 1 :end-y 0.5
                                                :stops [{:color "#000000" :opacity 1 :offset 0}
                                                        {:color "#ffffff" :opacity 1 :offset 1}]}}
        {:keys [lines content]} (lines (update fixture/catalog :colors conj g))]
    (is (str/includes? content "struct GradientStop {"))
    (is (contains? lines "    let colorBrandSky: GradientToken"))
    (is (contains? lines "            colorBrandSky: GradientToken(linear: false, angle: 0.0, stops: [GradientStop(color: Color(.sRGB, red: 0.0, green: 0.0, blue: 0.0, opacity: 1.0), position: 0.0), GradientStop(color: Color(.sRGB, red: 1.0, green: 1.0, blue: 1.0, opacity: 1.0), position: 1.0)])"))))

(deftest output-is-deterministic
  (is (= (swiftui/render (fixture/model) options) (swiftui/render (fixture/model) options))))

(deftest invalid-options-are-rejected
  (doseq [o [{} {:type-name "tokens"} {:type-name "A B"} {:type-name 3}]]
    (is (= :penpot.mcp.design.render/invalid-option
           (:type (try (render/render (fixture/model) :swiftui o) (catch clojure.lang.ExceptionInfo e (ex-data e))))))))

(defn- pathed-model [tokens]
  {:combinations [{:id "default"
                   :themes {}
                   :default? true
                   :tokens (mapv (fn [[path type value]] {:name (str/join "." path) :path path :type type :value value}) tokens)}]
   :uniform (mapv (fn [[path]] (str/join "." path)) tokens)
   :library {:colors [] :typographies []}
   :problems []})

(defn- thrown [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e e)))

(deftest nested-initializers-follow-the-group-structure-without-trailing-commas
  (let [{:keys [content]} (lines fixture/catalog)]
    (is (str/includes? content "    static let brandAModeLight = Tokens(\n        space: TokensSpace(\n            base: 4.0\n        ),\n        radius: TokensRadius("))
    (is (str/includes? content "        library: TokensLibrary(\n            colorBrandPrimary: Color(.sRGB, red: 0.2, green: 0.4, blue: 1.0, opacity: 1.0)\n        )\n    )\n"))))

(deftest the-exact-alpha-is-used-for-opacity
  (let [{:keys [content]} (lines fixture/catalog)]
    (is (str/includes? content "opacity: 0.25)"))
    (is (not (str/includes? content "opacity: 0.251")))))

(deftest keyword-theme-names-are-escaped-in-every-position
  (let [c (assoc fixture/catalog :themes [{:group "" :name "in" :active true :sets ["core" "light"]}
                                          {:group "" :name "object" :active false :sets ["core" "dark"]}])
        {:keys [lines]} (lines c)]
    (is (contains? lines "    static let `in` = Tokens("))
    (is (contains? lines "    static let standard = `in`"))))

(deftest unusable-identifiers-drop-the-token-with-a-problem
  (let [{:keys [files problems]} (swiftui/render (pathed-model [[["grp" "_"] :spacing {:kind :dimension :value 1.0 :unit "px"}]
                                                                [["ok"] :spacing {:kind :dimension :value 1.0 :unit "px"}]])
                                                 options)]
    (is (= [{:code :invalid-identifier :token "grp._" :platform :swiftui}] problems))
    (is (not (str/includes? (:content (first files)) "TokensGrp")))))

(deftest type-names-clashing-with-swiftui-types-or-generated-structs-are-rejected
  (doseq [name ["Color" "Font" "Text" "View" "Gradient" "Shadow" "Image" "Shape" "ShadowToken"]]
    (let [e (thrown #(swiftui/render (fixture/model) {:type-name name}))]
      (is (= :penpot.mcp.design.render/invalid-option (:type (ex-data e))) name)
      (is (str/includes? (ex-message e) name))))
  (let [e (thrown #(swiftui/render (pathed-model [[["key" "x"] :spacing {:kind :dimension :value 1.0 :unit "px"}]]) options))]
    (is (str/includes? (ex-message e) "TokensKey"))))

(deftest a-model-without-combinations-is-rejected
  (let [e (thrown #(swiftui/render (assoc (fixture/model) :combinations []) options))]
    (is (= :penpot.mcp.design.render/no-combinations (:type (ex-data e))))
    (is (= "No theme combination could be resolved" (ex-message e)))))
