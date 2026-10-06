(ns penpot.mcp.recording.record
  (:require
   [app.common.features :as cfeat]
   [app.common.uuid :as uuid]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.recording.data :as data]
   [penpot.mcp.test-client :as mcp]
   [penpot.mcp.tools.plugin-it-test :as p]))

(def ^:private out-dir "test/resources/recorded")

(def ^:private file-name "Design test data")

(def ^:private source-name "Design")

(defn- log-lines [log]
  (with-open [r (io/reader log)]
    (vec (line-seq r))))

(defn- read-entry [line]
  (edn/read-string {:default (fn [_ v] v)} line))

(defn- call-entries [lines tool]
  (let [entries (map read-entry lines)
        start   (first (filter #(and (= :tool-start (:kind %)) (= tool (:tool %))) entries))
        thread  (:thread start)
        own     (filter #(= thread (:thread %)) entries)
        inside  (->> own
                     (drop-while #(not= start %))
                     (take-while #(not= :tool-end (:kind %))))]
    {:args (:args start)
     :unsaved (:unsaved start)
     :entries (vec (map #(dissoc % :thread) (rest inside)))}))

(def ^:private blob-threshold 4096)

(defn- printed [x]
  (binding [*print-length* nil *print-level* nil *print-namespace-maps* false]
    (pr-str x)))

(defn- sha256 [^String s]
  (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" %) d))))

(defn- blob! [entry]
  (let [text (printed entry)]
    (if (< (count text) blob-threshold)
      entry
      (let [id (sha256 text)
            f  (io/file out-dir "blobs" (str id ".edn.gz"))]
        (when-not (.exists f)
          (io/make-parents f)
          (with-open [out (java.util.zip.GZIPOutputStream. (io/output-stream f))]
            (.write out (.getBytes text "UTF-8"))))
        {:blob id}))))

(defn- write! [scenario recorded]
  (let [f (io/file out-dir (str scenario ".edn"))]
    (io/make-parents f)
    (spit f (printed (-> recorded
                         (assoc :scenario scenario)
                         (update :entries #(mapv blob! %)))))))

(defn- record-one! [log session files {:keys [name tool args] :as scenario}]
  (let [before (count (log-lines log))
        file   (get files (:file scenario :data))
        answer (mcp/call-tool session tool (args file))
        after  (subvec (log-lines log) before)
        call   (call-entries after tool)]
    (when (nil? (:args call))
      (throw (ex-info (str "The server never ran " tool " for " name ": " (pr-str answer)) {})))
    (write! name (assoc call :tool tool))
    (println "recorded" name)))

(defn- find-file [client wanted]
  (let [team    (:default-team-id (rpc/call client :get-profile {}))
        project (first (filter :is-default (rpc/call client :get-projects {:team-id team})))]
    (or (first (filter #(= wanted (:name %)) (rpc/call client :get-project-files {:project-id (:id project)})))
        (throw (ex-info (str "No file named " wanted " on the stand") {})))))

(defn- fetch [client id]
  (rpc/call client :get-file {:id id :features cfeat/supported-features}))

(defn- absent-page-id [client file]
  (let [source (fetch client (:id (find-file client source-name)))
        kept   (set (get-in file [:data :pages]))]
    (str (first (remove kept (get-in source [:data :pages]))))))

(defn- absent-shape-id [client file]
  (let [source (fetch client (:id (find-file client source-name)))
        kept   (set (get-in file [:data :pages]))
        page   (first (remove kept (get-in source [:data :pages])))]
    (str (first (sort (map str (remove #{uuid/zero} (keys (get-in source [:data :pages-index page :objects])))))))))

(defn- describe [client meta]
  (let [raw (fetch client (:id meta))]
    (assoc (data/describe raw) :absent-page-id (absent-page-id client raw) :absent-shape-id (absent-shape-id client raw))))

(defn- scratch-copy [client meta]
  (let [copy (rpc/call client :duplicate-file {:file-id (:id meta) :name "Design edit scratch"})]
    (assoc (describe client copy) :client client :source-file (:source-file meta))))

(defn- record-group! [log session team-id files scenarios]
  (let [{saved false live true} (group-by (comp boolean :editor) scenarios)
        editor-file (:id (get-in files [(or (some :file live) :data) :file]))]
    (doseq [s saved] (record-one! log session files s))
    (when (seq live)
      (let [proc (@#'p/open-editor team-id editor-file)]
        (try
          (doseq [s live] (record-one! log session files s))
          (finally (@#'p/close-editor proc)))))))

(def ^:private settle-ms 20000)

(defn- settle! [client team-id meta]
  (let [proc (@#'p/open-editor team-id (:id meta))]
    (try
      (Thread/sleep (long settle-ms))
      (finally (@#'p/close-editor proc))))
  (println "settled" (:name meta) "at revision" (:revn (fetch client (:id meta)))))

(def ^:private recorded-project "Recorded project")

(defn- remove-recorded-projects! [client team-id]
  (doseq [p (rpc/call client :get-projects {:team-id team-id})
          :when (= recorded-project (:name p))]
    (rpc/call client :delete-project {:id (:id p)})))

(defn -main [log & scenario-nss]
  (let [client  (it/client)
        team-id (:default-team-id (rpc/call client :get-profile {}))
        _       (remove-recorded-projects! client team-id)
        _       (settle! client team-id (find-file client file-name))
        files   {:data (assoc (describe client (find-file client file-name)) :client client)
                 :source (assoc (data/describe (fetch client (:id (find-file client source-name)))) :client client)}
        only    (some-> (System/getenv "RECORD_ONLY") (str/split #",") set)
        all     (cond->> (mapcat (fn [n] (require (symbol n)) @(resolve (symbol n "scenarios"))) scenario-nss)
                  only (filter #(only (:name %))))
        session (mcp/connect (str p/mcp-url "?userToken=" (get it/env "PENPOT_MCP_KEY")))
        _       (doseq [g ["export" "import" "manage"]] (mcp/call-tool session "set_toolset" {"name" g "enabled" true}))
        {edits true reads false} (group-by #(= :scratch (:file %)) all)]
    (record-group! log session team-id files reads)
    (when (seq edits)
      (let [scratch (scratch-copy client (assoc (get-in files [:data :file]) :source-file (get-in files [:source :file])))]
        (try
          (record-group! log session team-id (assoc files :scratch scratch) edits)
          (finally (rpc/call client :delete-file {:id (:id (:file scratch))})))))
    (println "done" (count all) "scenarios into" out-dir (str/join " " scenario-nss))
    (shutdown-agents)
    (System/exit 0)))
