(ns penpot.mcp.tools.design-system-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.exports :as exports]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools.design-system :as design-system])
  (:import
   (java.io ByteArrayInputStream)
   (java.util.zip ZipInputStream)))

(def ^:private tool (first design-system/tools))

(defn- unzip [^bytes data]
  (with-open [z (ZipInputStream. (ByteArrayInputStream. data))]
    (loop [acc {}]
      (if-let [e (.getNextEntry z)]
        (recur (assoc acc (.getName e) (String. (.readAllBytes z) "UTF-8")))
        acc))))

(defn- export! [scenario]
  (let [{:keys [result left ctx]} (replay/run tool scenario)
        answer (replay/data {:result result})]
    (is (empty? left) scenario)
    (assoc answer ::files (some->> (get answer "export_id") (exports/take! (:exports ctx)) unzip))))

(def ^:private platforms
  {"css" ["tokens.css"] "scss" ["_tokens.scss"] "tailwind-4" ["tokens.css"] "tailwind-3" ["tailwind.theme.js" "tokens.css"]
   "typescript" ["tokens.ts"] "kotlin" ["DesignTestData.kt"] "swiftui" ["DesignTestData.swift"]})

(deftest every-platform-exports-the-real-design-system-once
  (doseq [[platform paths] platforms
          :let [answer (export! (str "design-system/" platform))]]
    (is (= 6 (count (get answer "combinations"))) platform)
    (is (= (set (conj paths "problems.json")) (set (keys (::files answer)))) platform)
    (is (re-find #"^curl -o design-system\.zip " (get answer "download")) platform)))

(deftest dtcg-writes-one-file-per-combination
  (let [answer (export! "design-system/dtcg")]
    (is (= 7 (count (::files answer))))))

(deftest problems-in-the-archive-match-the-answer
  (let [answer   (export! "design-system/kotlin")
        problems (json/read-str (get (::files answer) "problems.json"))]
    (is (= (count problems) (+ (get-in answer ["problems" "errors"]) (get-in answer ["problems" "warnings"]))))))

(deftest a-closed-editor-is-a-clear-error
  (is (re-find #"Open the file in the Penpot editor" (get (export! "design-system/closed-editor") :error))))

(deftest arguments-are-checked-before-reading-the-editor
  (let [args (:args (replay/recording "design-system/kotlin"))]
    (is (:error (replay/data (replay/run-without-penpot tool (assoc args "platform" "cobol")))))
    (is (:error (replay/data (replay/run-without-penpot tool (assoc args "options" {})))))))
