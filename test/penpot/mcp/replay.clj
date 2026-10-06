(ns penpot.mcp.replay
  (:require
   [penpot.mcp.penpot.uuid :as uuid]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [penpot.mcp.config :as config]
   [penpot.mcp.exports :as exports]
   [penpot.mcp.html.jobs :as jobs]
   [penpot.mcp.html.uploads :as uploads]
   [penpot.mcp.penpot.notifications :as notifications]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.penpot.transit :as transit]
   [penpot.mcp.tool :as tool])
  (:import
   (java.util.concurrent.locks ReentrantLock)))

(def ^:private dir "recorded/")

(defn- read-edn [text]
  (edn/read-string {:default (fn [_ v] v)} text))

(defn- blob [id]
  (let [res (or (io/resource (str dir "blobs/" id ".edn.gz"))
                (throw (ex-info (str "Missing recorded blob " id) {})))]
    (with-open [in (java.util.zip.GZIPInputStream. (io/input-stream res))]
      (read-edn (slurp in :encoding "UTF-8")))))

(defn- resolved [entry]
  (if-let [id (:blob entry)] (blob id) entry))

(defn recording [scenario]
  (let [res (or (io/resource (str dir scenario ".edn"))
                (throw (ex-info (str "No recording for scenario " scenario "; run the recorder against the stand") {})))]
    (update (read-edn (slurp res)) :entries #(mapv resolved %))))

(defn- comparable [params]
  (dissoc (transit/decode (transit/encode (or params {}))) :session-id))

(defn- take-entry! [pending kind matches?]
  (let [found (atom nil)]
    (swap! pending (fn [es]
                     (let [[before [hit & after]] (split-with #(not (and (= kind (:kind %)) (matches? %))) es)]
                       (reset! found hit)
                       (if hit (into (vec before) after) es))))
    @found))

(defn- unexpected [what detail]
  (throw (ex-info (str "The recording has no " what "; record the scenario again") {:replay/missing detail})))

(defn- rpc-send [pending]
  (fn [cmd params]
    (let [wanted (comparable params)]
      (if-let [{:keys [status body]} (take-entry! pending :rpc #(and (= cmd (:cmd %)) (= wanted (comparable (transit/decode (:params %))))))]
        (rpc/response->result cmd status body)
        (unexpected (str "Penpot " (name cmd) " request with these parameters") {:cmd cmd :params params})))))

(defn- plugin-result [{:keys [result result-json]}]
  (if result-json (json/read-str result-json :key-fn keyword) result))

(defn- execute [pending]
  (fn [code]
    (if-let [{:keys [error data] :as entry} (take-entry! pending :plugin #(= code (:code %)))]
      (if error (throw (ex-info error (or data {}))) (plugin-result entry))
      (unexpected "editor script like this one" {:code code}))))

(def ^:private replay-env
  {"PENPOT_BASE_URL" "http://penpot.replay"
   "PENPOT_ACCESS_TOKEN" "replay"
   "PENPOT_EMAIL" "replay@penpot.replay"
   "PENPOT_PASSWORD" "replay"
   "PENPOT_MCP_KEY" "replay-replay-replay-replay-replay-replay"})

(defn- presence [pending]
  (fn [file-id]
    (take-entry! pending :rpc #(= :login-with-password (:cmd %)))
    (if-let [{:keys [messages own-session-id]} (take-entry! pending :presence (constantly true))]
      (notifications/presence-sessions messages file-id own-session-id)
      (unexpected "presence messages" {:file-id file-id}))))

(defn ctx [pending]
  {:config (config/load-config replay-env)
   :presence (presence pending)
   :rpc {:session-id (uuid/next) :send (rpc-send pending)}
   :execute (execute pending)
   :persistence {:dirty (atom #{})}
   :file-cache (atom nil)
   :plugin-lock {:lock (ReentrantLock.) :wait-ms 1000}
   :uploads (uploads/store {:now #(System/currentTimeMillis)})
   :exports (exports/store {:now #(System/currentTimeMillis)})
   :import-jobs (atom {})
   :version-error (constantly nil)})

(defn- upload-text [pending]
  (fn [_ id]
    (if-let [{:keys [text]} (take-entry! pending :upload #(= id (:id %)))]
      text
      (unexpected "an upload" {:id id}))))

(defn- uuid-source [entries]
  (let [ids (atom (map :value (filter #(= :uuid (:kind %)) entries)))]
    (fn []
      (if-let [id (first @ids)]
        (do (swap! ids rest) id)
        (unexpected "more generated ids" {})))))

(defn run [tool-def scenario]
  (let [{:keys [args entries unsaved]} (recording scenario)
        pending (atom (vec (remove #(= :uuid (:kind %)) entries)))
        context (assoc-in (ctx pending) [:persistence :dirty] (atom (set unsaved)))
        result  (with-redefs [uuid/next    (uuid-source entries)
                              uploads/text (upload-text pending)
                              jobs/start!  (fn [ctx id] (jobs/run! ctx id) nil)]
                  (tool/invoke tool-def context args))]
    {:result result
     :left @pending
     :ctx context}))

(defn data [{:keys [result]}]
  (let [{:keys [type text] :as content} (get-in result [:content 0])]
    (cond
      (:error? result) {:error text}
      (= :image type) {:image (select-keys content [:data :mime-type])}
      :else (json/read-str text))))

(defn- unzip [^bytes data]
  (with-open [z (java.util.zip.ZipInputStream. (java.io.ByteArrayInputStream. data))]
    (loop [acc {}]
      (if-let [e (.getNextEntry z)]
        (recur (assoc acc (.getName e) (String. (.readAllBytes z) "UTF-8")))
        acc))))

(defn full-data [{:keys [ctx] :as replayed}]
  (let [answer (data replayed)
        link   (get-in answer ["full_result" "download"])]
    (if-not link
      answer
      (let [id      (second (re-find #"export=([0-9a-f]{32})" link))
            entries (unzip (exports/take! (:exports ctx) id))
            archived (json/read-str (val (first entries)))]
        (merge (dissoc answer "full_result" "archived_sections") archived)))))

(defn penpot-answers [scenario cmd]
  (for [{:keys [kind status body] :as e} (:entries (recording scenario))
        :when (and (= :rpc kind) (= cmd (:cmd e)))]
    (rpc/response->result cmd status body)))

(defn editor-answers [scenario]
  (for [{:keys [kind] :as e} (:entries (recording scenario))
        :when (= :plugin kind)]
    (plugin-result e)))

(defn requests [scenario]
  (for [{:keys [kind cmd]} (:entries (recording scenario))
        :when (= :rpc kind)]
    cmd))

(defn run-without-penpot [tool-def args]
  (let [pending (atom [])]
    {:result (tool/invoke tool-def (ctx pending) args)
     :left []}))

(defn script-args [code]
  (json/read-str (second (re-find #"(?s)^const args = (.*?);\n" code))))

(defn editor-scripts [scenario]
  (for [{:keys [kind code]} (:entries (recording scenario))
        :when (= :plugin kind)]
    code))

(defn context [& scenarios]
  (let [entries (mapcat (comp :entries recording) scenarios)
        pending (atom (vec (remove #(= :uuid (:kind %)) entries)))]
    (assoc (ctx pending) :replay/pending pending)))
