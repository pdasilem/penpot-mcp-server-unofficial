(ns penpot.mcp.tools-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.server :as server]
   [penpot.mcp.test-client :as client]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools :as tools]))

(def expected-tools
  #{"get_profile" "list_teams" "list_projects" "list_files" "search_files" "get_file" "get_file_libraries"
    "list_snapshots" "compare_snapshots" "list_shapes" "get_shape_tree" "get_shape" "search_shapes"
    "get_shape_css" "get_shape_svg" "list_components" "get_component_instances"
    "get_colors" "get_typographies" "get_design_tokens" "list_comments" "list_media" "list_fonts"
    "list_webhooks" "get_active_users"
    "create_project" "rename_project" "create_file" "rename_file" "duplicate_file" "delete_file"
    "create_snapshot" "create_page" "rename_page" "delete_page" "create_comment" "reply_comment"
    "resolve_comment" "update_comment" "delete_comment" "delete_comment_thread" "upload_media_from_url"
    "create_board" "create_rect" "create_ellipse" "create_text" "create_path" "create_group" "create_component"
    "create_component_instance" "create_variants" "set_variant_property" "rename_variant_property"
    "remove_variant_property" "switch_variant" "swap_component" "detach_instance" "reset_overrides"
    "set_layout_child" "set_grid_cell" "set_shadows" "set_blur" "set_blend_mode" "set_constraints"
    "set_proportion_lock" "set_flip" "duplicate_shape"
    "set_text_range_style" "apply_typography" "apply_library_color" "set_image_fill" "create_library_color"
    "create_library_typography"
    "create_token_set" "delete_token_set" "set_token_set_active" "create_token" "update_token" "delete_token"
    "create_token_theme" "delete_token_theme" "set_token_theme_active" "set_theme_sets"
    "create_boolean" "set_mask" "ungroup" "flatten" "import_svg" "align_shapes" "distribute_shapes"
    "import_html" "get_import_status" "cancel_import" "resume_import"
    "set_position" "resize" "rotate" "rename_shape" "set_fills" "set_strokes" "set_opacity" "set_radius"
    "set_visible" "set_blocked" "set_parent_index" "move_to_parent" "delete_shapes"
    "set_flex_layout" "set_grid_layout" "remove_layout" "set_text_content" "set_text_style"
    "set_token" "remove_token" "export_shape" "export_design_system"})

(deftest registers-tools
  (is (= expected-tools (set (map :name tools/all)))))

(deftest tool-names-are-unique
  (is (= (count tools/all) (count (set (map :name tools/all))))))

(deftest every-tool-has-description-and-valid-schema
  (doseq [t tools/all]
    (is (seq (:description t)) (:name t))
    (is (= "object" (get (tool/json-schema (:input-schema t)) "type")) (:name t))))

(deftest sdk-accepts-every-tool-schema
  (let [s (server/start! {:host "127.0.0.1" :port 0 :mcp-key "k" :tools tools/all
                          :ctx {:version-error (constantly nil)}})]
    (try
      (let [listed (get-in (client/request (client/connect (str "http://127.0.0.1:" (:port s) "/mcp?userToken=k"))
                                           2 "tools/list" {})
                           [:result :tools])]
        (is (= (count tools/all) (count listed))))
      (finally (server/stop! s)))))

(def read-only-tools
  #{"get_profile" "list_teams" "list_projects" "list_files" "search_files" "get_file" "get_file_libraries"
    "list_snapshots" "compare_snapshots" "list_shapes" "get_shape_tree" "get_shape" "search_shapes"
    "get_shape_css" "get_shape_svg" "list_components" "get_component_instances"
    "get_colors" "get_typographies" "get_design_tokens" "list_comments" "list_media" "list_fonts"
    "list_webhooks" "get_active_users" "export_shape" "export_design_system" "get_import_status"})

