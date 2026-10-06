(ns penpot.mcp.html.uploads
  (:require
   [penpot.mcp.spool :as spool])
  (:import
   (java.nio.charset StandardCharsets)
   (java.util UUID)))

(def ^:private ttl-ms (* 60 60 1000))
(def ^:private default-max-bytes (* 100 1024 1024))

(defn store [{:keys [now max-bytes spool]}]
  {:spool (or spool (spool/create {:max-bytes (or max-bytes default-max-bytes) :now now})) :now now})

(defn put! [{:keys [spool now]} ^String text]
  (let [id (str (UUID/randomUUID))]
    (spool/put! spool (.getBytes text StandardCharsets/UTF_8)
                {:kind :upload :expires-at (+ (now) ttl-ms) ::spool/id id})))

(defn- live? [entry now-ms]
  (and (= :upload (:kind entry)) (<= now-ms (:expires-at entry))))

(defn text [{:keys [spool now]} id]
  (let [now-ms (now)]
    (when (live? (spool/entry spool id) now-ms)
      (spool/update! spool id assoc :expires-at (+ now-ms ttl-ms))
      (some-> (spool/read spool id) (String. StandardCharsets/UTF_8)))))

(defn remove! [{:keys [spool]} id]
  (when (= :upload (:kind (spool/entry spool id)))
    (spool/delete! spool id))
  nil)
