(ns penpot.mcp.html.bundle
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str])
  (:import
   (java.io ByteArrayInputStream)
   (java.util Base64)
   (java.util.zip GZIPInputStream)
   (org.jsoup Jsoup)
   (org.jsoup.nodes Document Element)))

(defn- script-json [^Document doc kind]
  (when-let [^Element el (.selectFirst doc (str "script[type=__bundler/" kind "]"))]
    (json/read-str (.data el))))

(defn- asset-bytes [{:strs [data compressed]}]
  (let [raw (.decode (Base64/getDecoder) ^String data)]
    (if compressed
      (with-open [in (GZIPInputStream. (ByteArrayInputStream. raw))]
        (.readAllBytes in))
      raw)))

(defn- data-uri [{:strs [mime] :as entry}]
  (str "data:" mime ";base64," (.encodeToString (Base64/getEncoder) ^bytes (asset-bytes entry))))

(defn- inline-assets [template manifest pages]
  (reduce (fn [html [id entry]]
            (if (contains? pages id) html (str/replace html id (data-uri entry))))
          template
          manifest))

(defn- strip-integrity [html]
  (-> html
      (str/replace #"(?i)\s+integrity=\"[^\"]*\"" "")
      (str/replace #"(?i)\s+crossorigin=\"[^\"]*\"" "")))

(defn unpack [html]
  (let [doc (Jsoup/parse ^String html)]
    (if-let [template (script-json doc "template")]
      (let [manifest (or (script-json doc "manifest") {})
            pages    (set (or (script-json doc "page_order") []))]
        {:html (strip-integrity (inline-assets template manifest pages))
         :pages (count pages)})
      {:html html :pages 0})))