(def destructive-tools
  #{"rename_project" "rename_file" "delete_file" "rename_page" "delete_page" "resolve_comment"
    "update_comment" "delete_comment" "delete_comment_thread"
    "set_position" "resize" "rotate" "rename_shape" "set_fills" "set_strokes" "set_opacity" "set_radius"
    "set_visible" "set_blocked" "set_parent_index" "move_to_parent" "delete_shapes"
    "set_flex_layout" "set_grid_layout" "remove_layout" "set_text_content" "set_text_style"
    "set_token" "remove_token"
    "create_variants" "set_variant_property" "rename_variant_property" "remove_variant_property"
    "switch_variant" "swap_component" "detach_instance" "reset_overrides"
    "set_layout_child" "set_grid_cell" "set_shadows" "set_blur" "set_blend_mode" "set_constraints"
    "set_proportion_lock" "set_flip"
    "set_text_range_style" "apply_typography" "apply_library_color" "set_image_fill"
    "delete_token_set" "set_token_set_active" "update_token" "delete_token" "delete_token_theme"
    "set_token_theme_active" "set_theme_sets"
    "set_mask" "ungroup" "flatten" "align_shapes" "distribute_shapes" "cancel_import"})

(deftest every-tool-has-annotations
  (doseq [t tools/all]
    (is (= #{:read-only :destructive :idempotent :open-world} (set (keys (:annotations t)))) (:name t))))

(deftest annotations-match-tool-behaviour
  (is (= read-only-tools (set (map :name (filter (comp :read-only :annotations) tools/all)))))
  (is (= destructive-tools (set (map :name (filter (comp :destructive :annotations) tools/all)))))
  (is (= #{"upload_media_from_url" "set_image_fill" "import_svg" "import_html"} (set (map :name (filter (comp :open-world :annotations) tools/all))))))

(deftest instructions-carry-the-shared-rules-once
  (is (str/includes? tools/instructions "open in a browser tab"))
  (is (not-any? #(str/includes? (:description %) "open in a browser tab") tools/all))
  (is (every? #(str/ends-with? (:description %) "[editor]")
              (filter #(str/includes? (:description %) "[editor]") tools/all))))

(deftest every-parameter-is-described
  (doseq [t tools/all
          [k v] (get (tool/json-schema (:input-schema t)) "properties")]
    (is (seq (get v "description")) (str (:name t) "." k))))

(deftest sdk-publishes-annotations
  (let [s (server/start! {:host "127.0.0.1" :port 0 :mcp-key "k" :tools tools/all
                          :ctx {:version-error (constantly nil)}})]
    (try
      (let [listed (get-in (client/request (client/connect (str "http://127.0.0.1:" (:port s) "/mcp?userToken=k"))
                                           2 "tools/list" {})
                           [:result :tools])
            delete (first (filter #(= "delete_file" (:name %)) listed))]
        (is (= {:readOnlyHint false :destructiveHint true :idempotentHint true :openWorldHint false}
               (select-keys (:annotations delete) [:readOnlyHint :destructiveHint :idempotentHint :openWorldHint]))))
      (finally (server/stop! s)))))

(def list-tools
  #{"list_teams" "list_projects" "list_files" "search_files" "list_snapshots" "list_shapes" "search_shapes"
    "list_components" "get_component_instances" "get_colors" "get_typographies" "list_comments" "list_media"
    "list_fonts" "list_webhooks"})

(deftest list-tools-are-paged
  (doseq [t (filter (comp list-tools :name) tools/all)
          k ["limit" "cursor"]]
    (is (contains? (get (tool/json-schema (:input-schema t)) "properties") k) (str (:name t) "." k))))

(deftest page-data-tool-is-gone
  (is (not-any? #(= "get_page_data" (:name %)) tools/all)))

(deftest every-tool-belongs-to-a-toolset
  (is (every? #(contains? #{"read" "edit" "manage" "export" "import"} (:toolset %)) tools/all))
  (is (= {"get_shape" "read" "list_comments" "read" "get_active_users" "read"
          "create_rect" "edit" "create_comment" "edit" "update_comment" "edit" "create_page" "edit"
          "upload_media_from_url" "edit" "set_token" "edit" "create_token" "edit"
          "create_file" "manage" "delete_file" "manage" "create_snapshot" "manage" "list_webhooks" "manage"
          "export_shape" "export"}
         (into {} (keep (fn [t] (when (#{"get_shape" "list_comments" "get_active_users" "create_rect" "create_comment"
                                         "update_comment" "create_page" "upload_media_from_url" "set_token" "create_token"
                                         "create_file" "delete_file" "create_snapshot" "list_webhooks" "export_shape"}
                                       (:name t))
                                  [(:name t) (:toolset t)])))
               tools/all)))
  (is (every? (comp seq tools/toolset-summaries) ["read" "edit" "manage" "export" "import"])))
