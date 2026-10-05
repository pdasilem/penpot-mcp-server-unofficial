(ns penpot.mcp.tools.design-system-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.exports :as exports]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.design-system :as design-system])
  (:import
   (java.io ByteArrayInputStream)
   (java.util.zip ZipInputStream)))

(def ^:private editor-result
  {:tokens {:sets [{:id "s1" :name "core" :active true
                    :tokens [{:id "t1" :name "space.base" :type "spacing" :value "4"}
                             {:id "t2" :name "broken" :type "spacing" :value "{nope}"}]}
                   {:id "s2" :name "light" :active true :tokens [{:id "t3" :name "color.bg" :type "color" :value "#FFFFFF"}]}
                   {:id "s3" :name "dark" :active false :tokens [{:id "t4" :name "color.bg" :type "color" :value "#111111"}]}]
            :themes [{:id "th1" :group "mode" :name "light" :active true :sets ["core" "light"]}
                     {:id "th2" :group "mode" :name "dark" :active false :sets ["core" "dark"]}]}
   :colors [{:id "c1" :name "Primary" :path "Brand" :color "#3366ff" :opacity 1}]
   :typographies []
   :fileName "Brand Kit"})

(defn- unzip [^bytes data]
  (with-open [z (ZipInputStream. (ByteArrayInputStream. data))]
    (loop [acc {}]
      (if-let [e (.getNextEntry z)]
        (recur (assoc acc (.getName e) (String. (.readAllBytes z) "UTF-8")))
        acc))))

(defn- run [ctx args]
  (fx/call (fx/find-tool design-system/tools "export_design_system") ctx args))

