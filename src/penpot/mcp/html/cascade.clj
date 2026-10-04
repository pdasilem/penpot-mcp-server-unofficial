(ns penpot.mcp.html.cascade
  (:require
   [clojure.string :as str]
   [penpot.mcp.html.css.parse :as parse]
   [penpot.mcp.html.css.values :as v]
   [penpot.mcp.html.ua :as ua])
  (:import
   (org.jsoup.nodes Document Element)
   (org.jsoup.select Selector$SelectorParseException)))

(def root-font-size 16.0)

(def ^:private inherited
  #{"color" "font-family" "font-size" "font-weight" "font-style" "line-height" "letter-spacing"
    "text-transform" "text-align" "white-space" "visibility" "text-decoration" "word-spacing" "direction"})

(defn- matches? [^Element el selector]
  (try
    (.is el ^String selector)
    (catch Selector$SelectorParseException _ false)
    (catch IllegalArgumentException _ false)))

(defn- weighted [decls level specificity order]
  (map-indexed (fn [i [prop value important]]
                 {:prop prop :value value :key [(if important 1 0) level specificity order i]})
               decls))

(defn- declared [^Element el rules pseudo]
  (let [author (for [r rules :when (and (= pseudo (:pseudo r)) (matches? el (:selector r)))]
                 (weighted (:decls r) 1 (:specificity r) (:order r)))
        inline (when (and (nil? pseudo) (.hasAttr el "style"))
                 [(weighted (parse/declarations (.attr el "style")) 2 [0 0 0] 0)])
        ua     (when (nil? pseudo)
                 [(weighted (map #(conj % false) (ua/declarations (.tagName el))) 0 [0 0 0] 0)])]
    (->> (concat ua author inline)
         (apply concat)
         (sort-by :key (fn [a b] (compare (vec a) (vec b))))
         (reduce (fn [m {:keys [prop value]}] (into m (v/expand prop value))) {}))))

(def ^:private max-var-length (* 64 1024))

(defn- resolve-vars [value props depth]
  (cond
    (> (count (str value)) max-var-length) ""
    (or (> depth 8) (not (str/includes? (str value) "var("))) value
    :else
    (resolve-vars
     (str/replace value #"var\(\s*(--[\w-]+)\s*(?:,\s*([^()]*(?:\([^()]*\))?[^()]*))?\)"
                  (fn [[_ name fallback]] (or (get props name) (some-> fallback str/trim) "")))
     props
     (inc depth))))

(defn- font-size-px [value parent-px]
  (let [v (str/trim (str value))]
    (case v
      "smaller" (* parent-px 0.833)
      "larger" (* parent-px 1.2)
      ("small" "x-small" "xx-small") ({"small" 13.0 "x-small" 10.0 "xx-small" 9.0} v)
      ("medium") 16.0
      ("large" "x-large" "xx-large") ({"large" 18.0 "x-large" 24.0 "xx-large" 32.0} v)
      (let [l (v/length v {:font-size parent-px :root-font-size root-font-size :viewport 0})]
        (cond
          (number? l) l
          (map? l) (* parent-px (/ (:percent l) 100.0))
          :else parent-px)))))

(defn- fmt-px [n]
  (let [r (/ (Math/round (* 100.0 (double n))) 100.0)]
    (str (if (== r (Math/rint r)) (long r) r) "px")))

(defn- absolute-lengths [style parent-style]
  (let [parent-px (or (some-> (get parent-style "font-size") (v/px {})) root-font-size)
        fs        (font-size-px (get style "font-size" (str parent-px "px")) parent-px)
        ctx       {:font-size fs :root-font-size root-font-size :viewport 0}
        ls        (get style "letter-spacing")]
    (cond-> (assoc style "font-size" (fmt-px fs))
      (and ls (number? (v/length ls ctx))) (assoc "letter-spacing" (fmt-px (v/px ls ctx)))
      (= "normal" ls) (assoc "letter-spacing" "0px"))))

(defn- finish [own parent-style]
  (let [custom (merge (into {} (filter (fn [[k]] (str/starts-with? k "--"))) parent-style)
                      (into {} (filter (fn [[k]] (str/starts-with? k "--"))) own))
        base   (merge (select-keys parent-style (concat inherited (keys custom))) own custom)
        props  (into {} (keep (fn [[k value]]
                                (let [value (resolve-vars value custom 0)]
                                  (case (str/trim value)
                                    "inherit" (when-let [pv (get parent-style k)] [k pv])
                                    ("initial" "unset") nil
                                    [k value]))))
                     base)
        own-ls (get own "letter-spacing")]
    (absolute-lengths (cond-> props
                        (and (nil? own-ls) (get parent-style "letter-spacing")) (assoc "letter-spacing" (get parent-style "letter-spacing")))
                      parent-style)))

(defn- content-text [value]
  (let [v (str/trim (str value))]
    (when-not (#{"" "none" "normal"} v)
      (let [parts (re-seq #"\"((?:[^\"\\]|\\.)*)\"|'((?:[^'\\]|\\.)*)'" v)]
        (when (seq parts)
          (apply str (map (fn [[_ a b]] (str/replace (or a b) #"\\(.)" "$1")) parts)))))))

(defn- pseudo-style [^Element el rules style which]
  (let [own (declared el rules which)]
    (when-let [text (content-text (get own "content"))]
      (assoc (finish (dissoc own "content") style) "content" text))))

(defn compute [^Document doc {:keys [viewport]}]
  (let [css   (str/join "\n" (map #(.data ^Element %) (.select doc "style")))
        rules (parse/stylesheet css viewport)]
    (loop [stack [[(.root doc) {"font-size" (fmt-px root-font-size)}]] out (transient {})]
      (if-let [[node parent-style] (peek stack)]
        (let [stack (pop stack)]
          (if (instance? Element node)
            (let [el     ^Element node
                  style  (finish (declared el rules nil) parent-style)
                  pseudo (into {} (keep (fn [w] (some->> (pseudo-style el rules style w) (vector w)))) ["before" "after"])
                  kids   (map #(vector % style) (reverse (.children el)))]
              (recur (into stack kids) (assoc! out el {:style style :pseudo pseudo})))
            (recur stack out)))
        (persistent! out)))))
