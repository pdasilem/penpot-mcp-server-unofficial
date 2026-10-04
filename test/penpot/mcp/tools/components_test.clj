(ns penpot.mcp.tools.components-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.components :as components]))

(def fid (str fx/file-id))
(def sid (str fx/instance-id))
(def cid (str fx/component-id))
(def lib-id "66666666-0000-0000-0000-0000000000aa")

(defn- run [tool-name args]
  (let [ctx (fx/plugin-ctx {:shape {:id sid}})]
    {:ctx ctx :result (fx/call (fx/find-tool components/tools tool-name) ctx args)}))

(deftest creates-instance-of-local-component
  (let [{:keys [ctx result]} (run "create_component_instance" {"file_id" fid "component_id" cid "x" 10 "y" 20})]
    (is (= {"fileId" fid "componentId" cid "x" 10 "y" 20} (fx/last-script-args ctx)))
    (is (str/includes? (last @(:scripts ctx)) ".instance()"))
    (is (= {"shape" {"id" sid}} result))))

(deftest creates-instance-of-library-component-in-a-board
  (let [{:keys [ctx]} (run "create_component_instance" {"file_id" fid "component_id" cid "library_file_id" lib-id
                                                        "parent_id" (str fx/board-id) "x" 0 "y" 0})]
    (is (= {"fileId" fid "componentId" cid "libraryId" lib-id "parentId" (str fx/board-id) "x" 0 "y" 0}
           (fx/last-script-args ctx)))))

(deftest instance-needs-one-placement
  (is (= {:error "Give page_id or parent_id, not both"}
         (:result (run "create_component_instance" {"file_id" fid "component_id" cid "x" 0 "y" 0
                                                    "page_id" (str fx/page-id) "parent_id" (str fx/board-id)})))))

(deftest combines-components-into-variants
  (let [other "33333333-0000-0000-0000-000000000002"
        {:keys [ctx]} (run "create_variants" {"file_id" fid "component_ids" [cid other]})]
    (is (= {"fileId" fid "componentIds" [cid other]} (fx/last-script-args ctx)))
    (is (str/includes? (last @(:scripts ctx)) "createVariantFromComponents"))))

(deftest variants-need-two-components
  (is (contains? (:result (run "create_variants" {"file_id" fid "component_ids" [cid]})) :error)))

(deftest sets-variant-property-by-name
  (let [{:keys [ctx]} (run "set_variant_property" {"file_id" fid "component_id" cid "property" "State" "value" "hover"})]
    (is (= {"fileId" fid "componentId" cid "property" "State" "value" "hover"} (fx/last-script-args ctx)))
    (is (str/includes? (last @(:scripts ctx)) "addProperty()"))))

(deftest renames-and-removes-variant-property
  (let [{:keys [ctx]} (run "rename_variant_property" {"file_id" fid "component_id" cid "property" "State" "new_name" "Status"})]
    (is (= {"fileId" fid "componentId" cid "property" "State" "newName" "Status"} (fx/last-script-args ctx))))
  (let [{:keys [ctx]} (run "remove_variant_property" {"file_id" fid "component_id" cid "property" "State"})]
    (is (= {"fileId" fid "componentId" cid "property" "State"} (fx/last-script-args ctx)))))

(deftest variant-property-names-cannot-be-blank
  (is (contains? (:result (run "set_variant_property" {"file_id" fid "component_id" cid "property" "" "value" "x"})) :error))
  (is (contains? (:result (run "rename_variant_property" {"file_id" fid "component_id" cid "property" "A" "new_name" ""})) :error)))

(deftest switches-variant-of-a-copy
  (let [{:keys [ctx]} (run "switch_variant" {"file_id" fid "shape_id" sid "property" "State" "value" "hover"})]
    (is (= {"fileId" fid "shapeId" sid "property" "State" "value" "hover"} (fx/last-script-args ctx)))
    (is (str/includes? (last @(:scripts ctx)) "switchVariant("))))

(deftest copy-actions-target-one-shape
  (doseq [[tool-name call] [["detach_instance" "detach()"] ["reset_overrides" "resetOverrides()"]]]
    (let [{:keys [ctx]} (run tool-name {"file_id" fid "shape_id" sid})]
      (is (= {"fileId" fid "shapeId" sid} (fx/last-script-args ctx)))
      (is (str/includes? (last @(:scripts ctx)) call)))))

(deftest swaps-component-of-a-copy
  (let [{:keys [ctx]} (run "swap_component" {"file_id" fid "shape_id" sid "component_id" cid "library_file_id" lib-id})]
    (is (= {"fileId" fid "shapeId" sid "componentId" cid "libraryId" lib-id} (fx/last-script-args ctx)))
    (is (str/includes? (last @(:scripts ctx)) "swapComponent("))))

(deftest instance-without-parent-is-moved-to-the-page-root
  (let [{:keys [ctx]} (run "create_component_instance" {"file_id" fid "component_id" cid "x" 10 "y" 20})]
    (is (str/includes? (last @(:scripts ctx)) "(parent ?? penpot.currentPage.root).appendChild(s);"))))
