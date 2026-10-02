(ns penpot.mcp.tools.library-test
  (:require
   [app.common.types.tokens-lib :as ctob]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.library :as library]))

(defn- run [tool-name args]
  (fx/call (fx/find-tool library/tools tool-name) (fx/ctx {:get-file fx/file}) args))

(def fid (str fx/file-id))

(deftest lists-components
  (is (= {"components" [{"id" (str fx/component-id) "name" "Button" "path" "Forms"
                         "main_instance_id" (str fx/rect-id) "main_instance_page" (str fx/page-id)}]}
         (run "list_components" {"file_id" fid}))))

(deftest lists-component-instances
  (is (= {"instances" [{"id" (str fx/instance-id) "name" "Button Instance" "page_id" (str fx/page-id)
                        "component_id" (str fx/component-id) "component_file" (str fx/file-id) "is_main" false}]}
         (run "get_component_instances" {"file_id" fid "component_id" (str fx/component-id)}))))

(deftest lists-colors
  (is (= {"colors" [{"id" (str fx/color-id) "name" "Primary" "path" "Brand" "color" "#3366FF" "opacity" 1}]}
         (run "get_colors" {"file_id" fid}))))

(deftest lists-typographies
  (let [[t] (get (run "get_typographies" {"file_id" fid}) "typographies")]
    (is (= "Heading" (get t "name")))
    (is (= "Inter" (get t "font_family")))
    (is (= "24" (get t "font_size")))))

(deftest lists-token-sets-with-ids
  (is (= {"sets" [{"id" (str fx/token-set-id) "name" "brand" "active" false
                   "tokens" [{"id" (str fx/token-id) "name" "color.primary" "type" "color"
                              "value" "#3366FF" "description" ""}]}]
          "themes" []}
         (run "get_design_tokens" {"file_id" fid}))))

(def theme-id (parse-uuid "88888888-0000-0000-0000-0000000000e1"))

(deftest lists-token-themes-with-their-sets
  (let [lib    (-> fx/tokens-lib
                   (ctob/add-theme (ctob/make-token-theme :id theme-id :name "dark" :group "mode" :sets #{"brand"}))
                   (ctob/activate-theme theme-id))
        result (fx/call (fx/find-tool library/tools "get_design_tokens")
                        (fx/ctx {:get-file (assoc-in fx/file [:data :tokens-lib] lib)})
                        {"file_id" fid})]
    (is (= [{"id" (str theme-id) "group" "mode" "name" "dark" "active" true "sets" ["brand"]}] (get result "themes")))
    (is (true? (get-in result ["sets" 0 "active"])))))

(deftest design-tokens-empty-without-library
  (is (= {"sets" [] "themes" []}
         (fx/call (fx/find-tool library/tools "get_design_tokens")
                  (fx/ctx {:get-file (update fx/file :data dissoc :tokens-lib)})
                  {"file_id" fid}))))
