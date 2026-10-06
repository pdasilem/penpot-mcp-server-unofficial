(ns penpot.mcp.recording.scenarios.html-import
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [penpot.mcp.it :as it]
   [penpot.mcp.tools.plugin-it-test :as p])
  (:import
   (java.net URI)
   (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers)))

(defn- upload! []
  (let [url  (str p/mcp-url "?userToken=" (get it/env "PENPOT_MCP_KEY") "&upload=html")
        resp (.send (HttpClient/newHttpClient)
                    (-> (HttpRequest/newBuilder (URI/create url))
                        (.header "Content-Type" "text/html")
                        (.POST (HttpRequest$BodyPublishers/ofString (slurp (io/resource "html/sayvibe-section.html"))))
                        (.build))
                    (HttpResponse$BodyHandlers/ofString))]
    (when (not= 201 (.statusCode resp))
      (throw (ex-info (str "Upload failed with " (.statusCode resp)) {})))
    (get (json/read-str (.body resp)) "upload_id")))

(defn- s [name args] {:name (str "import/" name) :tool "import_html" :file :scratch :editor true :args args})

(def scenarios
  [(s "sections" #(hash-map "file_id" (:fid %) "upload_id" (upload!) "frame_selector" ".desk" "section_selector" "h2"))
   (s "no-match" #(hash-map "file_id" (:fid %) "upload_id" (upload!) "frame_selector" ".absent"))
   (s "unknown-upload" #(hash-map "file_id" (:fid %) "upload_id" "00000000-0000-0000-0000-000000000001" "frame_selector" ".desk"))])
