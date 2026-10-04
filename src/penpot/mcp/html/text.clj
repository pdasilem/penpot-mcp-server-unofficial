(ns penpot.mcp.html.text
  (:require
   [clojure.string :as str]
   [penpot.mcp.html.css.values :as v])
  (:import
   (org.jsoup.nodes Element TextNode)))

(defn number-str [n]
  (let [r (/ (Math/round (* 100.0 (double n))) 100.0)]
    (if (== r (Math/rint r)) (str (long r)) (str r))))

(defn- font-size [style]
  (or (v/px (get style "font-size" "16px") {}) 16.0))

(defn- weight [w]
  (case (str/trim (str w))
    ("normal" "") "400"
    ("bold" "bolder") "700"
    "lighter" "300"
    (str/trim (str w))))

(defn- line-height [style fs]
  (let [lh (str/trim (str (get style "line-height" "normal")))]
    (cond
      (= "normal" lh) "1.2"
      (re-matches #"[\d.]+" lh) (number-str (parse-double lh))
      :else (let [l (v/length lh {:font-size fs :root-font-size 16.0 :viewport 0})]
              (cond
                (number? l) (number-str (/ l fs))
                (map? l) (number-str (/ (:percent l) 100.0))
                :else "1.2")))))

(defn- decoration [style]
  (let [d (str (get style "text-decoration" "none"))]
    (cond
      (str/includes? d "underline") "underline"
      (str/includes? d "line-through") "line-through"
      :else "none")))

(defn run-style [style]
  (let [fs    (font-size style)
        color (or (v/color (get style "color" "#000000")) {:hex "#000000" :opacity 1.0})]
    {:fontFamily (str/replace (str (get style "font-family" "sans-serif")) #"[\"']" "")
     :fontSize (number-str fs)
     :fontWeight (weight (get style "font-weight" "400"))
     :fontStyle (if (#{"italic" "oblique"} (get style "font-style")) "italic" "normal")
     :lineHeight (line-height style fs)
     :letterSpacing (number-str (or (v/px (get style "letter-spacing" "0") {:font-size fs}) 0.0))
     :textTransform (let [t (get style "text-transform" "none")] (if (#{"uppercase" "lowercase" "capitalize"} t) t "none"))
     :textDecoration (decoration style)
     :fills [{:fillColor (:hex color) :fillOpacity (:opacity color)}]}))

(defn- pre? [style]
  (#{"pre" "pre-wrap" "break-spaces"} (get style "white-space")))

(defn- raw-runs [^Element el computed]
  (let [{:keys [style pseudo]} (get computed el)
        own   (fn [which] (when-let [p (get pseudo which)] [{:text (get p "content") :style p}]))
        inner (mapcat (fn [node]
                        (cond
                          (instance? TextNode node) [{:text (.getWholeText ^TextNode node) :style style}]
                          (and (instance? Element node) (= "br" (.tagName ^Element node))) [{:text "\n" :style style :break true}]
                          (and (instance? Element node) (= "inline" (get-in computed [node :style "display"]))) (raw-runs node computed)
                          :else nil))
                      (.childNodes el))]
    (concat (own "before") inner (own "after"))))

(defn- collapse [runs]
  (let [collapsed (map (fn [r] (if (or (:break r) (pre? (:style r))) r (update r :text #(str/replace % #"\s+" " ")))) runs)]
    (:out (reduce (fn [{:keys [out prev-space]} r]
                    (let [t (if (and prev-space (not (pre? (:style r))) (not (:break r))) (str/replace (:text r) #"^ " "") (:text r))]
                      {:out (conj out (assoc r :text t))
                       :prev-space (or (str/ends-with? t " ") (str/ends-with? t "\n") (and (empty? t) prev-space))}))
                  {:out [] :prev-space true}
                  collapsed))))

(defn- trim-edges [runs]
  (let [trim-end (fn [rs] (if-let [l (last rs)]
                            (if (pre? (:style l)) rs (conj (vec (butlast rs)) (update l :text #(str/replace % #" +$" ""))))
                            rs))
        runs     (map (fn [r] (if (pre? (:style r)) r (update r :text #(str/replace % #" \n" "\n")))) runs)]
    (trim-end (vec runs))))

(defn- merge-equal [runs]
  (reduce (fn [out r]
            (if (and (seq out) (= (:style (peek out)) (:style r)))
              (conj (pop out) (update (peek out) :text str (:text r)))
              (conj out r)))
          []
          runs))

(defn content [^Element el computed]
  (let [style (get-in computed [el :style])
        runs  (->> (raw-runs el computed)
                   collapse
                   trim-edges
                   (remove #(empty? (:text %)))
                   (map (fn [r] {:text (:text r) :style (run-style (:style r))}))
                   merge-equal)]
    (when (some #(not (str/blank? (:text %))) runs)
      {:runs runs
       :align (case (get style "text-align" "left") ("center") "center" ("right" "end") "right" ("justify") "justify" "left")
       :nowrap (boolean (#{"nowrap" "pre"} (get style "white-space")))})))
