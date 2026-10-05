(ns penpot.mcp.design.render.kotlin-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.render.kotlin :as kotlin]))

(def ^:private options {:package "com.acme.ds" :type-name "Tokens"})

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
  (let [{:keys [files problems]} (kotlin/render (fixture/model catalog) options)]
    {:lines (set (str/split-lines (:content (first files)))) :content (:content (first files)) :problems problems :files files}))

(deftest the-file-is-named-after-the-type-and-uses-the-package
  (let [{:keys [files content]} (lines fixture/catalog)]
    (is (= ["Tokens.kt"] (map :path files)))
    (is (str/starts-with? content "package com.acme.ds\n\n"))
    (is (not (str/includes? content "import ")))))

(deftest every-uniform-token-becomes-a-typed-property
  (let [{:keys [lines]} (lines fixture/catalog)]
    (doseq [line ["data class Tokens("
                  "    val space: TokensSpace,"
                  "    val color: TokensColor,"
                  "    val library: TokensLibrary,"
                  "data class TokensSpace("
                  "    val base: Float,"
                  "    val card: Float,"
                  "    val weightStrong: FontWeightToken,"
                  "    val body: TypographyToken,"
                  "    val lift: List<ShadowToken>,"
                  "    val muted: Float,"
                  "    val bg: Long,"
                  "    val colorBrandPrimary: Long,"]]
      (is (contains? lines line) line))))

(deftest values-use-argb-long-literals-and-float-units
  (let [{:keys [lines]} (lines fixture/catalog)]
    (doseq [line ["        space = TokensSpace("
                  "            base = 4.0f,"
                  "            card = 8.0f,"
                  "            bg = 0xFFFFFFFF,"
                  "            accent = 0xFF3366FF,"
                  "            muted = 0.5f,"
                  "            colorBrandPrimary = 0xFF3366FF,"
                  "            weightStrong = FontWeightToken(weight = 700, italic = true),"
                  "            lift = listOf(ShadowToken(offsetX = 0.0f, offsetY = 2.0f, blur = 4.0f, spread = 0.0f, color = 0x40000000L, inset = false)),"
                  "            body = TypographyToken(fontFamily = listOf(\"Inter\", \"Segoe UI\"), fontSize = 16.0f, fontWeight = FontWeightToken(weight = 400, italic = false), lineHeight = 1.5f, letterSpacing = null, textCase = null, textDecoration = null),"]]
      (is (contains? lines line) line))))

(deftest only-the-helper-classes-in-use-are-declared
  (let [{:keys [content]} (lines fixture/catalog)]
    (is (str/includes? content "data class FontWeightToken(val weight: Int, val italic: Boolean)"))
    (is (str/includes? content "data class TypographyToken("))
    (is (str/includes? content "data class ShadowToken("))
    (is (not (str/includes? content "GradientToken")))))

