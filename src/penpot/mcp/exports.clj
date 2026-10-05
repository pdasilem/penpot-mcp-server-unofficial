(ns penpot.mcp.exports
  (:import
   (java.io ByteArrayOutputStream)
   (java.nio.charset StandardCharsets)
   (java.security SecureRandom)
   (java.util HexFormat)
   (java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit)
   (java.util.zip ZipEntry ZipOutputStream)))

(def ttl-ms (* 60 60 1000))

(def ^:private default-max-bytes (* 100 1024 1024))

(def max-export-bytes (* 20 1024 1024))

(def ^:private sweep-interval-s 60)

(def ^:private random (SecureRandom.))

(defn store [{:keys [now max-bytes]}]
  {:entries (atom {}) :now now :max-bytes (or max-bytes default-max-bytes)})

(defn- new-id []
  (let [bytes (byte-array 16)]
    (.nextBytes ^SecureRandom random bytes)
    (.formatHex (HexFormat/of) bytes)))

(defn- live [entries now-ms]
  (into {} (remove (fn [[_ e]] (> (- now-ms (:created e)) ttl-ms))) entries))

(defn- stored-bytes [entries]
  (reduce + 0 (map (comp alength :bytes) (vals entries))))

(defn zip [files]
  (let [out (ByteArrayOutputStream.)]
    (with-open [z (ZipOutputStream. out)]
      (doseq [{:keys [path content]} files]
        (.putNextEntry z (ZipEntry. ^String path))
        (.write z (.getBytes ^String content StandardCharsets/UTF_8))
        (.closeEntry z)))
    (.toByteArray out)))

(defn put! [{:keys [entries now max-bytes]} ^bytes data]
  (if (> (alength data) max-export-bytes)
    {:error :too-large}
    (let [id     (new-id)
          now-ms (now)
          after  (swap! entries (fn [es]
                                  (let [es (live es now-ms)]
                                    (if (> (+ (stored-bytes es) (alength data)) max-bytes)
                                      es
                                      (assoc es id {:bytes data :created now-ms})))))]
      (if (contains? after id) {:id id} {:error :full}))))

(defn sweep! [{:keys [entries now]}]
  (let [now-ms (now)]
    (swap! entries live now-ms)
    nil))

(def ^:private daemon-factory
  (reify ThreadFactory
    (newThread [_ runnable]
      (doto (Thread. runnable "exports-sweeper") (.setDaemon true)))))

(defn start! [opts]
  (let [s       (store opts)
        sweeper (Executors/newSingleThreadScheduledExecutor daemon-factory)]
    (.scheduleAtFixedRate sweeper ^Runnable #(sweep! s) (long sweep-interval-s) (long sweep-interval-s) TimeUnit/SECONDS)
    (assoc s :sweeper sweeper)))

(defn stop! [{:keys [sweeper]}]
  (when sweeper
    (.shutdownNow ^ScheduledExecutorService sweeper)))

(defn take! [{:keys [entries now]} id]
  (let [now-ms (now)
        [before _] (swap-vals! entries (fn [es] (dissoc (live es now-ms) id)))]
    (when-let [e (get before id)]
      (when (<= (- now-ms (:created e)) ttl-ms)
        (:bytes e)))))
