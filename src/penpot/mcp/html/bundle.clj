(ns penpot.mcp.html.bundle
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [penpot.mcp.tool :as tool])
  (:import
   (java.io ByteArrayInputStream)
   (java.util Base64)
   (java.util.zip GZIPInputStream)
   (org.jsoup Jsoup)
   (org.jsoup.nodes Document Element)))

(defn- script-json [^Document doc kind]
  (when-let [^Element el (.selectFirst doc (str "script[type=__bundler/" kind "]"))]
    (json/read-str (.data el))))

(def default-max-bytes (* 32 1024 1024))

(defn- over-budget [max-bytes]
  (tool/user-error (str "The design bundle's assets unpack to more than " max-bytes " bytes; "
                        "raise PENPOT_MCP_IMPORT_MAX_ASSET_MB together with the server memory to import it")))

(defn- asset-bytes [{:strs [data compressed]} remaining max-bytes]
  (let [raw   (.decode (Base64/getDecoder) ^String data)
        bytes (if compressed
                (with-open [in (GZIPInputStream. (ByteArrayInputStream. raw))]
                  (.readNBytes in (int (min Integer/MAX_VALUE (inc remaining)))))
                raw)]
    (when (> (alength ^bytes bytes) remaining) (throw (over-budget max-bytes)))
    bytes))

(defn- data-uri [mime ^bytes bytes]
  (str "data:" mime ";base64," (.encodeToString (Base64/getEncoder) bytes)))

(defn- inline-assets [template manifest pages {:keys [max-bytes]}]
  (let [ids  (remove #(contains? pages %) (keys manifest))
        uris (:uris (reduce (fn [{:keys [remaining] :as acc} id]
                              (let [entry (get manifest id)
                                    bytes (asset-bytes entry remaining max-bytes)]
                                (-> acc
                                    (update :remaining - (alength ^bytes bytes))
                                    (assoc-in [:uris id] (data-uri (get entry "mime") bytes)))))
                            {:remaining max-bytes :uris {}}
                            ids))]
    (if (empty? uris)
      template
      (str/replace template (re-pattern (str/join "|" (map #(java.util.regex.Pattern/quote %) (keys uris))))
                   #(java.util.regex.Matcher/quoteReplacement (get uris %))))))

(defn- strip-integrity [html]
  (-> html
      (str/replace #"(?i)\s+integrity=\"[^\"]*\"" "")
      (str/replace #"(?i)\s+crossorigin=\"[^\"]*\"" "")))

(defn unpack
  ([html] (unpack html {:max-bytes default-max-bytes}))
  ([html limits]
   (let [doc (Jsoup/parse ^String html)]
     (if-let [template (script-json doc "template")]
       (let [manifest (or (script-json doc "manifest") {})
             pages    (set (or (script-json doc "page_order") []))]
         {:html (strip-integrity (inline-assets template manifest pages limits))
          :pages (count pages)})
       {:html html :pages 0}))))