(deftest themes-hold-one-instance-per-combination-and-select-picks-by-group
  (let [{:keys [lines content]} (lines fixture/catalog)]
    (is (contains? lines "object TokensThemes {"))
    (is (contains? lines "    val brandAModeLight: Tokens = Tokens("))
    (is (contains? lines "    val brandBModeDark: Tokens = Tokens("))
    (is (contains? lines "    val default: Tokens = brandAModeLight"))
    (is (contains? lines "    fun select(brand: String, mode: String): Tokens = when {"))
    (is (contains? lines "        brand == \"b\" && mode == \"dark\" -> brandBModeDark"))
    (is (contains? lines "        else -> brandAModeLight"))
    (is (= 4 (count (re-seq #"bg = " content))))))

(deftest themes-without-a-group-name-use-the-theme-parameter
  (let [c (assoc fixture/catalog :themes [{:group "" :name "light" :active true :sets ["core" "light"]}
                                          {:group "" :name "dark" :active false :sets ["core" "dark"]}])
        {:keys [lines]} (lines c)]
    (is (contains? lines "    val light: Tokens = Tokens("))
    (is (contains? lines "    fun select(theme: String): Tokens = when {"))
    (is (contains? lines "        theme == \"dark\" -> dark"))))

(deftest rem-and-percent-units-become-floats
  (let [{:keys [files problems]} (kotlin/render (synthetic-model ["spaceRem" :spacing {:kind :dimension :value 1.5 :unit "rem"}] ["fontSizePct" :font-size {:kind :dimension :value 150.0 :unit "%"}]
                                                                 ["fontSizeRem" :font-size {:kind :dimension :value 2.0 :unit "rem"}] ["spaceHalf" :spacing {:kind :dimension :value 50.0 :unit "%"}])
                                                options)
        lines (set (str/split-lines (:content (first files))))]
    (is (contains? lines "        spaceRem = 24.0f,"))
    (is (contains? lines "        fontSizePct = 24.0f,"))
    (is (contains? lines "        fontSizeRem = 32.0f,"))
    (is (not (str/includes? (:content (first files)) "spaceHalf")))
    (is (= [{:code :unsupported-unit :token "spaceHalf" :platform :kotlin}] problems))
    (is (contains? lines "    val default: Tokens = base"))
    (is (contains? lines "    fun select(): Tokens = base"))))

(deftest strings-are-escaped-for-kotlin
  (let [{:keys [files]} (kotlin/render (synthetic-model ["textOdd" :string {:kind :text :value "a\"b\\c$d\ne"}]
                                                        ["fontFamilyOdd" :font-family {:kind :font-family :families ["Ev\"il"]}])
                                       options)
        lines (set (str/split-lines (:content (first files))))]
    (is (contains? lines "        textOdd = \"a\\\"b\\\\c\\$d\\ne\","))
    (is (contains? lines "        fontFamilyOdd = listOf(\"Ev\\\"il\"),"))))

(deftest hard-keywords-are-wrapped-in-backticks
  (let [c (extra {:name "object" :type :spacing :value "1"})
        {:keys [lines]} (lines c)]
    (is (contains? lines "    val `object`: Float,"))
    (is (contains? lines "        `object` = 1.0f,"))))

(deftest identifiers-that-collide-merge-only-when-values-agree
  (let [c (extra {:name "space.BASE" :type :spacing :value "4"} {:name "radius.CARD" :type :border-radius :value "3"})
        {:keys [content problems]} (lines c)]
    (is (= 1 (count (re-seq #"val base: Float" content))))
    (is (not (str/includes? content "card")))
    (is (= [{:code :name-collision :identifier "card" :tokens ["radius.card" "radius.CARD"]}] problems))))

(deftest groups-that-collide-after-camel-casing-share-one-class
  (let [c (extra {:name "Color.border" :type :color :value "#000000"})
        {:keys [content]} (lines c)]
    (is (= 1 (count (re-seq #"data class TokensColor\(" content))))
    (is (str/includes? content "    val border: Long,"))))

(deftest library-gradients-use-a-gradient-token
  (let [g {:name "Sky" :path "Brand" :gradient {:type "linear" :start-x 0 :start-y 0 :end-x 1 :end-y 0
                                                :stops [{:color "#000000" :opacity 1 :offset 0}
                                                        {:color "#ffffff" :opacity 0.5 :offset 1}]}}
        {:keys [lines content]} (lines (update fixture/catalog :colors conj g))]
    (is (str/includes? content "data class GradientStop(val color: Long, val position: Float)"))
    (is (contains? lines "    val colorBrandSky: GradientToken,"))
    (is (contains? lines "            colorBrandSky = GradientToken(linear = true, angle = 90.0f, stops = listOf(GradientStop(color = 0xFF000000, position = 0.0f), GradientStop(color = 0x80FFFFFF, position = 1.0f))),"))))

(deftest output-is-deterministic
  (is (= (kotlin/render (fixture/model) options) (kotlin/render (fixture/model) options))))

(deftest invalid-options-are-rejected
  (doseq [o [{:type-name "Tokens"} {:package "Com.Acme" :type-name "Tokens"} {:package "com..acme" :type-name "Tokens"}
             {:package "com.acme"} {:package "com.acme" :type-name "tokens"} {:package "com.acme" :type-name "A-B"}]]
    (is (= :penpot.mcp.design.render/invalid-option
           (:type (try (render/render (fixture/model) :kotlin o) (catch clojure.lang.ExceptionInfo e (ex-data e))))))))

(deftest translucent-colors-are-long-literals
  (let [c (update-in fixture/catalog [:sets 0 :tokens] conj {:name "color.veil" :type :color :value "rgba(#000000, 0.2)"})
        content (:content (first (:files (kotlin/render (fixture/model c) {:package "com.acme" :type-name "Tokens"}))))]
    (is (re-find #"            veil = 0x33000000L," content))
    (is (re-find #"            accent = 0xFF3366FF," content))))

(deftest a-catalog-without-tokens-still-compiles
  (let [empty-catalog {:sets [] :themes [] :colors [] :typographies [] :warnings []}
        content (:content (first (:files (kotlin/render (fixture/model empty-catalog) {:package "com.acme" :type-name "Tokens"}))))]
    (is (str/includes? content "\nclass Tokens\n"))
    (is (str/includes? content "val base: Tokens = Tokens()"))
    (is (not (str/includes? content "data class Tokens(")))))

(defn- pathed-model [tokens]
  {:combinations [{:id "default"
                   :themes {}
                   :default? true
                   :tokens (mapv (fn [[path type value]] {:name (str/join "." path) :path path :type type :value value}) tokens)}]
   :uniform (mapv (fn [[path]] (str/join "." path)) tokens)
   :library {:colors [] :typographies []}
   :problems []})

(defn- colors-in [groups n]
  (pathed-model (for [i (range n)]
                  [[(groups i) (str "c" i)] :color {:kind :color :rgba {:r 0 :g 0 :b 0 :a 1.0}}])))

(defn- thrown [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e e)))

(deftest a-class-over-the-jvm-parameter-limit-is-rejected
  (let [e (thrown #(kotlin/render (colors-in (constantly "color") 128) options))]
    (is (= {:type :penpot.mcp.design.render/too-many-fields :class "TokensColor" :slots 256} (ex-data e)))
    (is (str/includes? (ex-message e) "TokensColor"))))

(deftest a-class-exactly-at-the-limit-is-accepted
  (is (some? (kotlin/render (colors-in (constantly "color") 127) options))))

(deftest tokens-spread-over-groups-stay-under-the-limit
  (let [content (:content (first (:files (kotlin/render (colors-in #(str "group" (mod % 5)) 130) options))))]
    (is (str/includes? content "data class TokensGroup0("))
    (is (str/includes? content "    val group4: TokensGroup4,"))))

(deftest keyword-theme-names-and-groups-are-escaped-in-every-position
  (let [c (assoc fixture/catalog :themes [{:group "" :name "in" :active true :sets ["core" "light"]}
                                          {:group "" :name "object" :active false :sets ["core" "dark"]}])
        {:keys [lines]} (lines c)]
    (is (contains? lines "    val `in`: Tokens = Tokens("))
    (is (contains? lines "    val `object`: Tokens = Tokens("))
    (is (contains? lines "    val default: Tokens = `in`"))
    (is (contains? lines "        theme == \"object\" -> `object`"))
    (is (contains? lines "        else -> `in`"))))

(deftest unusable-identifiers-drop-the-token-with-a-problem
  (let [{:keys [files problems]} (kotlin/render (pathed-model [[["--"] :spacing {:kind :dimension :value 1.0 :unit "px"}]
                                                               [["grp" "_"] :spacing {:kind :dimension :value 1.0 :unit "px"}]
                                                               [["ok"] :spacing {:kind :dimension :value 1.0 :unit "px"}]])
                                                options)]
    (is (= [{:code :invalid-identifier :token "--" :platform :kotlin}
            {:code :invalid-identifier :token "grp._" :platform :kotlin}]
           problems))
    (is (not (str/includes? (:content (first files)) "TokensGrp")))
    (is (str/includes? (:content (first files)) "    val ok: Float,"))))

(deftest type-names-clashing-with-generated-classes-are-rejected
  (doseq [[name model] [["FontWeightToken" (fixture/model)]
                        ["Tokens" (pathed-model [[["themes" "x"] :spacing {:kind :dimension :value 1.0 :unit "px"}]])]]]
    (let [e (thrown #(kotlin/render model {:package "com.acme" :type-name name}))]
      (is (= :penpot.mcp.design.render/invalid-option (:type (ex-data e))))
      (is (str/includes? (ex-message e) (if (= name "Tokens") "TokensThemes" name))))))

(deftest a-model-without-combinations-is-rejected
  (let [e (thrown #(kotlin/render (assoc (fixture/model) :combinations []) options))]
    (is (= :penpot.mcp.design.render/no-combinations (:type (ex-data e))))
    (is (= "No theme combination could be resolved" (ex-message e)))))
