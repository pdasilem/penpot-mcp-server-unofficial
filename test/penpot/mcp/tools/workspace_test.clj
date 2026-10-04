(ns penpot.mcp.tools.workspace-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.files :as files]
   [penpot.mcp.tools.profile :as profile]
   [penpot.mcp.tools.projects :as projects]))

(def team-id (parse-uuid "77777777-0000-0000-0000-000000000001"))
(def project-id (parse-uuid "66666666-0000-0000-0000-000000000001"))
(def now (java.time.Instant/parse "2026-10-01T10:00:00Z"))

(deftest get-profile-returns-identity-and-defaults
  (let [ctx (fx/ctx {:get-profile {:id team-id :email "a@b.c" :fullname "Ann" :default-team-id team-id
                                   :default-project-id project-id :props {:secret "x"}}})]
    (is (= {"id" (str team-id) "email" "a@b.c" "fullname" "Ann"
            "default_team_id" (str team-id) "default_project_id" (str project-id)}
           (fx/call (fx/find-tool profile/tools "get_profile") ctx {})))))

(deftest list-teams-returns-brief-teams
  (let [ctx (fx/ctx {:get-teams [{:id team-id :name "Default" :is-default true :permissions {:x 1}}]})]
    (is (= {"teams" [{"id" (str team-id) "name" "Default" "is_default" true}]}
           (fx/call (fx/find-tool projects/tools "list_teams") ctx {})))))

(deftest list-projects-for-team-or-all
  (let [project {:id project-id :name "Web" :team-id team-id :is-default false :modified-at now :extra 1}
        ctx     (fx/ctx {:get-projects (fn [p] (is (= team-id (:team-id p))) [project])
                         :get-all-projects [project]})
        expected {"projects" [{"id" (str project-id) "name" "Web" "team_id" (str team-id)
                               "is_default" false "modified_at" "2026-10-01T10:00:00Z"}]}]
    (is (= expected (fx/call (fx/find-tool projects/tools "list_projects") ctx {"team_id" (str team-id)})))
    (is (= expected (fx/call (fx/find-tool projects/tools "list_projects") ctx {})))))

(deftest list-files-of-project
  (let [ctx (fx/ctx {:get-project-files (fn [p] (is (= project-id (:project-id p)))
                                          [{:id fx/file-id :name "Login" :is-shared false :revn 3 :modified-at now :data {}}])})]
    (is (= {"files" [{"id" (str fx/file-id) "name" "Login" "is_shared" false "revn" 3 "modified_at" "2026-10-01T10:00:00Z"}]}
           (fx/call (fx/find-tool files/tools "list_files") ctx {"project_id" (str project-id)})))))

(deftest search-files-passes-term
  (let [ctx (fx/ctx {:search-files (fn [p]
                                     (is (= {:team-id team-id :search-term "log"} p))
                                     [{:id fx/file-id :name "Login" :project-id project-id :is-shared false :modified-at now}])})]
    (is (= "Login" (get-in (fx/call (fx/find-tool files/tools "search_files") ctx {"team_id" (str team-id) "query" "log"})
                           ["files" 0 "name"])))))

(deftest get-file-summarizes-pages-and-libraries
  (let [result (fx/call (fx/find-tool files/tools "get_file") (fx/ctx (fx/file-responses fx/file)) {"file_id" (str fx/file-id)})]
    (is (= "Login" (get result "name")))
    (is (= 12 (get result "revn")))
    (is (= [{"id" (str fx/page-id) "name" "Screens" "shape_count" 6}
            {"id" (str fx/page2-id) "name" "Archive" "shape_count" 0}]
           (get result "pages")))
    (is (= {"components" 1 "colors" 1 "typographies" 1 "token_sets" 1 "media" 1} (get result "counts")))))

(deftest get-file-reads-pages-and-library-from-the-open-editor
  (let [ctx    (fx/plugin-ctx (fn [code]
                                (if (str/includes? code "shapeCount")
                                  [{:id (str fx/page-id) :name "Screens" :shapeCount 6}
                                   {:id (str fx/page2-id) :name "Archive" :shapeCount 0}]
                                  {:components 1 :colors 1 :typographies 1 :tokenSets 1}))
                              (fx/file-responses fx/file))
        result (fx/call (fx/find-tool files/tools "get_file") ctx {"file_id" (str fx/file-id)})]
    (is (= {"id" (str fx/file-id) "name" "Login" "project_id" "66666666-0000-0000-0000-000000000001"
            "team_id" "77777777-0000-0000-0000-000000000001" "revn" 12 "vern" 0 "is_shared" false
            "pages" [{"id" (str fx/page-id) "name" "Screens" "shape_count" 6}
                     {"id" (str fx/page2-id) "name" "Archive" "shape_count" 0}]
            "counts" {"components" 1 "colors" 1 "typographies" 1 "token_sets" 1}}
           result))
    (is (not-any? #{:get-file :get-file-stats} (fx/rpc-commands ctx)))))

(deftest get-file-of-large-file-without-editor-gives-counts-only
  (let [ctx    (assoc-in (fx/closed-editor-ctx (assoc (fx/file-responses fx/file)
                                                      :get-file-stats {:revn 12 :shape-counts {:total 6}
                                                                       :component-count 1 :color-count 1
                                                                       :typography-count 1}))
                         [:config :full-file-shapes-max] 5)
        result (fx/call (fx/find-tool files/tools "get_file") ctx {"file_id" (str fx/file-id)})]
    (is (= {"id" (str fx/file-id) "name" "Login" "project_id" "66666666-0000-0000-0000-000000000001"
            "team_id" "77777777-0000-0000-0000-000000000001" "revn" 12 "vern" 0 "is_shared" false
            "counts" {"components" 1 "colors" 1 "typographies" 1}}
           result))
    (is (not-any? #{:get-file} (fx/rpc-commands ctx)))))

(deftest get-file-libraries-returns-linked-libraries
  (let [lib-id (parse-uuid "aaaaaaaa-0000-0000-0000-000000000001")
        ctx    (fx/ctx {:get-file-libraries [{:id lib-id :name "UI Kit" :project-id project-id :is-shared true :data {}}]})]
    (is (= {"libraries" [{"id" (str lib-id) "name" "UI Kit" "project_id" (str project-id) "is_shared" true}]}
           (fx/call (fx/find-tool files/tools "get_file_libraries") ctx {"file_id" (str fx/file-id)})))))
