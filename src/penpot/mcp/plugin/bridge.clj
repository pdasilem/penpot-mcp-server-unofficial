(ns penpot.mcp.plugin.bridge
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [penpot.mcp.auth :as auth]
   [penpot.mcp.tool :as tool])
  (:import
   (java.util UUID)
   (java.util.concurrent CompletableFuture ExecutionException TimeUnit TimeoutException)
   (java.util.function Consumer)
   (org.eclipse.jetty.server Request Response Server ServerConnector)
   (org.eclipse.jetty.websocket.api Callback Session Session$Listener$AutoDemanding StatusCode)
   (org.eclipse.jetty.websocket.server WebSocketCreator WebSocketUpgradeHandler)))

(def ^:private endpoint-path "/mcp/ws")
(def ^:private max-message-bytes (* 32 1024 1024))

(def ^:private disconnected-message "Penpot editor disconnected while running the task")

(defn- parse-message [text]
  (try (json/read-str text :key-fn keyword)
       (catch Exception _ nil)))

(defn- fail-pending! [state session]
  (let [failed (filter (fn [[_ {s :session}]] (identical? s session)) (:pending @state))]
    (swap! state update :pending #(apply dissoc % (map first failed)))
    (doseq [[_ {:keys [^CompletableFuture future]}] failed]
      (.completeExceptionally future (tool/user-error disconnected-message)))))

(defn- complete-task! [state session {:keys [id] :as msg}]
  (when-let [{:keys [^CompletableFuture future] s :session} (get-in @state [:pending id])]
    (when (identical? s session)
      (swap! state update :pending dissoc id)
      (.complete future msg))))

(defn- replace-session! [state session]
  (let [[old _] (swap-vals! state assoc :session session)
        previous (:session old)]
    (when (and previous (not (identical? previous session)))
      (fail-pending! state previous)
      (.close ^Session previous StatusCode/NORMAL "replaced by a newer connection" Callback/NOOP))))

(defn- endpoint [state]
  (let [own (atom nil)]
    (reify Session$Listener$AutoDemanding
      (onWebSocketOpen [_ session]
        (reset! own session)
        (replace-session! state session)
        (log/info "Penpot plugin connected"))
      (onWebSocketText [_ text]
        (when-let [session @own]
          (when (identical? session (:session @state))
            (when-let [msg (parse-message text)]
              (when (:id msg)
                (complete-task! state session msg))))))
      (onWebSocketClose [_ _ _ _]
        (when-let [session @own]
          (swap! state (fn [s] (cond-> s (identical? session (:session s)) (dissoc :session))))
          (fail-pending! state session)
          (log/info "Penpot plugin disconnected"))))))

(def ^:private min-idle-timeout-ms (* 10 60 1000))

(defn idle-timeout-ms [{:keys [task-timeout-ms idle-timeout-ms]}]
  (or idle-timeout-ms (max min-idle-timeout-ms (* 2 (or task-timeout-ms 0)))))

(defn- upgrade-handler [jetty state mcp-key idle-ms]
  (WebSocketUpgradeHandler/from
   jetty
   (reify Consumer
     (accept [_ container]
       (.setMaxTextMessageSize container max-message-bytes)
       (.setIdleTimeout container (java.time.Duration/ofMillis idle-ms))
       (.addMapping container endpoint-path
                    (reify WebSocketCreator
                      (createWebSocket [_ request response callback]
                        (if (auth/valid-token? mcp-key (auth/query-token (.getQuery (.getHttpURI ^Request request))))
                          (endpoint state)
                          (do (log/warn "Rejected Penpot plugin connection with invalid userToken")
                              (Response/writeError ^Request request ^Response response ^org.eclipse.jetty.util.Callback callback 401)
                              nil)))))))))

(defn start! [{:keys [host port mcp-key task-timeout-ms] :as opts}]
  (let [state     (atom {:pending {}})
        jetty     (Server.)
        connector (doto (ServerConnector. jetty) (.setHost host) (.setPort port))]
    (.addConnector jetty connector)
    (.setHandler jetty (upgrade-handler jetty state mcp-key (idle-timeout-ms opts)))
    (try
      (.start jetty)
      (catch Throwable t
        (.stop jetty)
        (throw t)))
    {:jetty jetty
     :state state
     :port (.getLocalPort connector)
     :task-timeout-ms task-timeout-ms}))

(defn port [bridge]
  (:port bridge))

(defn connected? [{:keys [state]}]
  (some? (:session @state)))

(defn- await-reply [^CompletableFuture fut timeout-ms]
  (try
    (.get fut timeout-ms TimeUnit/MILLISECONDS)
    (catch TimeoutException _
      (throw (ex-info "Penpot editor did not answer in time" {:type :tool/user-error :plugin/code "timeout"})))
    (catch ExecutionException e
      (throw (.getCause e)))))

(defn- send-callback [^CompletableFuture fut]
  (reify Callback
    (succeed [_])
    (fail [_ _]
      (.completeExceptionally fut (tool/user-error disconnected-message)))))

(defn execute! [{:keys [state task-timeout-ms]} code]
  (let [^Session session (:session @state)]
    (when-not session
      (throw (ex-info "Penpot editor is not connected; open the file in Penpot with MCP enabled"
                      {:type :tool/user-error :plugin/code "not-connected"})))
    (let [id  (str (UUID/randomUUID))
          fut (CompletableFuture.)]
      (swap! state assoc-in [:pending id] {:future fut :session session})
      (try
        (.sendText session (json/write-str {:id id :task "executeCode" :params {:code code}}) (send-callback fut))
        (let [{:keys [success error data]} (await-reply fut task-timeout-ms)]
          (if success
            (:result data)
            (throw (tool/user-error (str "Penpot editor reported an error: " error)))))
        (finally
          (swap! state update :pending dissoc id))))))

(defn stop! [{:keys [^Server jetty state]}]
  (when-let [session (:session @state)]
    (fail-pending! state session))
  (.stop jetty))
