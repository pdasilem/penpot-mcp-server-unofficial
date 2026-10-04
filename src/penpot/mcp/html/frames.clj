(ns penpot.mcp.html.frames
  (:require
   [clojure.string :as str]
   [penpot.mcp.tool :as tool])
  (:import
   (org.jsoup.nodes Document Element)
   (org.jsoup.select Selector$SelectorParseException)))

(def ^:private max-label 120)

(defn- select! [^Document doc selector param]
  (try
    (vec (.select doc ^String selector))
    (catch Selector$SelectorParseException _
      (throw (tool/user-error (str "Invalid CSS selector in " param ": " selector))))))

(defn- label [^Element el index]
  (let [prev (some-> (.previousElementSibling el) .text str/trim)]
    (cond
      (and (seq prev) (<= (count prev) max-label)) prev
      (seq (.attr el "aria-label")) (.attr el "aria-label")
      (seq (.attr el "title")) (.attr el "title")
      :else (str "Frame " (inc index)))))

(defn- inside? [^Element el chosen]
  (some (fn [^Element c] (some #(identical? c %) (.parents el))) chosen))

(defn plan [^Document doc {:keys [frame-selector section-selector]}]
  (if (str/blank? frame-selector)
    [{:element (.body doc) :section nil :name (or (not-empty (str/trim (.title doc))) "Page")}]
    (let [frame-set   (set (select! doc frame-selector "frame_selector"))
          section-set (if (str/blank? section-selector) #{} (set (select! doc section-selector "section_selector")))
          ordered     (if (empty? section-set)
                        (select! doc frame-selector "frame_selector")
                        (select! doc (str frame-selector ", " section-selector) "frame_selector"))]
      (:frames
       (reduce (fn [{:keys [section frames] :as acc} ^Element el]
                 (cond
                   (and (contains? frame-set el) (not (inside? el (map :element frames))))
                   (update acc :frames conj {:element el :section section :name (label el (count frames))})
                   (contains? section-set el)
                   (assoc acc :section (subs (str/trim (.text el)) 0 (min max-label (count (str/trim (.text el))))))
                   :else acc))
               {:section nil :frames []}
               ordered)))))
