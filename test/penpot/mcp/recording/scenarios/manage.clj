(ns penpot.mcp.recording.scenarios.manage
  (:require
   [app.common.features :as cfeat]
   [app.common.types.tokens-lib :as ctob]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.recording.data :as d]))

(def ^:private project-name "Recorded project")

(def ^:private wait-ms 20000)

(defn- eventually [f]
  (let [until (+ (System/currentTimeMillis) wait-ms)]
    (loop []
      (let [r (try {:value (f)} (catch clojure.lang.ExceptionInfo e (if (< (System/currentTimeMillis) until) nil (throw e))))]
        (if r (:value r) (do (Thread/sleep 500) (recur)))))))

(defn- call [f cmd params]
  (rpc/call (:client f) cmd params))

(defn- current-lib [f]
  (get-in (call f :get-file {:id (parse-uuid (:fid f)) :features cfeat/supported-features}) [:data :tokens-lib]))

(defn- named [xs pick-name want]
  (some (fn [x] (when (= want (pick-name x)) x)) xs))

(defn- all-tokens [lib]
  (mapcat (fn [st] (vals (ctob/get-tokens lib (ctob/get-id st)))) (ctob/get-sets lib)))

(defn- set-id [f set-name]
  (eventually (fn [] (str (ctob/get-id (or (named (ctob/get-sets (current-lib f)) ctob/get-name set-name)
                                           (throw (ex-info (str "No token set " set-name " yet") {}))))))))

(defn- token-id [f token-name]
  (eventually (fn [] (str (:id (or (named (all-tokens (current-lib f)) :name token-name)
                                   (throw (ex-info (str "No token " token-name " yet") {}))))))))

(defn- theme-id [f theme-name]
  (eventually (fn [] (str (:id (or (named (ctob/get-themes (current-lib f)) :name theme-name)
                                   (throw (ex-info (str "No theme " theme-name " yet") {}))))))))

(defn- team-id [f] (str (get-in f [:file :team-id])))

