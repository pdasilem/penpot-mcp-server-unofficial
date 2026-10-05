(ns penpot.mcp.exports.endpoint
  (:require
   [penpot.mcp.auth :as auth]
   [penpot.mcp.exports :as exports])
  (:import
   (jakarta.servlet Filter)
   (jakarta.servlet.http HttpServletRequest HttpServletResponse)))

(defn- export-id [^HttpServletRequest req]
  (auth/query-param (.getQueryString req) "export"))

(defn- send-zip [^HttpServletResponse res ^bytes data]
  (doto res
    (.setStatus 200)
    (.setContentType "application/zip")
    (.setHeader "Content-Disposition" "attachment; filename=\"design-system.zip\"")
    (.setHeader "Cache-Control" "no-store")
    (.setHeader "X-Content-Type-Options" "nosniff")
    (.setContentLength (alength data)))
  (.write (.getOutputStream res) data))

(defn- handle [store id ^HttpServletRequest req ^HttpServletResponse res]
  (cond
    (not= "GET" (.getMethod req)) (.sendError res 405)
    (not (re-matches #"[0-9a-f]{32}" (str id))) (.sendError res 404)
    :else (if-let [data (exports/take! store id)] (send-zip res data) (.sendError res 404))))

(defn export-filter [store]
  (reify Filter
    (doFilter [_ req res chain]
      (if-let [id (export-id req)]
        (handle store id req res)
        (.doFilter chain req res)))))
