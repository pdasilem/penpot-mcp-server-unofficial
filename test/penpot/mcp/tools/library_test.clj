(ns penpot.mcp.tools.library-test
  (:require
   [penpot.mcp.penpot.tokens-lib :as ctob]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools.library :as library]))

(def ^:private tools (into {} (map (juxt :name identity)) library/tools))

(defn- run [scenario]
  (let [replayed (replay/run (tools (:tool (replay/recording scenario))) scenario)]
    (is (empty? (:left replayed)) (str scenario " left recorded requests unused"))
    (replay/full-data replayed)))

(defn- args [scenario]
  (:args (replay/recording scenario)))

(defn- saved-file [scenario]
  (first (replay/penpot-answers scenario :get-file)))

(defn- live-components [scenario]
  (remove :deleted (vals (get-in (saved-file scenario) [:data :components]))))

(defn- full-name [{:keys [path name]}]
  (str/lower-case (if (str/blank? path) (str name) (str path " / " name))))

(deftest components-are-the-live-components-of-the-file-sorted-by-path-and-name
  (let [result (run "library/components")
        listed (get result "components")
        saved  (live-components "library/components")]
    (is (= 100 (count listed)))
    (is (= "100" (get result "next_cursor")))
    (is (= (->> saved (sort-by (juxt :path :name (comp str :id))) (take 100) (map (comp str :id)))
           (map #(get % "id") listed)))))

(deftest components-are-filtered-by-name-or-path
  (let [query  (get (args "library/components-query") "query")
        listed (get (run "library/components-query") "components")]
    (is (= (count (filter #(str/includes? (full-name %) query) (live-components "library/components-query")))
           (count listed)))
    (is (every? #(str/includes? (full-name {:path (get % "path") :name (get % "name")}) query) listed))))

(deftest a-large-file-needs-the-editor-for-its-library
  (is (re-find #"more than the 5000" (:error (run "library/components-large-file")))))

(deftest instances-are-the-copies-of-the-component
  (let [component (parse-uuid (get (args "library/instances") "component_id"))
        file      (saved-file "library/instances")
        roots     (for [p (vals (get-in file [:data :pages-index]))
                        s (vals (:objects p))
                        :when (and (:component-root s) (= component (:component-id s)))]
                    (str (:id s)))
        listed    (get (run "library/instances") "instances")]
    (is (= (set roots) (set (map #(get % "id") listed))))
    (is (every? #(= (str component) (get % "component_id")) listed))))

(deftest colors-are-the-library-colors-of-the-file
  (is (= (set (map (comp str :id) (vals (get-in (saved-file "library/colors") [:data :colors]))))
         (set (map #(get % "id") (get (run "library/colors") "colors"))))))

(deftest typographies-are-the-library-typographies-of-the-file
  (is (= (count (get-in (saved-file "library/typographies") [:data :typographies]))
         (count (get (run "library/typographies") "typographies")))))

(defn- token-names [result]
  (mapcat (fn [s] (map #(get % "name") (get s "tokens"))) (get result "sets")))

(deftest design-tokens-are-the-sets-and-themes-of-the-file
  (let [lib    (get-in (saved-file "library/tokens") [:data :tokens-lib])
        result (run "library/tokens")]
    (is (= (map ctob/get-name (ctob/get-sets lib)) (map #(get % "name") (get result "sets"))))
    (is (= (count (remove ctob/hidden-theme? (ctob/get-themes lib))) (count (get result "themes"))))
    (is (= (reduce + (map #(count (ctob/get-tokens lib (ctob/get-id %))) (ctob/get-sets lib)))
           (count (token-names result))))))

(deftest design-tokens-are-filtered-by-name-and-type
  (let [query (get (args "library/tokens-query") "query")]
    (is (seq (token-names (run "library/tokens-query"))))
    (is (every? #(str/includes? (str/lower-case %) query) (token-names (run "library/tokens-query")))))
  (let [types (mapcat (fn [s] (map #(get % "type") (get s "tokens"))) (get (run "library/tokens-type") "sets"))]
    (is (seq types))
    (is (every? #{"color"} types))))

(deftest the-editor-gives-the-same-library
  (doseq [[saved editor] [["library/components" "library/components-editor"]
                          ["library/instances" "library/instances-editor"]
                          ["library/colors" "library/colors-editor"]
                          ["library/typographies" "library/typographies-editor"]
                          ["library/tokens" "library/tokens-editor"]
                          ["library/tokens-query" "library/tokens-query-editor"]
                          ["library/tokens-type" "library/tokens-type-editor"]]]
    (is (= (run saved) (run editor)) editor)
    (is (empty? (replay/requests editor)) (str editor " reads the library from the editor"))))
