(ns penpot.mcp.design.export.library
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.color :as color]
   [penpot.mcp.design.js.number :as jsnum]))

(defn- full-name [{:keys [path name]}]
  (let [path (str/trim (str path))]
    (if (str/blank? path) (str name) (str path " / " name))))

(defn- channels [hex opacity]
  (when-let [rgba (color/rgba (str hex))]
    (assoc rgba :a (double (if (number? opacity) opacity 1)))))

(defn- css [{:keys [r g b a]}]
  (if (= 1.0 a)
    (format "#%02x%02x%02x" r g b)
    (str "rgba(" r ", " g ", " b ", " (jsnum/to-string a) ")")))

(defn- solid [hex opacity]
  (when-let [rgba (channels hex opacity)]
    {:kind :color :rgba rgba :css (css rgba)}))

(defn- stop [{:keys [color opacity offset]}]
  (when-let [rgba (channels color opacity)]
    {:rgba rgba :css (css rgba) :offset (double (or offset 0))}))

(defn- angle [{:keys [start-x start-y end-x end-y]}]
  (let [degrees (+ 90.0 (Math/toDegrees (Math/atan2 (- (double end-y) (double start-y)) (- (double end-x) (double start-x)))))]
    (mod (Math/round (* 100.0 degrees)) 36000)))

(defn- percent [x]
  (jsnum/to-string (/ (Math/round (* 10000.0 (double x))) 100.0)))

(defn- gradient-css [{:keys [type start-x start-y] :as gradient} stops]
  (let [stops-css (str/join ", " (map #(str (:css %) " " (percent (:offset %)) "%") stops))]
    (if (= "radial" (str type))
      (str "radial-gradient(circle at " (percent start-x) "% " (percent start-y) "%, " stops-css ")")
      (str "linear-gradient(" (jsnum/to-string (/ (angle gradient) 100.0)) "deg, " stops-css ")"))))

(defn- gradient-value [{:keys [type start-x start-y end-x end-y width stops] :as g}]
  (let [stops (mapv stop stops)]
    (when (and (seq stops) (every? some? stops) (every? number? [start-x start-y end-x end-y]))
      {:kind :gradient
       :type (if (= "radial" (str type)) :radial :linear)
       :start {:x (double start-x) :y (double start-y)}
       :end {:x (double end-x) :y (double end-y)}
       :width (double (or width 1))
       :stops stops
       :angle (/ (angle g) 100.0)
       :css (gradient-css g stops)})))

(defn- library-color [{:keys [color opacity gradient image] :as c}]
  (cond
    image {:problem {:code :image-color-skipped :color (full-name c)}}
    gradient (if-let [g (gradient-value gradient)] {:value g} {:problem {:code :unsupported-gradient :color (full-name c)}})
    :else (if-let [s (solid color opacity)] {:value s} {:problem {:code :invalid-color :color (full-name c)}})))

(defn colors [items]
  (reduce (fn [acc c]
            (let [{:keys [value problem]} (library-color c)]
              (if value
                (update acc :colors conj {:name (full-name c) :value value})
                (update acc :problems conj problem))))
          {:colors [] :problems []}
          items))

(defn- number [x]
  (when-not (str/blank? (str x))
    (let [n (jsnum/number (str x))]
      (when-not (Double/isNaN n) n))))

(defn- weight [{:keys [font-weight font-style]}]
  (when-let [w (some-> font-weight number long)]
    {:kind :font-weight :weight w :italic (= "italic" (str font-style)) :css (str w (when (= "italic" (str font-style)) " italic"))}))

(defn- typography [{:keys [font-family font-size letter-spacing line-height text-transform] :as t}]
  (let [fields (cond-> {}
                 (seq (str font-family)) (assoc :font-family {:kind :font-family :families [(str font-family)]})
                 (number font-size) (assoc :font-size {:kind :dimension :value (number font-size) :unit "px"})
                 (weight t) (assoc :font-weight (weight t))
                 (number line-height) (assoc :line-height {:kind :number :value (number line-height)})
                 (number letter-spacing) (assoc :letter-spacing {:kind :dimension :value (number letter-spacing) :unit "px"})
                 (seq (str text-transform)) (assoc :text-case {:kind :text :value (str text-transform)}))]
    {:name (full-name t) :value {:kind :typography :fields fields}}))

(defn typographies [items]
  (mapv typography items))
