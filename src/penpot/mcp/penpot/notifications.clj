(ns penpot.mcp.penpot.notifications
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.penpot.transit :as transit]
   [penpot.mcp.penpot.uuid :as uuid])
  (:import
   (java.net URI)
   (java.net.http HttpClient WebSocket WebSocket$Listener)
   (java.util.concurrent TimeUnit TimeoutException)))

(def ^:private connect-timeout-seconds 10)

(defn ws-url [base-url session-id]
  (str (str/replace-first (str/replace base-url #"/+$" "") #"^http" "ws")
       "/ws/notifications?session-id=" session-id))

(defn- listener [received]
  (let [buffer (StringBuilder.)]
    (reify WebSocket$Listener
      (onText [_ ws data last?]
        (.append buffer data)
        (when last?
          (let [text (.toString buffer)]
            (.setLength buffer 0)
            (when-let [msg (try (transit/decode text) (catch Exception _ nil))]
              (swap! received conj msg))))
        (.request ^WebSocket ws 1)
        nil))))

(defn presence-sessions [messages file-id own-session-id]
  (->> messages
       (filter #(and (= :presence (:type %)) (= file-id (:file-id %))))
       (remove #(= own-session-id (:session-id %)))
       (map #(select-keys % [:profile-id :session-id]))
       (distinct)
       (vec)))

(defn- connect [^HttpClient http url listener]
  (let [fut (-> (.newWebSocketBuilder http) (.buildAsync (URI/create url) listener))]
    (try
      (.get fut connect-timeout-seconds TimeUnit/SECONDS)
      (catch TimeoutException e
        (.cancel fut true)
        (throw e)))))

(defn- collect [^HttpClient http base-url file-id wait-ms]
  (let [session-id (uuid/next)
        received   (atom [])
        ^WebSocket ws (connect http (ws-url base-url session-id) (listener received))]
    (try
      (.join (.sendText ws (transit/encode {:type :subscribe-file :file-id file-id}) true))
      (Thread/sleep (long wait-ms))
      (.join (.sendText ws (transit/encode {:type :unsubscribe-file :file-id file-id}) true))
      (presence-sessions @received file-id session-id)
      (finally
        (.abort ws)))))

(defn presence-fn [{:keys [base-url email password wait-ms]}]
  (let [session (atom nil)
        login!  (fn []
                  (let [fresh (rpc/session-client {:base-url base-url :email email :password password})
                        [old _] (reset-vals! session fresh)]
                    (some-> ^HttpClient (:http old) (.close))
                    fresh))]
    (fn [file-id]
      (let [client (or @session (login!))]
        (try
          (collect (:http client) base-url file-id wait-ms)
          (catch Exception e
            (log/warn "Penpot notifications failed, logging in again:" (ex-message e))
            (collect (:http (login!)) base-url file-id wait-ms)))))))