(defn- project-id [f]
  (or (some #(when (= project-name (:name %)) (str (:id %))) (call f :get-projects {:team-id (parse-uuid (team-id f))}))
      (throw (ex-info "No recorded project yet" {}))))

(defn- file-in-project [f file-name]
  (or (some #(when (= file-name (:name %)) (str (:id %))) (call f :get-project-files {:project-id (parse-uuid (project-id f))}))
      (throw (ex-info (str "No file " file-name " yet") {}))))

(defn- page-id [f file-id page-name]
  (eventually (fn []
                (let [file (call f :get-file {:id (parse-uuid file-id) :features cfeat/supported-features})]
                  (str (or (named (get-in file [:data :pages]) (fn [id] (get-in file [:data :pages-index id :name])) page-name)
                           (throw (ex-info (str "No page " page-name " yet") {}))))))))

(defn- threads [f]
  (call f :get-comment-threads {:file-id (parse-uuid (:fid f))}))

(defn- thread-id [f]
  (eventually (fn [] (str (:id (or (first (sort-by :created-at (threads f))) (throw (ex-info "No comment thread yet" {}))))))))

(defn- comment-ids [f]
  (eventually (fn []
                (let [ids (map (comp str :id) (sort-by :created-at (call f :get-comments {:thread-id (parse-uuid (thread-id f))})))]
                  (if (< (count ids) 2) (throw (ex-info "The reply is not saved yet" {})) ids)))))

(defn- snapshot-id [f file-id]
  (str (:id (first (call f :get-file-snapshots {:file-id (parse-uuid file-id)})))))

(defn- base [f] {"file_id" (:fid f)})

(defn- s [name tool args] {:name (str "manage/" name) :tool tool :file :scratch :editor true :args args})

(def scenarios
  [(s "token-set" "create_token_set" #(merge (base %) {"name" "Recorded set" "active" true}))
   (s "token-set-again" "create_token_set" #(merge (base %) {"name" "Recorded set"}))
   (s "token" "create_token" #(merge (base %) {"set_id" (set-id % "Recorded set") "type" "spacing" "name" "recorded.space" "value" "8"}))
   (s "token-composite" "create_token" #(merge (base %) {"set_id" (set-id % "Recorded set") "type" "typography" "name" "recorded.type"
                                                         "value" {"fontFamilies" ["Work Sans"] "fontSizes" "16" "fontWeight" "400"}}))
   (s "token-again" "create_token" #(merge (base %) {"set_id" (set-id % "Recorded set") "type" "typography" "name" "recorded.type"
                                                     "value" {"fontFamilies" ["Work Sans"] "fontSizes" "16" "fontWeight" "400"}}))
   (s "token-update" "update_token" #(merge (base %) {"token_id" (token-id % "recorded.type") "description" "Recorded description"}))
   (s "token-set-inactive" "set_token_set_active" #(merge (base %) {"set_id" (set-id % "Recorded set") "active" false}))
   (s "theme" "create_token_theme" #(merge (base %) {"group" "Recorded" "name" "Recorded theme" "set_ids" [(set-id % "Recorded set")]}))
   (s "theme-sets" "set_theme_sets" #(merge (base %) {"theme_id" (theme-id % "Recorded theme") "set_ids" [(set-id % "Recorded set")]}))
   (s "theme-active" "set_token_theme_active" #(merge (base %) {"theme_id" (theme-id % "Recorded theme") "active" true}))
   (s "token-delete" "delete_token" #(merge (base %) {"token_id" (token-id % "recorded.type")}))
   (s "theme-delete" "delete_token_theme" #(merge (base %) {"theme_id" (theme-id % "Recorded theme")}))
   (s "token-set-delete" "delete_token_set" #(merge (base %) {"set_id" (set-id % "Recorded set")}))
   (s "comment" "create_comment" #(merge (base %) {"page_id" (d/page-id % "Model") "x" 40 "y" 40 "content" "Recorded comment"}))
   (s "comment-reply" "reply_comment" #(hash-map "thread_id" (thread-id %) "content" "Recorded reply"))
   (s "comment-update" "update_comment" #(hash-map "comment_id" (second (comment-ids %)) "content" "Recorded reply, edited"))
   (s "comment-resolve" "resolve_comment" #(hash-map "thread_id" (thread-id %)))
   (s "comments" "list_comments" base)
   (s "comments-resolved" "list_comments" #(merge (base %) {"resolved" true}))
   (s "comment-delete" "delete_comment" #(hash-map "comment_id" (second (comment-ids %))))
   (s "comment-thread-delete" "delete_comment_thread" #(hash-map "thread_id" (thread-id %)))
   (s "snapshot" "create_snapshot" #(merge (base %) {"label" "Recorded snapshot"}))
   (s "snapshots" "list_snapshots" base)
   (s "snapshot-compare" "compare_snapshots" #(merge (base %) {"from_snapshot_id" (snapshot-id % (:fid %))}))
   (s "page" "create_page" #(merge (base %) {"name" "Recorded page"}))
   (s "page-rename" "rename_page" #(merge (base %) {"page_id" (page-id % (:fid %) "Recorded page") "name" "Recorded page renamed"}))
   (s "page-delete" "delete_page" #(merge (base %) {"page_id" (page-id % (:fid %) "Recorded page renamed")}))
   (s "project" "create_project" #(hash-map "team_id" (team-id %) "name" project-name))
   (s "project-rename" "rename_project" #(hash-map "project_id" (project-id %) "name" project-name))
   (s "file" "create_file" #(hash-map "project_id" (project-id %) "name" "Recorded file"))
   (s "file-rename" "rename_file" #(hash-map "file_id" (file-in-project % "Recorded file") "name" "Recorded file renamed"))
   (s "file-duplicate" "duplicate_file" #(hash-map "file_id" (file-in-project % "Recorded file renamed") "name" "Recorded copy"))
   (s "api-page" "create_page" #(hash-map "file_id" (file-in-project % "Recorded file renamed") "name" "Recorded api page"))
   (s "api-page-rename" "rename_page" #(let [fid (file-in-project % "Recorded file renamed")] (hash-map "file_id" fid "page_id" (page-id % fid "Recorded api page") "name" "Recorded api page renamed")))
   (s "api-page-delete" "delete_page" #(let [fid (file-in-project % "Recorded file renamed")] (hash-map "file_id" fid "page_id" (page-id % fid "Recorded api page renamed"))))
   (s "last-page-delete" "delete_page" #(let [fid (file-in-project % "Recorded copy")
                                              file (call % :get-file {:id (parse-uuid fid) :features cfeat/supported-features})]
                                          (hash-map "file_id" fid "page_id" (str (first (get-in file [:data :pages]))))))
   (s "file-delete" "delete_file" #(hash-map "file_id" (file-in-project % "Recorded copy")))
   (s "media" "list_media" base)
   (s "media-large-file" "list_media" #(hash-map "file_id" (str (:id (get-in % [:source-file])))))
   (s "fonts" "list_fonts" #(hash-map "team_id" (team-id %)))
   (s "webhooks" "list_webhooks" #(hash-map "team_id" (team-id %)))
   (s "active-users" "get_active_users" base)])
