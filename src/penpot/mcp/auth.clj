(ns penpot.mcp.auth
  (:require
   [clojure.string :as str])
  (:import
   (jakarta.servlet Filter)
   (jakarta.servlet.http HttpServletRequest HttpServletResponse)
   (java.net URLDecoder)
   (java.nio.charset StandardCharsets)
   (java.security MessageDigest)))

(defn valid-token? [^String expected ^String actual]
  (boolean
   (and (seq actual)
        (MessageDigest/isEqual (.getBytes expected StandardCharsets/UTF_8)
                               (.getBytes actual StandardCharsets/UTF_8)))))

(defn query-param [^String query param]
  (let [values (->> (str/split (or query "") #"&")
                    (keep (fn [pair]
                            (let [[k v] (str/split pair #"=" 2)]
                              (when (= param k)
                                (try
                                  (URLDecoder/decode (or v "") StandardCharsets/UTF_8)
                                  (catch IllegalArgumentException _ ::malformed)))))))]
    (when (and (= 1 (count values)) (string? (first values)))
      (first values))))

(defn query-token [^String query]
  (query-param query "userToken"))

(defn bearer-token [^String header]
  (when header
    (second (re-matches #"(?i)Bearer +(\S+)" header))))

(defn request-token [^HttpServletRequest req]
  (or (bearer-token (.getHeader req "Authorization"))
      (query-token (.getQueryString req))))

(defn user-token-filter [expected]
  (reify Filter
    (doFilter [_ req res chain]
      (if (valid-token? expected (request-token req))
        (.doFilter chain req res)
        (.sendError ^HttpServletResponse res 401)))))
