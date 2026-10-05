(ns ^:integration penpot.mcp.tools.token-usage-it-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.test-client :as mcp]
   [penpot.mcp.tools.design-system-it-test :as ds]
   [penpot.mcp.tools.plugin-it-test :as p]))

(defn- data [session tool-name args]
  (let [result (mcp/call-tool session tool-name args)]
    (when (:isError result)
      (throw (ex-info (str tool-name " failed: " (get-in result [:content 0 :text])) {})))
    (json/read-str (get-in result [:content 0 :text]))))

(defn- created [session tool-name args]
  (get-in (data session tool-name args) ["shape" "id"]))

(defn- values-of [result shape-name]
  (some #(when (= shape-name (get % "shape")) (get % "values"))
        (mapcat #(get % "shapes") (get result "raw_values"))))

(defn- value [result shape-name attribute]
  (some #(when (= attribute (get % "attribute")) %) (values-of result shape-name)))

(defn- build-canvas [s fid]
  (let [screen (created s "create_board" {:file_id fid :x 0 :y 0 :width 400 :height 300 :name "Screen"})
        card   (created s "create_board" {:file_id fid :x 20 :y 20 :width 200 :height 100 :name "Card" :parent_id screen})
        _      (data s "set_flex_layout" {:file_id fid :board_id card :dir "column" :padding {:top 16} :row_gap 12})
        _      (created s "create_rect" {:file_id fid :x 0 :y 0 :width 40 :height 20 :name "Chip" :border_radius 8 :parent_id card})
        tag    (created s "create_rect" {:file_id fid :x 0 :y 0 :width 40 :height 20 :name "Tag" :parent_id card})]
    (data s "set_token" {:file_id fid :shape_id tag :token_id (str @#'ds/radius-id)})))

(defn- check-report [result]
  (is (= ["radius.card"] (map #(get % "name") (get result "usage"))))
  (is (= ["space.base"] (get result "referenced_only")))
  (is (every? (set (map #(get % "name") (get result "unused"))) ["type.body" "font.size.body" "space.lg" "color.overlay"]))
  (is (= [] (get result "missing")))
  (is (= ["space.lg"] (get (value result "Card" "paddingTop") "matches")))
  (is (true? (get (value result "Card" "rowGap") "off_scale")))
  (is (nil? (value result "Card" "columnGap")))
  (is (= 200 (get (value result "Card" "width") "value")))
  (is (= ["radius.card"] (get (value result "Chip" "borderRadiusTopLeft") "matches")))
  (is (nil? (value result "Tag" "borderRadiusTopLeft")) "a bound radius is not raw")
  (is (= ["color.bg"] (get (value result "Screen" "fill") "matches")))
  (is (nil? (value result "Screen" "width")) "top-level boards keep their size"))

(deftest token-usage-against-live-editor-and-file
  (let [client  (it/client)
        team-id (:default-team-id (rpc/call client :get-profile {}))]
    (it/with-temp-project client
      (fn [project]
        (let [file (rpc/call client :create-file {:project-id (:id project) :name "it-token-usage"})
              fid  (str (:id file))
              _    (@#'ds/add-design-system client (:id file))
              s    (mcp/connect (str p/mcp-url "?userToken=" (get it/env "PENPOT_MCP_KEY")))
              proc (@#'p/open-editor team-id (:id file))
              live (try
                     (build-canvas s fid)
                     (data s "token_usage" {:file_id fid})
                     (finally (@#'p/close-editor proc)))]
          (testing "through the open editor"
            (check-report live)
            (is (true? (get-in live ["summary" "values_compared"]))))
          (testing "from the saved file without the editor"
            (let [saved (data s "token_usage" {:file_id fid})]
              (check-report saved)
              (is (= (get live "summary") (get saved "summary"))))))))))
