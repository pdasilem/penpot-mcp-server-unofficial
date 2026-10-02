(ns penpot.mcp.system
  (:require
   [clojure.tools.logging :as log]
   [penpot.mcp.penpot.notifications :as notifications]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.penpot.version :as version]
   [penpot.mcp.plugin.bridge :as bridge]
   [penpot.mcp.server :as server])
  (:import
   (java.net URI)
   (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers)
   (java.time Duration)
   (java.util.concurrent.locks ReentrantLock)))

(def ^:private index-timeout (Duration/ofSeconds 10))
(def ^:private plugin-task-timeout-ms 30000)
(def ^:private presence-wait-ms 1500)
(def ^:private plugin-lock-wait-ms 60000)

(defn- index-fetcher [base-url]
  (let [client (-> (HttpClient/newBuilder) (.connectTimeout index-timeout) (.build))
        req    (-> (HttpRequest/newBuilder (URI/create (str base-url "/")))
                   (.timeout index-timeout)
                   (.GET)
                   (.build))]
    (fn []
      (let [resp (.send client req (HttpResponse$BodyHandlers/ofString))]
        (if (= 200 (.statusCode resp))
          (.body resp)
          (throw (ex-info (str "Penpot index returned HTTP " (.statusCode resp)) {})))))))

(defn- stop-logged! [k stop value]
  (try
    (stop value)
    (catch Throwable t
      (log/warn t "Failed to stop component" k))))

(defn- start-all! [steps]
  (reduce (fn [started [k start stop]]
            (try
              (conj started [k (start (into {} (map (juxt first second)) started)) stop])
              (catch Throwable t
                (doseq [[started-k value stop-fn] (reverse started)]
                  (stop-logged! started-k stop-fn value))
                (throw t))))
          []
          steps))

(defn start! [cfg {:keys [tools instructions toolset-summaries fetch-index]}]
  (let [version-state (atom {})
        rpc-client    (rpc/client {:base-url (:penpot-base-url cfg) :token (:penpot-access-token cfg)})
        started       (start-all!
                       [[:checker
                         (fn [_] (version/start-checker! version-state
                                                         (or fetch-index (index-fetcher (:penpot-base-url cfg)))
                                                         (* 1000 (:version-check-interval cfg))))
                         version/stop-checker!]
                        [:bridge
                         (fn [_] (bridge/start! {:host (:ws-host cfg)
                                                 :port (:ws-port cfg)
                                                 :mcp-key (:penpot-mcp-key cfg)
                                                 :task-timeout-ms plugin-task-timeout-ms}))
                         bridge/stop!]
                        [:mcp
                         (fn [{:keys [bridge]}]
                           (server/start! {:host (:mcp-host cfg)
                                           :port (:mcp-port cfg)
                                           :mcp-key (:penpot-mcp-key cfg)
                                           :tools tools
                                           :instructions instructions
                                           :toolsets (:toolsets cfg)
                                           :toolset-summaries toolset-summaries
                                           :ctx {:config cfg
                                                 :rpc rpc-client
                                                 :presence (notifications/presence-fn {:base-url (:penpot-base-url cfg)
                                                                                       :email (:penpot-email cfg)
                                                                                       :password (:penpot-password cfg)
                                                                                       :wait-ms presence-wait-ms})
                                                 :bridge bridge
                                                 :execute #(bridge/execute! bridge %)
                                                 :persistence {:dirty (atom #{})}
                                                 :plugin-lock {:lock (ReentrantLock.) :wait-ms plugin-lock-wait-ms}
                                                 :version-error #(version/check-error @version-state)}}))
                         server/stop!]])]
    (assoc (into {} (map (juxt first second)) started)
           :version-state version-state
           :stops (vec (reverse started)))))

(defn mcp-port [sys]
  (get-in sys [:mcp :port]))

(defn ws-port [sys]
  (bridge/port (:bridge sys)))

(defn stop! [{:keys [stops]}]
  (doseq [[k value stop] stops]
    (stop-logged! k stop value)))
