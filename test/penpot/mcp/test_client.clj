(ns penpot.mcp.test-client
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str])
  (:import
   (java.net URI)
   (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers)
   (java.time Duration)))

(def ^:private timeout (Duration/ofSeconds 60))

(def ^:private http
  (-> (HttpClient/newBuilder) (.connectTimeout timeout) (.build)))

(defn- parse-body [^String body]
  (let [data-line (->> (str/split-lines body) (filter #(str/starts-with? % "data:")) first)
        payload   (if data-line (subs data-line 5) body)]
    (when-not (str/blank? payload)
      (json/read-str payload :key-fn keyword))))

(defn post [url body & {:keys [session-id]}]
  (let [req  (cond-> (HttpRequest/newBuilder (URI/create url))
               true       (.header "content-type" "application/json")
               true       (.header "accept" "application/json, text/event-stream")
               session-id (.header "mcp-session-id" session-id)
               true       (.timeout timeout)
               true       (.POST (HttpRequest$BodyPublishers/ofString (json/write-str body))))
        resp (.send http (.build req) (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode resp)
     :session-id (.orElse (.firstValue (.headers resp) "mcp-session-id") nil)
     :body (parse-body (.body resp))}))

(defn connect [url]
  (let [init (post url {:jsonrpc "2.0" :id 1 :method "initialize"
                        :params {:protocolVersion "2025-06-18" :capabilities {}
                                 :clientInfo {:name "test" :version "1"}}})
        sid  (:session-id init)]
    (post url {:jsonrpc "2.0" :method "notifications/initialized"} :session-id sid)
    {:url url :session-id sid :init init}))

(defn request [{:keys [url session-id]} id method params]
  (:body (post url {:jsonrpc "2.0" :id id :method method :params params} :session-id session-id)))

(defn call-tool [client tool-name args]
  (:result (request client 10 "tools/call" {:name tool-name :arguments args})))
