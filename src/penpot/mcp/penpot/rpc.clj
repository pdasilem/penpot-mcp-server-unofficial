(ns penpot.mcp.penpot.rpc
  (:require
   [app.common.uuid :as uuid]
   [clojure.string :as str]
   [penpot.mcp.penpot.transit :as transit])
  (:import
   (java.net CookieManager CookiePolicy URI)
   (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse HttpResponse$BodyHandlers)
   (java.time Duration)))

(def ^:private connect-timeout (Duration/ofSeconds 10))
(def ^:private request-timeout (Duration/ofSeconds 60))

(defn- decode-body [^String body]
  (when-not (str/blank? body)
    (try (transit/decode body) (catch Exception _ nil))))

(defn- error-label [status {:keys [code]}]
  (cond
    (#{401 403} status) "access denied"
    (keyword? code) (name code)
    :else (str "HTTP " status)))

(def ^:private gateway-statuses #{502 503 504})

(defn- unreachable [detail]
  (ex-info (str "Penpot is unreachable" detail) {:type :tool/user-error :penpot/code :unreachable}))

(defn response->result [cmd status body]
  (let [data (decode-body body)]
    (cond
      (< status 300)
      data

      (gateway-statuses status)
      (throw (unreachable (str " (HTTP " status ")")))

      :else
      (let [label (error-label status data)]
        (throw (ex-info (str "Penpot " (name cmd) " failed: " label)
                        (cond-> {:status status
                                 :penpot/code (:code data)}
                          (<= 400 status 499) (assoc :type :tool/user-error))))))))

(defn conflict? [e]
  (contains? #{:vern-conflict :revn-conflict} (:penpot/code (ex-data e))))

(defn- http-client []
  (-> (HttpClient/newBuilder)
      (.cookieHandler (doto (CookieManager.) (.setCookiePolicy CookiePolicy/ACCEPT_ALL)))
      (.connectTimeout connect-timeout)
      (.build)))

(defn- http-send [^HttpClient http base-url token cmd params]
  (let [req  (cond-> (HttpRequest/newBuilder (URI/create (str base-url "/api/rpc/command/" (name cmd))))
               true  (.header "content-type" "application/transit+json")
               true  (.header "accept" "application/transit+json")
               token (.header "authorization" (str "Token " token))
               true  (.timeout request-timeout)
               true  (.POST (HttpRequest$BodyPublishers/ofString (transit/encode (or params {})))))
        ^HttpResponse resp (try
                             (.send http (.build req) (HttpResponse$BodyHandlers/ofString))
                             (catch java.io.IOException _
                               (throw (unreachable ""))))]
    (response->result cmd (.statusCode resp) (.body resp))))

(defn- normalize-base-url [base-url]
  (str/replace base-url #"/+$" ""))

(defn client [{:keys [base-url token]}]
  (let [http     (http-client)
        base-url (normalize-base-url base-url)]
    {:session-id (uuid/next)
     :send (fn [cmd params] (http-send http base-url token cmd params))}))

(defn session-client [{:keys [base-url email password]}]
  (let [http     (http-client)
        base-url (normalize-base-url base-url)
        send (fn [cmd params] (http-send http base-url nil cmd params))]
    (send :login-with-password {:email email :password password})
    {:session-id (uuid/next)
     :http http
     :send send}))

(defn call [{:keys [send]} cmd params]
  (send cmd params))
