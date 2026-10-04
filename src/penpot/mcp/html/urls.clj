(ns penpot.mcp.html.urls
  (:require
   [clojure.string :as str])
  (:import
   (java.net Inet6Address InetAddress URI)))

(def ^:private internal-suffixes [".localhost" ".local" ".internal"])

(defn- private-address? [^InetAddress a]
  (let [b (.getAddress a)]
    (or (.isLoopbackAddress a) (.isSiteLocalAddress a) (.isLinkLocalAddress a) (.isAnyLocalAddress a)
        (.isMulticastAddress a)
        (and (instance? Inet6Address a) (= 0xfc (bit-and (aget b 0) 0xfe)))
        (and (= 4 (alength b)) (= 100 (bit-and (aget b 0) 0xff)) (= 64 (bit-and (aget b 1) 0xc0))))))

(defn- host-of [^String url]
  (try
    (some-> (URI. url) .getHost (str/replace #"^\[|\]$" "") str/lower-case)
    (catch Exception _ nil)))

(defn- internal-host? [host]
  (or (str/blank? host)
      (= "localhost" host)
      (some #(str/ends-with? host %) internal-suffixes)
      (and (not (str/includes? host ".")) (not (str/includes? host ":")))
      (try
        (some private-address? (InetAddress/getAllByName host))
        (catch Exception _ true))))

(defn internal? [url]
  (boolean
   (when (re-find #"(?i)^https?://" (str url))
     (internal-host? (host-of url)))))
