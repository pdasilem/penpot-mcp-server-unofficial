(ns penpot.mcp.tools.token-usage-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.tokens :as tokens]
   [penpot.mcp.exports :as exports]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.tools :as tools]
   [penpot.mcp.tools.token-usage :as token-usage]))

(def ^:private fid (str fx/file-id))

(def ^:private tool (fx/find-tool token-usage/tools "token_usage"))

(def ^:private file
  (assoc-in fx/file [:data :pages-index fx/page-id :objects fx/rect-id :applied-tokens] {:stroke-color "color.primary"}))

(defn- run [f args]
  (fx/call tool (fx/ctx (fx/file-responses f)) args))

(defn- shapes [result]
  (mapcat #(get % "shapes") (get result "raw_values")))

(defn- values-of [result shape-name]
  (some #(when (= shape-name (get % "shape")) (get % "values")) (shapes result)))

(deftest token-usage-is-a-read-tool
  (is (= "read" (:toolset (fx/find-tool tools/all "token_usage")))))

(deftest reports-usage-of-the-applied-token
  (let [result (run file {"file_id" fid})]
    (is (= {"tokens" 1 "applied" 1 "missing" 0 "referenced_only" 0 "unused" 0 "shapes_checked" 6 "raw_values" 17 "values_compared" true}
           (get result "summary")))
    (is (= [{"name" "color.primary" "shapes" 1 "copies" 0 "attributes" ["strokeColor"]
             "pages" [{"id" (str fx/page-id) "name" "Screens" "shapes" 1}]}]
           (get result "usage")))))

(deftest a-token-no-shape-applies-is-unused
  (is (= [{"name" "color.primary" "type" "color" "sets" [{"set" "brand" "value" "#3366FF"}]}]
         (get (run fx/file {"file_id" fid}) "unused"))))

(deftest raw-values-are-grouped-by-frame-and-shape
  (let [result (run file {"file_id" fid})
        [card] (get result "raw_values")]
    (is (= {"page_id" (str fx/page-id) "page" "Screens" "frame_id" (str fx/board-id) "frame" "Login Card"}
           (dissoc card "shapes")))
    (is (= {"attribute" "paddingTop" "value" 24 "off_scale" true} (first (values-of result "Login Card"))))
    (is (= {"attribute" "fill" "value" "#3366ff" "opacity" 0.5 "off_scale" true}
           (last (values-of result "Submit Button"))))
    (is (not-any? #(= "strokeColor" (get % "attribute")) (values-of result "Submit Button")))
    (is (nil? (values-of result "Avatar")) "gradients are not raw values")))

(deftest raw-values-page-with-limit-and-cursor
  (let [first-page (run file {"file_id" fid "limit" 3})
        second     (run file {"file_id" fid "limit" 3 "cursor" (get first-page "next_cursor")})]
    (is (= "3" (get first-page "next_cursor")))
    (is (= 3 (count (mapcat #(get % "values") (shapes first-page)))))
    (is (= {"attribute" "paddingLeft" "value" 16 "off_scale" true} (first (values-of second "Login Card"))))))

(deftest page-id-narrows-raw-values
  (let [result (run file {"file_id" fid "page_id" (str fx/page2-id)})]
    (is (= [] (get result "raw_values")))
    (is (= 0 (get-in result ["summary" "raw_values"])))
    (is (= 1 (get-in result ["summary" "applied"])))))

(defn- editor [editor-tokens]
  (fn [code]
    (cond
      (str/includes? code read/pages-body) [{:id (str fx/page-id) :name "Screens"}]
      (str/includes? code read/tokens-body)
      {:sets [{:id "s" :name "brand" :active true
               :tokens (into [{:id "t" :name "color.primary" :type "color" :value "#3366FF" :description ""}] editor-tokens)}]
       :themes []})))

(def ^:private blue-instance
  (assoc-in file [:data :pages-index fx/page-id :objects fx/instance-id :fills] [{:fill-color "#3366FF" :fill-opacity 1}]))

(deftest reads-tokens-and-page-list-from-the-open-editor
  (let [ctx    (fx/plugin-ctx (editor []) (fx/file-responses blue-instance))
        result (fx/call tool ctx {"file_id" fid})]
    (is (= [:get-page] (distinct (fx/rpc-commands ctx))))
    (is (= 1 (get-in result ["summary" "applied"])))
    (is (= ["color.primary"] (get (first (values-of result "Button Instance")) "matches")))))

(deftest tokens-that-fail-to-resolve-are-named-in-the-summary
  (let [ctx    (fx/plugin-ctx (editor [{:id "b" :name "space.bad" :type "spacing" :value "{nope} * 2" :description ""}])
                              (fx/file-responses blue-instance))
        result (fx/call tool ctx {"file_id" fid})]
    (is (= ["space.bad"] (get-in result ["summary" "unresolved_tokens"])))
    (is (true? (get-in result ["summary" "values_compared"])))))

(deftest raw-values-stay-without-a-verdict-when-token-values-cannot-be-computed
  (with-redefs [tokens/resolve-catalog (fn [_] (throw (ex-info "Token resolution is busy, try again later"
                                                               {:type :penpot.mcp.design.budget/busy})))]
    (let [result (run file {"file_id" fid})]
      (is (false? (get-in result ["summary" "values_compared"])))
      (is (= "Token resolution is busy, try again later" (get-in result ["summary" "values_not_compared"])))
      (is (not-any? #(or (contains? % "matches") (contains? % "off_scale")) (mapcat #(get % "values") (shapes result)))))))

(deftest sections-limit-the-answer
  (let [result (run file {"file_id" fid "sections" ["unused" "missing"]})]
    (is (= #{"summary" "unused" "missing"} (set (keys result))))))

(deftest token-values-are-not-computed-when-raw-values-are-not-asked
  (with-redefs [tokens/resolve-catalog (fn [_] (throw (AssertionError. "not expected")))]
    (is (= 1 (get-in (run file {"file_id" fid "sections" ["usage"]}) ["summary" "applied"])))))

(deftest an-unknown-page-is-an-error
  (is (str/includes? (:error (run file {"file_id" fid "page_id" "11111111-0000-0000-0000-0000000000ff"})) "not found")))

(deftest without-the-editor-the-file-is-downloaded-once
  (let [ctx (assoc (fx/ctx (fx/file-responses file)) :file-cache (atom nil))]
    (fx/call tool ctx {"file_id" fid})
    (is (= 1 (count (filter #{:get-file} (fx/rpc-commands ctx)))))))

(deftest only-the-default-theme-combination-is-resolved
  (let [themed (fn [code]
                 (cond
                   (str/includes? code read/pages-body) [{:id (str fx/page-id) :name "Screens"}]
                   (str/includes? code read/tokens-body)
                   {:sets [{:id "l" :name "light" :active false
                            :tokens [{:id "a" :name "color.bg" :type "color" :value "#3366FF" :description ""}]}
                           {:id "d" :name "dark" :active true
                            :tokens [{:id "b" :name "color.bg" :type "color" :value "#FFFFFF" :description ""}]}]
                    :themes [{:id "t1" :group "mode" :name "light" :active false :sets ["light"]}
                             {:id "t2" :group "mode" :name "dark" :active true :sets ["dark"]}]}))
        seen   (atom nil)
        real   tokens/resolve-catalog]
    (with-redefs [tokens/resolve-catalog (fn [c] (reset! seen (:themes c)) (real c))]
      (let [result (fx/call tool (fx/plugin-ctx themed (fx/file-responses blue-instance)) {"file_id" fid})]
        (is (= ["dark"] (map :name @seen)))
        (is (= ["color.bg"] (get (last (values-of result "Login Card")) "matches")))
        (is (true? (get (first (values-of result "Button Instance")) "off_scale")))))))

(deftest large-sections-go-to-a-download-and-raw-values-stay
  (let [many   (into [] (map (fn [i] {:id (str i) :name (str "space.unused." i) :type "spacing" :value (str i) :description ""})) (range 3000))
        ctx    (assoc (fx/plugin-ctx (editor many) (fx/file-responses blue-instance)) :exports (exports/store {:now (constantly 0)}))
        result (fx/call tool ctx {"file_id" fid "limit" 5})]
    (is (= 5 (count (mapcat #(get % "values") (shapes result)))))
    (is (contains? result "next_cursor"))
    (is (not (contains? result "unused")))
    (is (= #{"unused" "missing" "referenced_only" "references" "usage"} (set (get result "archived_sections"))))
    (is (re-find #"curl -o token-usage\.zip" (get-in result ["full_result" "download"])))))
