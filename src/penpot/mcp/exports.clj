(ns penpot.mcp.exports
  (:require
   [penpot.mcp.spool :as spool]
   [penpot.mcp.sweeper :as sweeper])
  (:import
   (java.io ByteArrayOutputStream)
   (java.nio.charset StandardCharsets)
   (java.util.zip ZipEntry ZipOutputStream)))

(def ttl-ms (* 60 60 1000))

(def ^:private default-max-bytes (* 100 1024 1024))

(def max-export-bytes (* 20 1024 1024))

(def ^:private sweep-interval-s 60)

(defn store [{:keys [now max-bytes spool]}]
  (let [sp (or spool (spool/create {:max-bytes (or max-bytes default-max-bytes) :now now}))]
    {:spool sp :entries (:entries sp) :now now}))

(defn zip [files]
  (let [out (ByteArrayOutputStream.)]
    (with-open [z (ZipOutputStream. out)]
      (doseq [{:keys [path content]} files]
        (.putNextEntry z (ZipEntry. ^String path))
        (.write z (.getBytes ^String content StandardCharsets/UTF_8))
        (.closeEntry z)))
    (.toByteArray out)))

(def ^:private default-name "design-system.zip")

(defn put!
  ([s data] (put! s data default-name))
  ([{:keys [spool now]} ^bytes data file-name]
   (if (> (alength data) max-export-bytes)
     {:error :too-large}
     (if-let [id (spool/put! spool data {:kind :export :name file-name :expires-at (+ (now) ttl-ms)})]
       {:id id}
       {:error :full}))))

(defn sweep! [{:keys [spool]}]
  (spool/sweep! spool))

(defn start! [opts]
  (let [s (store opts)]
    (assoc s :sweeper (sweeper/start! "exports-sweeper" sweep-interval-s #(sweep! s)))))

(defn stop! [{:keys [sweeper]}]
  (sweeper/stop! sweeper))

(defn take-download! [{:keys [spool now]} id]
  (when (= :export (:kind (spool/entry spool id)))
    (when-let [{:keys [bytes expires-at name]} (spool/take! spool id)]
      (when (<= (now) expires-at)
        {:bytes bytes :name name}))))

(defn take! [s id]
  (:bytes (take-download! s id)))
