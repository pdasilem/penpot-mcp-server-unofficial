(ns penpot.mcp.tools.library-test
  (:require
   [app.common.types.tokens-lib :as ctob]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.library :as library]))

(defn- run [tool-name args]
  (fx/call (fx/find-tool library/tools tool-name) (fx/ctx (fx/file-responses fx/file)) args))

(defn- run-in-editor [tool-name result args]
  (let [ctx (fx/plugin-ctx result (fx/file-responses fx/file))]
    {:result (fx/call (fx/find-tool library/tools tool-name) ctx args)
     :calls (fx/rpc-commands ctx)
     :scripts @(:scripts ctx)}))

(def fid (str fx/file-id))

(def components-result
  {"components" [{"id" (str fx/component-id) "name" "Button" "path" "Forms"
                  "main_instance_id" (str fx/rect-id) "main_instance_page" (str fx/page-id)}]})

(deftest lists-components
  (is (= components-result (run "list_components" {"file_id" fid}))))

(deftest lists-components-from-the-open-editor
  (let [{:keys [result calls]} (run-in-editor "list_components"
                                              [{:id (str fx/component-id) :name "Button" :path "Forms"
                                                :mainInstanceId (str fx/rect-id) :mainInstancePage (str fx/page-id)}]
                                              {"file_id" fid})]
    (is (= components-result result))
    (is (empty? calls))))

(def instances-result
  {"instances" [{"id" (str fx/instance-id) "name" "Button Instance" "page_id" (str fx/page-id)
                 "component_id" (str fx/component-id) "component_file" (str fx/file-id) "is_main" false}]})

(deftest lists-component-instances
  (is (= instances-result (run "get_component_instances" {"file_id" fid "component_id" (str fx/component-id)}))))

(def colors-result
  {"colors" [{"id" (str fx/color-id) "name" "Primary" "path" "Brand" "color" "#3366FF" "opacity" 1}]})

(deftest lists-colors
  (is (= colors-result (run "get_colors" {"file_id" fid}))))

(deftest lists-colors-from-the-open-editor
  (let [{:keys [result calls]} (run-in-editor "get_colors"
                                              [{:id (str fx/color-id) :name "Primary" :path "Brand" :color "#3366FF" :opacity 1}]
                                              {"file_id" fid})]
    (is (= colors-result result))
    (is (empty? calls))))

(deftest lists-gradient-colors-from-the-open-editor-with-file-keys
  (let [{:keys [result]} (run-in-editor "get_colors"
                                        [{:id (str fx/color-id) :name "Sky" :path "" :opacity 1
                                          :gradient {:type "linear" :startX 0 :startY 0 :endX 1 :endY 1 :width 1
                                                     :stops [{:color "#FF0000" :opacity 1 :offset 0}]}}]
                                        {"file_id" fid})]
    (is (= {"type" "linear" "start_x" 0 "start_y" 0 "end_x" 1 "end_y" 1 "width" 1
            "stops" [{"color" "#FF0000" "opacity" 1 "offset" 0}]}
           (get-in result ["colors" 0 "gradient"])))))

(def typography-result
  {"typographies" [{"id" (str fx/typography-id) "name" "Heading" "path" "" "font_family" "Inter"
                    "font_size" "24" "font_weight" "700" "font_style" "normal" "line_height" "1.2"
                    "letter_spacing" "0" "text_transform" "none"}]})

(deftest lists-typographies
  (is (= typography-result (run "get_typographies" {"file_id" fid}))))

(deftest lists-typographies-from-the-open-editor
  (let [{:keys [result calls]} (run-in-editor "get_typographies"
                                              [{:id (str fx/typography-id) :libraryId fid :name "Heading" :path ""
                                                :fontFamily "Inter" :fontSize "24" :fontWeight "700" :fontStyle "normal"
                                                :lineHeight "1.2" :letterSpacing "0" :textTransform "none"}]
                                              {"file_id" fid})]
    (is (= typography-result result))
    (is (empty? calls))))

(def tokens-result
  {"sets" [{"id" (str fx/token-set-id) "name" "brand" "active" false
            "tokens" [{"id" (str fx/token-id) "name" "color.primary" "type" "color"
                       "value" "#3366FF" "description" ""}]}]
   "themes" []})

(deftest lists-token-sets-with-ids
  (is (= tokens-result (run "get_design_tokens" {"file_id" fid}))))

