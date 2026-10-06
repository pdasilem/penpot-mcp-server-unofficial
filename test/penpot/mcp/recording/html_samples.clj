(ns penpot.mcp.recording.html-samples
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [penpot.mcp.html.bundle :as bundle])
  (:import
   (org.jsoup Jsoup)
   (org.jsoup.nodes Document Element)))

(def ^:private out "test/resources/html/sayvibe-section.html")

(def ^:private bundle-out "test/resources/html/sayvibe-section.bundle.html")

(def ^:private template-pattern #"(?s)(<script type=\"__bundler/template\">\s*)(.*?)(\s*</script>)")

(defn- bundle-of [source html]
  (let [template (str/replace (json/write-str html :escape-unicode false :escape-slash false) "</" "<\\u002F")]
    (str/replace-first source template-pattern (fn [[_ open _ close]] (str open template close)))))

(def ^:private units 6)

(def ^:private extra-units #{"10.2.1" "10.27" "10.33" "10.36.1"})

(defn- number [^Element unit]
  (first (str/split (str/trim (.text (.selectFirst unit ".num"))) #"\s+")))

(defn- heading [^Element grid]
  (let [h (.previousElementSibling grid)]
    (when (and h (= "h2" (.tagName h))) h)))

(defn- section [^Document doc]
  (let [wrap     (.selectFirst doc "div.wrap")
        all      (.select doc "div.unit")
        chosen   (distinct (concat (take units all) (filter #(extra-units (number %)) all)))
        preamble (take-while #(not= "h2" (.tagName ^Element %)) (.children wrap))
        fresh    (Jsoup/parse (.outerHtml (.head doc)))
        target   (.shallowClone wrap)]
    (.appendChild (.body fresh) target)
    (doseq [^Element e preamble]
      (.appendChild target (.clone e)))
    (doseq [unit-group (partition-by first (map (juxt #(.parent ^Element %) identity) chosen))
            :let [^Element grid (ffirst unit-group)]]
      (when-let [h (heading grid)]
        (.appendChild target (.clone h)))
      (let [g (.shallowClone grid)]
        (.appendChild target g)
        (doseq [[_ ^Element u] unit-group]
          (.appendChild g (.clone u)))))
    (.outerHtml fresh)))

(defn -main [source]
  (let [text    (slurp source)
        html    (:html (bundle/unpack text))
        cut     (section (Jsoup/parse ^String html))]
    (io/make-parents out)
    (spit out cut)
    (spit bundle-out (bundle-of text cut))
    (println "wrote" out)
    (shutdown-agents)))
