(ns penpot.mcp.tools.large-result
  (:require
   [penpot.mcp.exports :as exports]
   [penpot.mcp.tool :as tool])
  (:import
   (java.nio.charset StandardCharsets)))

(def max-inline-chars 30000)

(defn- store! [ctx data file-name]
  (let [{:keys [id error]} (exports/put! (:exports ctx) data file-name)]
    (case error
      nil id
      :too-large (throw (tool/user-error (str "The result is larger than " (quot exports/max-export-bytes (* 1024 1024))
                                              " MB even as an archive; narrow the request")))
      :full (throw (tool/user-error "The download storage is full; narrow the request or try again later")))))

(defn- download [file-name id]
  (str "curl -o " file-name " \"<MCP address>?export=" id "\""))

(defn- archive-files [text {:keys [entry archived files]}]
  (cond
    files (files)
    archived [{:path entry :content (let [a (archived)] (if (string? a) a (tool/json-text a)))}]
    :else [{:path entry :content text}]))

(defn result [ctx {:keys [full brief file-name] :as opts}]
  (let [text  (tool/json-text full)
        size  (alength (.getBytes ^String text StandardCharsets/UTF_8))]
    (if (<= (count text) max-inline-chars)
      (tool/text-result text)
      (let [id (store! ctx (exports/zip (archive-files text opts)) file-name)]
        (tool/json-result (assoc (brief) :full_result {:download (download file-name id)
                                                        :expires_in_minutes (quot exports/ttl-ms 60000)
                                                        :size_bytes size}))))))

(defn- svg-size [^String markup]
  (let [[_ w h] (re-find #"(?s)^\s*(?:<\?xml[^>]*>\s*)?<svg[^>]*?\swidth=\"([^\"]+)\"[^>]*?\sheight=\"([^\"]+)\"" markup)]
    (cond-> {}
      w (assoc :width w)
      h (assoc :height h))))

(defn svg [ctx ^String markup file-name]
  (result ctx {:full {:svg markup}
               :brief #(assoc (svg-size markup) :svg_bytes (alength (.getBytes markup StandardCharsets/UTF_8)))
               :archived (constantly markup)
               :file-name file-name
               :entry "shape.svg"}))
