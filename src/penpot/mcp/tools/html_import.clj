(ns penpot.mcp.tools.html-import
  (:require
   [penpot.mcp.html.bundle :as bundle]
   [penpot.mcp.html.cascade :as cascade]
   [penpot.mcp.html.frames :as frames]
   [penpot.mcp.html.jobs :as jobs]
   [penpot.mcp.html.uploads :as uploads]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common])
  (:import
   (org.jsoup Jsoup)))

(def ^:private default-viewport 1440)

(def ^:private job-param
  [:job_id {:description "Job id from import_html"} :uuid])

(defn- start-import [{:keys [uploads] :as ctx} {:keys [file_id upload_id frame_selector section_selector page_id viewport_width font_family]}]
  (let [id       (str upload_id)
        text     (or (uploads/text uploads id)
                     (throw (tool/user-error (str "Upload " id " not found or expired; upload the file again"))))
        viewport (or viewport_width default-viewport)
        {:keys [html pages]} (bundle/unpack text)
        doc      (Jsoup/parse ^String html)
        computed (cascade/compute doc {:viewport viewport})
        plan     (frames/plan doc {:frame-selector frame_selector :section-selector section_selector})
        _        (when (empty? plan)
                   (throw (tool/user-error (str "No element matches frame_selector " frame_selector))))
        job      (jobs/create! ctx {:file-id file_id :plan plan :computed computed
                                    :opts {:viewport viewport :font-family (or font_family "sourcesanspro") :page-id page_id}
                                    :on-done #(uploads/remove! uploads id)})]
    (jobs/start! ctx (:id job))
    (tool/json-result (cond-> {:job_id (:id job)
                               :frames (count plan)
                               :sections (vec (distinct (keep :section plan)))}
                        (pos? pages) (assoc :skipped_nested_pages pages)))))

(def tools
  [{:name "import_html"
    :description (str "Start importing a static HTML design, such as a Claude Design export, into a Penpot file as native boards with flex and grid layouts, text, fills, strokes and shadows. "
                      "Upload the file first, without passing it through the model: curl --data-binary @design.html \"<MCP URL>&upload=html\" returns upload_id. "
                      "Each element matching frame_selector becomes a board; with section_selector each section heading starts a new page named after it. "
                      "Scripts are ignored. The import runs in the background frame by frame; poll get_import_status. Returns the job id and the number of frames and sections."
                      canvas/editor-note)
    :annotations tool/external
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:upload_id {:description "upload_id returned by the upload"} :uuid]
                   [:frame_selector {:optional true :description "CSS selector of the frames, e.g. .screen; without it the whole page is one board"} [:string {:min 1 :max 500}]]
                   [:section_selector {:optional true :description "CSS selector of section headings, e.g. h2; each section goes to a new page"} [:string {:min 1 :max 500}]]
                   [:page_id {:optional true :description "Page for frames outside sections; defaults to the page open in the editor"} :uuid]
                   [:viewport_width {:optional true :description "Viewport width in pixels for percentages and media queries, default 1440"} [:int {:min 320 :max 3840}]]
                   [:font_family {:optional true :description "Font for families Penpot does not have, default sourcesanspro"} common/short-text]]
    :handler start-import}
   {:name "get_import_status"
    :description "Show the progress of an HTML import. While it runs: status (pending, running, cancelling), frames done of total and the frame in progress. Once finished (done, failed, cancelled): also the created boards with their pages, the error and failed frame, unsupported CSS with counts and fonts replaced by the fallback. Poll at most once a minute."
    :annotations tool/read-only
    :input-schema [:map {:closed true} job-param]
    :handler (fn [ctx {:keys [job_id]}] (tool/json-result (jobs/status ctx (str job_id))))}
   {:name "cancel_import"
    :description "Stop an HTML import after the frame in progress; created boards stay. Returns the job status."
    :annotations tool/overwrite
    :input-schema [:map {:closed true} job-param]
    :handler (fn [ctx {:keys [job_id]}]
               (let [id (str job_id)]
                 (when (#{"pending" "running"} (:status (jobs/status ctx id))) (jobs/cancel! ctx id))
                 (tool/json-result (select-keys (jobs/status ctx id) [:job_id :status :frames_done :frames_total]))))}
   {:name "resume_import"
    :description "Continue a failed or cancelled HTML import from its first frame that was not created, for example after opening the file in the editor again. Returns the job status."
    :annotations tool/additive
    :input-schema [:map {:closed true} job-param]
    :handler (fn [ctx {:keys [job_id]}]
               (let [id (str job_id)]
                 (jobs/resume! ctx id)
                 (tool/json-result (select-keys (jobs/status ctx id) [:job_id :status :frames_done :frames_total]))))}])
