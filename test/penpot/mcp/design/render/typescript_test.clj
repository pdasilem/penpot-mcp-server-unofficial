(ns penpot.mcp.design.render.typescript-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.render.typescript :as typescript]))

(defn- ts [c]
  (let [{:keys [files problems]} (typescript/render (fixture/model c) {})]
    {:files files :content (:content (first files)) :problems problems}))

(defn- raw [& tokens]
  {:combinations [{:id "default" :themes {} :default? true :tokens (vec tokens)}]
   :uniform (mapv :name tokens)
   :library {:colors [] :typographies []}})

(defn- raw-token [name value]
  {:name name :path (str/split name #"\.") :type :other :value value})

(defn- raw-content [model]
  (let [{:keys [files problems]} (typescript/render model {})]
    {:content (:content (first files)) :problems problems}))

(defn- with-tokens [& tokens]
  (update-in fixture/catalog [:sets 0 :tokens] into tokens))

(deftest renders-one-tokens-file-with-a-block-per-combination
  (let [{:keys [files content problems]} (ts fixture/catalog)]
    (is (= ["tokens.ts"] (map :path files)))
    (is (= [] problems))
    (is (str/starts-with? content "export const themes = {\n"))
    (doseq [id ["brand=a;mode=light" "brand=a;mode=dark" "brand=b;mode=light" "brand=b;mode=dark"]]
      (is (str/includes? content (str "  \"" id "\": {\n")) id))
    (doseq [line ["} as const;"
                  "export const defaultTheme = \"brand=a;mode=light\";"
                  "export type ThemeId = keyof typeof themes;"
                  "export type Tokens = (typeof themes)[ThemeId];"]]
      (is (str/includes? content line) line))))

(deftest values-keep-their-typescript-shapes
  (let [{:keys [content]} (ts fixture/catalog)]
    (doseq [line ["      base: \"4px\","
                  "      card: \"8px\","
                  "        strong: { weight: 700, italic: true },"
                  "      body: { fontFamily: \"Inter, \\\"Segoe UI\\\"\", fontSize: \"16px\", fontWeight: { weight: 400, italic: false }, lineHeight: 1.5 },"
                  "      lift: \"0px 2px 4px 0px rgba(0, 0, 0, 0.25)\","
                  "      muted: 0.5,"
                  "      accent: \"#3366ff\","
                  "          primary: \"#3366ff\","]]
      (is (str/includes? content line) line))))

(deftest every-combination-has-the-same-keys
  (let [content (:content (ts (with-tokens {:name "extra.only" :type :spacing :value "1"})))
        blocks  (rest (str/split content #"\n  \"brand="))
        keys-of (fn [block] (vec (re-seq #"\n\s+[A-Za-z_0-9]+: [{\"0-9]" block)))]
    (is (= 4 (count blocks)))
    (is (apply = (map keys-of blocks)))
    (is (every? #(str/includes? % "only: \"1px\",") blocks))))

(deftest tokens-that-exist-in-only-some-combinations-are-left-out
  (let [c (update-in fixture/catalog [:sets 3 :tokens] conj {:name "only.brand-a" :type :spacing :value "1"})
        {:keys [content]} (ts c)]
    (is (not (str/includes? content "onlyBrandA")))
    (is (not (str/includes? content "only:")))
    (is (some #{{:code :not-in-every-combination :token "only.brand-a"}}
              (:problems (typescript/render (fixture/model c) {}))))))

(deftest segments-are-camel-cased
  (let [{:keys [content]} (ts (with-tokens {:name "Border-Width.thick line" :type :spacing :value "2"}))]
    (is (str/includes? content "    borderWidth: {\n      thickLine: \"2px\","))))

(deftest hostile-strings-are-escaped-as-string-literals
  (let [model (raw (raw-token "font.family.odd" {:kind :font-family :families ["Evil\"; } x\n\\ </script>"]})
                   (raw-token "label.odd" {:kind :text :value "a\"b\\c\nd"}))
        {:keys [content]} (raw-content model)]
    (is (str/includes? content "      odd: \"\\\"Evil\\\\22 ; } x\\\\a \\\\5c  <\\/script>\\\"\","))
    (is (str/includes? content "      odd: \"a\\\"b\\\\c\\nd\","))
    (is (not (str/includes? content "c\nd")))))

(deftest names-that-collide-merge-only-when-values-agree
  (let [model (raw (raw-token "space.base" {:kind :number :value 4.0})
                   (raw-token "space.Base" {:kind :number :value 4.0})
                   (raw-token "radius.card" {:kind :number :value 3.0})
                   (raw-token "radius.Card" {:kind :number :value 5.0}))
        {:keys [content problems]} (raw-content model)]
    (is (str/includes? content "      base: 4,"))
    (is (not (str/includes? content "card:")))
    (is (= [{:code :name-collision :identifier "radius.card" :tokens ["radius.card" "radius.Card"]}] problems))))

(deftest a-token-that-is-also-a-group-is-reported-and-dropped
  (let [model (raw (raw-token "space" {:kind :number :value 1.0})
                   (raw-token "space.base" {:kind :number :value 4.0}))
        {:keys [content problems]} (raw-content model)]
    (is (str/includes? content "    space: {\n      base: 4,"))
    (is (= [{:code :name-collision :identifier "space" :tokens ["space"]}] problems))))

(deftest a-model-without-combinations-renders-nothing
  (let [{:keys [files problems]} (typescript/render {:combinations []} {})]
    (is (= [] files))
    (is (= [{:code :no-combinations}] problems))))
