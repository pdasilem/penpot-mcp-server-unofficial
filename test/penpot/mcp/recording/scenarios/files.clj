(ns penpot.mcp.recording.scenarios.files)

(defn- file-only [f] {"file_id" (:fid f)})

(def scenarios
  [{:name "files/get-file" :tool "get_file" :args file-only}
   {:name "files/get-file-large" :tool "get_file" :file :source :args file-only}
   {:name "files/libraries" :tool "get_file_libraries" :args file-only}
   {:name "files/list-files" :tool "list_files" :args #(hash-map "project_id" (str (get-in % [:file :project-id])))}
   {:name "files/search-files" :tool "search_files" :args #(hash-map "team_id" (str (get-in % [:file :team-id])) "query" "design")}
   {:name "files/list-projects" :tool "list_projects" :args #(hash-map "team_id" (str (get-in % [:file :team-id])))}
   {:name "files/list-teams" :tool "list_teams" :args (constantly {})}
   {:name "files/profile" :tool "get_profile" :args (constantly {})}
   {:name "files/get-file-editor" :tool "get_file" :editor true :args file-only}])