(defn- editor-ctx []
  (assoc (fx/plugin-ctx editor-result) :exports (exports/store {:now #(System/currentTimeMillis)})))

(deftest a-css-export-is-stored-once-with-its-problems
  (let [ctx    (editor-ctx)
        result (run ctx {"file_id" (str fx/file-id) "platform" "css" "options" {"prefix" "sv"}})
        id     (get result "export_id")
        files  (unzip (exports/take! (:exports ctx) id))]
    (is (re-matches #"[0-9a-f]{32}" id))
    (is (= (str "curl -o design-system.zip \"<MCP address>?export=" id "\"") (get result "download")))
    (is (= ["mode=light" "mode=dark"] (get result "combinations")))
    (is (= #{"tokens.css" "problems.json"} (set (keys files))))
    (is (str/includes? (files "tokens.css") "--sv-space-base: 4px;"))
    (is (str/includes? (files "tokens.css") "--sv-library-color-brand-primary: #3366ff;"))
    (is (= 1 (get-in result ["problems" "errors"])))
    (is (= [{"code" "token-error" "severity" "error" "subject" {"kind" "token" "name" "broken"}
             "details" {"errors" [{"code" "missing-reference" "value" ["nope"]}]}
             "combinations" ["mode=light" "mode=dark"]}]
           (json/read-str (files "problems.json"))))
    (is (empty? (fx/rpc-commands ctx)))
    (is (nil? (exports/take! (:exports ctx) id)))))

(deftest a-closed-editor-is-a-clear-error
  (let [ctx (assoc (fx/closed-editor-ctx {}) :exports (exports/store {:now #(System/currentTimeMillis)}))]
    (is (str/starts-with? (:error (run ctx {"file_id" (str fx/file-id) "platform" "css"})) "Open the file in the Penpot editor"))
    (is (empty? (fx/rpc-commands ctx)))))

(deftest unknown-platforms-are-refused-by-the-schema
  (is (some? (:error (run (editor-ctx) {"file_id" (str fx/file-id) "platform" "cobol"})))))

(deftest kotlin-needs-a-package
  (is (str/includes? (:error (run (editor-ctx) {"file_id" (str fx/file-id) "platform" "kotlin"})) "package")))

(deftest kotlin-takes-its-type-name-from-the-file
  (let [ctx    (editor-ctx)
        result (run ctx {"file_id" (str fx/file-id) "platform" "kotlin" "options" {"package" "com.sayvibe.app.core.designsystem"}})]
    (is (= ["BrandKit.kt" "problems.json"] (mapv #(get % "path") (get result "files"))))))

(deftest no-resolvable-combination-is-a-clear-error-for-every-platform
  (let [looping (assoc-in editor-result [:tokens :sets 0 :tokens]
                          [{:id "a" :name "a" :type "spacing" :value "{b} + 1"} {:id "b" :name "b" :type "spacing" :value "{a} * 2"}])
        ctx     (assoc (fx/plugin-ctx looping) :exports (exports/store {:now #(System/currentTimeMillis)}))]
    (doseq [platform ["css" "dtcg" "kotlin"]]
      (is (str/starts-with? (str (:error (run ctx {"file_id" (str fx/file-id) "platform" platform "options" {"package" "com.acme"}})))
                            "No theme combination could be resolved")
          platform))))

(def ^:private realistic-result
  {:tokens {:sets [{:id "s1" :name "core" :active true
                    :tokens [{:id "1" :name "space.base" :type "spacing" :value "4"}
                             {:id "2" :name "space.lg" :type "spacing" :value "{space.base} * 4"}
                             {:id "3" :name "radius.card" :type "borderRadius" :value "{space.base} * 2"}
                             {:id "4" :name "font.size.body" :type "fontSizes" :value "16"}
                             {:id "5" :name "font.weight.strong" :type "fontWeights" :value "Bold"}
                             {:id "6" :name "font.family.base" :type "fontFamilies" :value ["Inter" "Segoe UI"]}
                             {:id "7" :name "opacity.muted" :type "opacity" :value "50%"}
                             {:id "8" :name "type.body" :type "typography"
                              :value {:fontFamilies ["Inter"] :fontSizes "{font.size.body}" :fontWeights "400" :lineHeight "150%" :letterSpacing "0"}}
                             {:id "9" :name "shadow.lift" :type "shadow"
                              :value [{:offsetX "0" :offsetY "2" :blur "4" :spread "0" :color "rgba(#000000, 0.25)" :inset false}]}
                             {:id "10" :name "odd" :type "mystery" :value "1"}
                             {:id "11" :name "text.case" :type "textCase" :value "uppercase"}]}
                   {:id "s2" :name "mode/light" :active true :tokens [{:id "12" :name "color.bg" :type "color" :value "#FFFFFF"}]}
                   {:id "s3" :name "mode/dark" :active false :tokens [{:id "13" :name "color.bg" :type "color" :value "#111111"}]}]
            :themes [{:id "h" :group "" :name "__PENPOT__HIDDEN__TOKEN__THEME__" :active true :sets ["core"]}
                     {:id "t1" :group "mode" :name "light" :active true :sets ["core" "mode/light"]}
                     {:id "t2" :group "mode" :name "dark" :active false :sets ["core" "mode/dark"]}]}
   :colors [{:id "c1" :name "Основной" :path "Brand" :color "#3366ff" :opacity 1}
            {:id "c2" :name "Sky" :path "Brand" :gradient {:type "linear" :startX 0.5 :startY 0 :endX 0.5 :endY 1 :width 1
                                                           :stops [{:color "#ffffff" :opacity 1 :offset 0} {:color "#000000" :opacity 0.5 :offset 1}]}}
            {:id "c3" :name "Photo" :path "" :image {:id "img"}}]
   :typographies [{:id "y1" :name "Body" :path "Text" :fontFamily "Inter" :fontSize "16" :fontWeight "700" :fontStyle "italic"
                   :lineHeight "" :letterSpacing "0" :textTransform "uppercase"}]
   :fileName "Brand Kit"})

(deftest every-platform-exports-realistic-editor-data
  (doseq [[platform options] [["css" {"prefix" "sv" "color_scheme_group" "mode"}] ["scss" {}] ["tailwind" {}] ["tailwind" {"version" 3}]
                              ["typescript" {}] ["dtcg" {}] ["kotlin" {"package" "com.sayvibe.app"}] ["swiftui" {}]]]
    (let [ctx    (assoc (fx/plugin-ctx realistic-result) :exports (exports/store {:now #(System/currentTimeMillis)}))
          result (run ctx {"file_id" (str fx/file-id) "platform" platform "options" options})
          files  (unzip (exports/take! (:exports ctx) (get result "export_id")))
          codes  (set (map #(get % "code") (json/read-str (files "problems.json"))))]
      (is (nil? (:error result)) (str platform " " (:error result)))
      (is (= ["mode=light" "mode=dark"] (get result "combinations")) platform)
      (is (contains? codes "unknown-token-type") platform)
      (is (contains? codes "image-color-skipped") platform)
      (is (< 1 (count files)) platform))))
