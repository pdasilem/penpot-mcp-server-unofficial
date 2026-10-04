(ns penpot.mcp.html.upload-endpoint
  (:require
   [clojure.data.json :as json]
   [penpot.mcp.auth :as auth]
   [penpot.mcp.html.uploads :as uploads])
  (:import
   (jakarta.servlet Filter)
   (jakarta.servlet.http HttpServletRequest HttpServletResponse)
   (java.nio.charset StandardCharsets)))

(def default-limit (* 20 1024 1024))

(defn- respond [^HttpServletResponse res status body]
  (.setStatus res status)
  (.setContentType res "application/json")
  (.setCharacterEncoding res "UTF-8")
  (.write (.getWriter res) ^String (json/write-str body)))

(defn- read-limited [^HttpServletRequest req limit]
  (let [bytes (.readNBytes (.getInputStream req) (inc limit))]
    (when (<= (alength bytes) limit) bytes)))

(defn- handle [store limit ^HttpServletRequest req res]
  (if (not= "POST" (.getMethod req))
    (respond res 405 {:error "Upload with POST"})
    (if-let [bytes (read-limited req limit)]
      (if (zero? (alength ^bytes bytes))
        (respond res 400 {:error "The body is empty"})
        (if-let [id (uploads/put! store (String. ^bytes bytes StandardCharsets/UTF_8))]
          (respond res 201 {:upload_id id :bytes (alength ^bytes bytes)})
          (respond res 507 {:error "Upload storage is full; finish or wait for earlier imports and try again"})))
      (respond res 413 {:error (str "The file is larger than " limit " bytes")}))))

(defn upload-filter [store limit]
  (reify Filter
    (doFilter [_ req res chain]
      (if (= "html" (auth/query-param (.getQueryString ^HttpServletRequest req) "upload"))
        (handle store limit req res)
        (.doFilter chain req res)))))
