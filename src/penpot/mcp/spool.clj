(ns penpot.mcp.spool
  (:refer-clojure :exclude [read])
  (:require
   [clojure.java.io :as io])
  (:import
   (java.io File)
   (java.nio.file Files LinkOption OpenOption)
   (java.security SecureRandom)))

(def ^:private random (SecureRandom.))

(defn- new-id []
  (let [b (byte-array 16)]
    (.nextBytes ^SecureRandom random b)
    (apply str (map #(format "%02x" %) b))))

(defn- clear-dir! [dir]
  (.mkdirs (io/file dir))
  (doseq [^File f (.listFiles (io/file dir))
          :when (.isFile f)]
    (.delete f)))

(defn create [{:keys [max-bytes now dir]}]
  (when dir (clear-dir! dir))
  {:entries (atom {}) :max-bytes max-bytes :now now :dir dir})

(defn- path [{:keys [dir]} id]
  (.toPath (io/file dir id)))

(defn- stored [entries]
  (reduce + 0 (map :size (vals entries))))

(defn- public [entry]
  (dissoc entry :bytes))

(defn- remove-file! [s id]
  (when (:dir s)
    (Files/deleteIfExists (path s id))))

(defn put! [{:keys [entries max-bytes dir] :as s} ^bytes data meta]
  (let [id    (or (::id meta) (new-id))
        entry (cond-> (assoc (dissoc meta ::id) :size (alength data)) (nil? dir) (assoc :bytes data))
        after (swap! entries (fn [es] (if (> (+ (stored es) (alength data)) max-bytes) es (assoc es id entry))))]
    (when (contains? after id)
      (when dir
        (try
          (Files/write (path s id) data ^"[Ljava.nio.file.OpenOption;" (make-array OpenOption 0))
          (catch Throwable t
            (swap! entries dissoc id)
            (throw t))))
      id)))

(defn entry [{:keys [entries]} id]
  (some-> (get @entries id) public))

(defn- bytes-of [s id entry]
  (if (:dir s)
    (let [p (path s id)]
      (when (Files/exists p (make-array LinkOption 0))
        (Files/readAllBytes p)))
    (:bytes entry)))

(defn read [{:keys [entries] :as s} id]
  (when-let [e (get @entries id)]
    (bytes-of s id e)))

(defn update! [{:keys [entries]} id f & args]
  (swap! entries (fn [es] (if (contains? es id) (apply update es id f args) es)))
  nil)

(defn delete! [{:keys [entries] :as s} id]
  (swap! entries dissoc id)
  (remove-file! s id)
  nil)

(defn take! [{:keys [entries] :as s} id]
  (let [[before _] (swap-vals! entries dissoc id)]
    (when-let [e (get before id)]
      (let [data (bytes-of s id e)]
        (remove-file! s id)
        (assoc (public e) :bytes data)))))

(defn sweep! [{:keys [entries now] :as s}]
  (let [now-ms     (now)
        [before _] (swap-vals! entries (fn [es] (into {} (remove (fn [[_ e]] (> now-ms (:expires-at e)))) es)))]
    (doseq [[id e] before
            :when (> now-ms (:expires-at e))]
      (remove-file! s id))
    nil))
