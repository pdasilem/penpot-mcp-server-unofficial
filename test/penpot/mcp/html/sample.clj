(ns penpot.mcp.html.sample
  (:require
   [clojure.java.io :as io]
   [penpot.mcp.html.cascade :as cascade])
  (:import
   (org.jsoup Jsoup)
   (org.jsoup.nodes Document Element)))

(def viewport 1440)

(defn html []
  (slurp (io/resource "html/sayvibe-section.html")))

(defn document ^Document []
  (Jsoup/parse ^String (html)))

(def ^:private parsed
  (delay (let [doc (document)]
           {:doc doc :computed (cascade/compute doc {:viewport viewport})})))

(defn doc ^Document [] (:doc @parsed))

(defn computed [] (:computed @parsed))

(defn element ^Element [selector]
  (or (.selectFirst (doc) ^String selector)
      (throw (ex-info (str "The sample has no " selector) {}))))

(defn style [selector]
  (:style (get (computed) (element selector))))

(defn pseudo [selector part]
  (get-in (computed) [(element selector) :pseudo part]))

(defn declared? [value]
  (.contains ^String (html) ^String value))