(deftest lists-token-sets-from-the-open-editor
  (let [{:keys [result calls]} (run-in-editor "get_design_tokens"
                                              {:sets [{:id (str fx/token-set-id) :name "brand" :active false
                                                       :tokens [{:id (str fx/token-id) :name "color.primary" :type "color"
                                                                 :value "#3366FF" :description ""}]}]
                                               :themes [{:id "00000000-0000-0000-0000-000000000000"
                                                         :group ctob/hidden-theme-group :name ctob/hidden-theme-name
                                                         :active true :sets []}]}
                                              {"file_id" fid})]
    (is (= tokens-result result))
    (is (empty? calls))))

(deftest editor-token-types-and-composite-values-use-file-names
  (let [{:keys [result]} (run-in-editor "get_design_tokens"
                                        {:sets [{:id (str fx/token-set-id) :name "brand" :active true
                                                 :tokens [{:id "a" :name "radius.s" :type "borderRadius" :value "4" :description ""}
                                                          {:id "b" :name "type.body" :type "typography" :description ""
                                                           :value {:fontFamilies ["Inter"] :fontSizes "16" :fontWeight "400"
                                                                   :lineHeight "1.4" :letterSpacing "0"}}]}]
                                         :themes []}
                                        {"file_id" fid})
        [radius body] (get-in result ["sets" 0 "tokens"])]
    (is (= "border-radius" (get radius "type")))
    (is (= {"font_family" ["Inter"] "font_size" "16" "font_weight" "400" "line_height" "1.4" "letter_spacing" "0"}
           (get body "value")))))

(def theme-id (parse-uuid "88888888-0000-0000-0000-0000000000e1"))

(deftest lists-token-themes-with-their-sets
  (let [lib    (-> fx/tokens-lib
                   (ctob/add-theme (ctob/make-token-theme :id theme-id :name "dark" :group "mode" :sets #{"brand"}))
                   (ctob/activate-theme theme-id))
        result (fx/call (fx/find-tool library/tools "get_design_tokens")
                        (fx/ctx (fx/file-responses (assoc-in fx/file [:data :tokens-lib] lib)))
                        {"file_id" fid})]
    (is (= [{"id" (str theme-id) "group" "mode" "name" "dark" "active" true "sets" ["brand"]}] (get result "themes")))
    (is (true? (get-in result ["sets" 0 "active"])))))

(deftest lists-token-themes-from-the-open-editor
  (let [{:keys [result]} (run-in-editor "get_design_tokens"
                                        {:sets [] :themes [{:id (str theme-id) :group "mode" :name "dark" :active true
                                                            :sets ["brand" "base"]}]}
                                        {"file_id" fid})]
    (is (= [{"id" (str theme-id) "group" "mode" "name" "dark" "active" true "sets" ["base" "brand"]}]
           (get result "themes")))))

(deftest design-tokens-empty-without-library
  (is (= {"sets" [] "themes" []}
         (fx/call (fx/find-tool library/tools "get_design_tokens")
                  (fx/ctx (fx/file-responses (update fx/file :data dissoc :tokens-lib)))
                  {"file_id" fid}))))

(deftest library-of-large-file-needs-the-editor
  (doseq [tool-name ["list_components" "get_colors" "get_typographies" "get_design_tokens" "get_component_instances"]]
    (let [ctx    (assoc-in (fx/closed-editor-ctx (fx/file-responses fx/file)) [:config :full-file-shapes-max] 5)
          result (fx/call (fx/find-tool library/tools tool-name) ctx {"file_id" fid})]
      (is (str/ends-with? (:error result) "; open it in the Penpot editor with MCP enabled") tool-name)
      (is (= [:get-file-stats] (fx/rpc-commands ctx)) tool-name))))

(deftest lists-component-instances-in-the-open-editor-without-downloading-pages
  (let [{:keys [result calls scripts]} (run-in-editor "get_component_instances"
                                                      [{:id (str fx/instance-id) :name "Button Instance" :page_id (str fx/page-id)
                                                        :component_id (str fx/component-id) :component_file (str fx/file-id)
                                                        :is_main false}]
                                                      {"file_id" fid "component_id" (str fx/component-id)})]
    (is (= instances-result result))
    (is (empty? calls))
    (is (str/includes? (last scripts) "s.isComponentRoot()"))))
