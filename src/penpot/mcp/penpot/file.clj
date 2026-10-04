(ns penpot.mcp.penpot.file
  (:require
   [app.common.features :as cfeat]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.tool :as tool]))

(defn fetch [client file-id]
  (rpc/call client :get-file {:id file-id :features cfeat/supported-features}))

(defn pages [file]
  (let [{:keys [pages pages-index]} (:data file)]
    (mapv (fn [id] {:id id :name (get-in pages-index [id :name])}) pages)))

(defn page [file page-id]
  (or (get-in file [:data :pages-index page-id])
      (throw (tool/user-error (str "Page " page-id " not found in file " (:id file))))))

(defn locate-shape [file shape-id]
  (or (some (fn [[page-id {:keys [objects]}]]
              (when-let [shape (get objects shape-id)]
                {:page-id page-id :shape shape}))
            (get-in file [:data :pages-index]))
      (throw (tool/user-error (str "Shape " shape-id " not found in file " (:id file))))))

(defn fetch-page [client file-id page-id]
  (let [found (rpc/call client :get-page (cond-> {:file-id file-id :features cfeat/supported-features}
                                           page-id (assoc :page-id page-id)))]
    (if (:id found)
      found
      (throw (tool/user-error (str "Page " page-id " not found in file " file-id))))))

(defn revision [client file-id]
  (or (some (fn [team]
              (some (fn [project]
                      (some #(when (= file-id (:id %)) %)
                            (rpc/call client :get-project-files {:project-id (:id project)})))
                    (rpc/call client :get-projects {:team-id (:id team)})))
            (rpc/call client :get-teams {}))
      (throw (tool/user-error (str "File " file-id " not found")))))

(defn read-page [{:keys [rpc] :as ctx} file-id page-id]
  (revision/await-clean! ctx file-id)
  (fetch-page rpc file-id page-id))

(defn- too-large [file-id stats limit hint]
  (ex-info (cond-> (str "File " file-id " has " (get-in stats [:shape-counts :total]) " shapes, more than the " limit
                        " this server reads at once")
             hint (str "; " hint))
           {:type :tool/user-error :reason ::too-large :stats stats}))

(defn- cached [{:keys [file-cache]} key load]
  (if-not file-cache
    (load)
    (locking file-cache
      (let [{cached-key :key value :file} @file-cache]
        (if (= key cached-key)
          value
          (do (reset! file-cache nil)
              (let [value (load)]
                (reset! file-cache {:key key :file value})
                value)))))))

(defn stats [client file-id]
  (rpc/call client :get-file-stats {:id file-id}))

(defn check-whole! [{:keys [rpc config]} file-id hint]
  (let [stats (stats rpc file-id)
        limit (:full-file-shapes-max config)]
    (when (> (get-in stats [:shape-counts :total]) limit)
      (throw (too-large file-id stats limit hint)))
    stats))

(defn read-whole
  ([ctx file-id] (read-whole ctx file-id nil))
  ([{:keys [rpc] :as ctx} file-id hint]
   (revision/await-clean! ctx file-id)
   (let [{:keys [revn updated-at]} (check-whole! ctx file-id hint)]
     (cached ctx [file-id revn updated-at] #(fetch rpc file-id)))))

(def editor-hint
  "open it in the Penpot editor with MCP enabled")

(defn editor-pages [ctx file-id]
  (read/in-editor ctx file-id read/pages-body {}))

(defn- one-by-one [f items]
  (lazy-seq
   (when-let [[item & more] (seq items)]
     (cons (f item) (one-by-one f more)))))

(defn read-pages
  ([ctx file-id] (read-pages ctx file-id editor-hint))
  ([ctx file-id hint]
   (revision/await-clean! ctx file-id)
   (if-let [{listed :value} (editor-pages ctx file-id)]
     (one-by-one #(read-page ctx file-id (parse-uuid (:id %))) listed)
     (let [f (read-whole ctx file-id hint)]
       (map #(page f (:id %)) (pages f))))))

(defn- shape-page-id [ctx file-id shape-id]
  (if-let [{found :value} (read/in-editor ctx file-id read/shape-page-body {:shape-id shape-id})]
    (or (some-> found parse-uuid)
        (throw (tool/user-error (str "Shape " shape-id " not found in file " file-id))))
    (throw (tool/user-error (str "Pass page_id, or open file " file-id " in the Penpot editor with MCP enabled")))))

(defn read-shape [ctx file-id shape-id page-id]
  (let [p     (read-page ctx file-id (or page-id (shape-page-id ctx file-id shape-id)))
        shape (get-in p [:objects shape-id])]
    (when-not shape
      (throw (tool/user-error (str "Shape " shape-id " not found on page " (:id p)))))
    {:page p :shape shape}))
