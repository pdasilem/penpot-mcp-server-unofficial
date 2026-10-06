(ns penpot.mcp.recording.server
  (:require
   [penpot.mcp.penpot.uuid :as uuid]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [penpot.mcp.app :as app]
   [penpot.mcp.html.jobs :as jobs]
   [penpot.mcp.html.uploads :as uploads]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.notifications :as notifications]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.penpot.transit :as transit]
   [penpot.mcp.plugin.bridge :as bridge]
   [penpot.mcp.tool :as tool]))

(def ^:private lock (Object.))

(def ^:dynamic *request* nil)

(defn- record! [log entry]
  (locking lock
    (with-open [w (io/writer log :append true)]
      (binding [*print-length* nil *print-level* nil *print-namespace-maps* false]
        (.write w (pr-str (assoc entry :thread (.getName (Thread/currentThread)))))
        (.write w "\n")))))

(defn- wrap-send [send]
  (fn [cmd params]
    (binding [*request* {:cmd cmd :params (transit/encode (or params {}))}]
      (send cmd params))))

(defn- wrap-client [make]
  (fn [opts]
    (update (make opts) :send wrap-send)))

(defn- wrap-response [log response->result]
  (fn [cmd status body]
    (record! log (merge {:kind :rpc :cmd cmd :status status :body body} *request*))
    (response->result cmd status body)))

(defn- wrap-execute [log execute!]
  (fn [bridge code]
    (try
      (let [result (execute! bridge code)]
        (record! log {:kind :plugin :code code :result-json (json/write-str result)})
        result)
      (catch clojure.lang.ExceptionInfo e
        (record! log {:kind :plugin :code code :error (ex-message e) :data (ex-data e)})
        (throw e)))))

(defn- wrap-invoke [log invoke]
  (fn [tool-def ctx args]
    (record! log {:kind :tool-start :tool (:name tool-def) :args args
                  :unsaved (vec (some-> ctx :persistence :dirty deref))})
    (let [result (invoke tool-def ctx args)]
      (record! log {:kind :tool-end :tool (:name tool-def) :result result})
      result)))

(defn- wrap-uuid [log next-id]
  (fn []
    (let [id (next-id)]
      (record! log {:kind :uuid :value id})
      id)))

(defn- wrap-presence [log presence-sessions]
  (fn [messages file-id own-session-id]
    (record! log {:kind :presence :messages (vec messages) :own-session-id own-session-id})
    (presence-sessions messages file-id own-session-id)))

(defn- wrap-upload-text [log text]
  (fn [store id]
    (let [t (text store id)]
      (record! log {:kind :upload :id id :text t})
      t)))

(defn- synchronous-start [ctx id]
  (jobs/run! ctx id)
  nil)

(defn install! [log]
  (alter-var-root #'rpc/client wrap-client)
  (alter-var-root #'rpc/session-client wrap-client)
  (alter-var-root #'rpc/response->result #(wrap-response log %))
  (alter-var-root #'bridge/execute! #(wrap-execute log %))
  (alter-var-root #'tool/invoke #(wrap-invoke log %))
  (alter-var-root #'uuid/next #(wrap-uuid log %))
  (alter-var-root #'file/cached (constantly (fn [_ _ load] (load))))
  (alter-var-root #'notifications/presence-sessions #(wrap-presence log %))
  (alter-var-root #'uploads/text #(wrap-upload-text log %))
  (alter-var-root #'jobs/start! (constantly synchronous-start)))

(defn -main [& _]
  (install! (System/getenv "RECORD_LOG"))
  (app/run (System/getenv)))
