(ns penpot.mcp.tools.token-catalog-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.token-catalog :as catalog]))

(def fid (str fx/file-id))
(def set-id (str fx/token-set-id))
(def token-id (str fx/token-id))
(def theme-id "88888888-0000-0000-0000-0000000000e1")

(defn- run [tool-name args]
  (let [ctx (fx/plugin-ctx {:id "x"})]
    {:result (fx/call (fx/find-tool catalog/tools tool-name) ctx (merge {"file_id" fid} args))
     :args   (when (seq @(:scripts ctx)) (fx/last-script-args ctx))
     :script (last @(:scripts ctx))}))

(deftest set-tools-send-their-arguments
  (is (= {"fileId" fid "name" "brand/dark" "active" true} (:args (run "create_token_set" {"name" "brand/dark"}))))
  (is (= {"fileId" fid "name" "core" "active" false} (:args (run "create_token_set" {"name" "core" "active" false}))))
  (is (= {"fileId" fid "setId" set-id} (:args (run "delete_token_set" {"set_id" set-id}))))
  (let [{:keys [args script]} (run "set_token_set_active" {"set_id" set-id "active" true})]
    (is (= {"fileId" fid "setId" set-id "active" true} args))
    (is (str/includes? script "if (set.active !== args.active) { set.toggleActive(); markChanged(); }"))))

(deftest create-token-passes-simple-and-composite-values
  (is (= {"fileId" fid "setId" set-id "type" "color" "name" "color.primary" "value" "#3366FF"}
         (:args (run "create_token" {"set_id" set-id "type" "color" "name" "color.primary" "value" "#3366FF"}))))
  (is (= ["Inter" "sans-serif"]
         (get (:args (run "create_token" {"set_id" set-id "type" "fontFamilies" "name" "font.body" "value" ["Inter" "sans-serif"]})) "value")))
  (is (= [{"color" "#000000" "offsetX" "0" "offsetY" "4" "blur" "8" "spread" "0" "inset" "false"}]
         (get (:args (run "create_token" {"set_id" set-id "type" "shadow" "name" "shadow.card"
                                          "value" [{"color" "#000000" "offsetX" "0" "offsetY" "4" "blur" "8" "spread" "0" "inset" "false"}]}))
              "value")))
  (is (= "Inter, {space.1}"
         (get (:args (run "create_token" {"set_id" set-id "type" "dimension" "name" "x" "value" "Inter, {space.1}" "description" "d"})) "value"))))

(deftest create-token-rejects-unknown-type
  (is (contains? (:result (run "create_token" {"set_id" set-id "type" "gradient" "name" "g" "value" "x"})) :error)))

(deftest update-token-needs-a-change
  (is (= {"fileId" fid "tokenId" token-id "value" "#000000"} (:args (run "update_token" {"token_id" token-id "value" "#000000"}))))
  (is (= {:error "Give name, value or description"} (:result (run "update_token" {"token_id" token-id}))))
  (is (= {"fileId" fid "tokenId" token-id} (:args (run "delete_token" {"token_id" token-id})))))

(deftest theme-tools-send-their-arguments
  (is (= {"fileId" fid "group" "mode" "name" "dark" "setIds" [set-id]}
         (:args (run "create_token_theme" {"group" "mode" "name" "dark" "set_ids" [set-id]}))))
  (is (= {"fileId" fid "group" "" "name" "default" "setIds" []} (:args (run "create_token_theme" {"name" "default"}))))
  (is (= {"fileId" fid "themeId" theme-id} (:args (run "delete_token_theme" {"theme_id" theme-id}))))
  (is (str/includes? (:script (run "set_token_theme_active" {"theme_id" theme-id "active" true}))
                     "if (theme.active !== args.active) { theme.toggleActive(); markChanged(); }"))
  (is (= {"fileId" fid "themeId" theme-id "setIds" [set-id]} (:args (run "set_theme_sets" {"theme_id" theme-id "set_ids" [set-id]}))))
  (is (= {"fileId" fid "themeId" theme-id "setIds" []} (:args (run "set_theme_sets" {"theme_id" theme-id "set_ids" []})))))
