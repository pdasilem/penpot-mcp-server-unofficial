(ns penpot.mcp.tools.workspace-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.real-file :as real]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools.edit-support :as e]))

(defn- answer [scenario cmd]
  (first (replay/penpot-answers scenario cmd)))

(deftest the-profile-gives-identity-and-defaults
  (let [profile (e/run "files/profile")
        saved   (answer "files/profile" :get-profile)]
    (is (= (str (:id saved)) (get profile "id")))
    (is (= (str (:default-team-id saved)) (get profile "default_team_id")))))

(deftest teams-projects-and-files-come-from-penpot
  (is (= (count (answer "files/list-teams" :get-teams)) (count (get (e/run "files/list-teams") "teams"))))
  (is (= (min 100 (count (answer "files/list-projects" :get-projects))) (count (get (e/run "files/list-projects") "projects"))))
  (is (= (set (map (comp str :id) (answer "files/list-files" :get-project-files)))
         (set (map #(get % "id") (get (e/run "files/list-files") "files")))))
  (is (every? #(str/includes? (str/lower-case (get % "name")) "design") (get (e/run "files/search-files") "files"))))

(deftest a-file-summary-lists-its-pages-and-library
  (let [summary (e/run "files/get-file")]
    (is (= (map :name (map #(get-in (real/file) [:data :pages-index %]) (get-in (real/file) [:data :pages])))
           (map #(get % "name") (get summary "pages"))))
    (is (= (count (remove :deleted (vals (get-in (real/file) [:data :components])))) (get-in summary ["counts" "components"])))))

(deftest the-open-editor-gives-the-same-pages
  (is (= (map #(get % "name") (get (e/run "files/get-file") "pages"))
         (map #(get % "name") (get (e/run "files/get-file-editor") "pages")))))

(deftest a-large-file-without-the-editor-gives-counts-only
  (let [summary (e/run "files/get-file-large")]
    (is (not (contains? summary "pages")))
    (is (map? (get summary "counts")))))

(deftest linked-libraries-come-from-penpot
  (is (= (count (answer "files/libraries" :get-file-libraries)) (count (get (e/run "files/libraries") "libraries")))))
