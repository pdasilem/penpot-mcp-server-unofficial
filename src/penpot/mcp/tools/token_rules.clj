(ns penpot.mcp.tools.token-rules
  (:require
   [app.common.types.shape.layout :as ctsl]
   [app.common.types.token :as ctt]
   [clojure.set :as set]
   [clojure.string :as str]
   [penpot.mcp.tool :as tool]))

(def ^:private token-properties
  {:border-radius   {:attributes ctt/border-radius-keys}
   :shadow          {:attributes ctt/shadow-keys}
   :color           {:attributes #{:fill} :all-attributes ctt/color-keys}
   :font-size       {:attributes ctt/font-size-keys}
   :letter-spacing  {:attributes ctt/letter-spacing-keys}
   :font-family     {:attributes ctt/font-family-keys}
   :text-case       {:attributes ctt/text-case-keys}
   :font-weight     {:attributes ctt/font-weight-keys}
   :typography      {:attributes ctt/typography-token-keys}
   :text-decoration {:attributes ctt/text-decoration-keys}
   :stroke-width    {:attributes ctt/stroke-width-keys}
   :sizing          {:attributes #{:width :height} :all-attributes ctt/sizing-keys}
   :dimensions      {:attributes #{:width :height}
                     :all-attributes (set/union ctt/spacing-keys ctt/sizing-keys ctt/border-radius-keys
                                                ctt/axis-keys ctt/stroke-width-keys)}
   :opacity         {:attributes ctt/opacity-keys}
   :number          {:attributes ctt/rotation-keys :all-attributes ctt/number-keys}
   :rotation        {:attributes ctt/rotation-keys}
   :spacing         {:attributes #{:column-gap :row-gap} :all-attributes ctt/spacing-keys}})

(def ^:private plugin-aliases
  {:r1 :border-radius-top-left :r2 :border-radius-top-right
   :r3 :border-radius-bottom-right :r4 :border-radius-bottom-left
   :p1 :padding-top :p2 :padding-right :p3 :padding-bottom :p4 :padding-left
   :m1 :margin-top :m2 :margin-right :m3 :margin-bottom :m4 :margin-left})

(def ^:private attr-order
  [:fill :stroke-color :stroke-width :shadow :opacity :rotation
   :r1 :r2 :r3 :r4 :x :y :width :height
   :layout-item-min-w :layout-item-max-w :layout-item-min-h :layout-item-max-h
   :row-gap :column-gap :p1 :p2 :p3 :p4 :m1 :m2 :m3 :m4
   :font-family :font-size :font-weight :line-height :letter-spacing :text-case :text-decoration :typography])

(defn plugin-name [attr]
  (name (get plugin-aliases attr attr)))

(defn- camel [s]
  (str/replace s #"-([a-z])" #(str/upper-case (second %))))

(defn public-name [attr]
  (camel (plugin-name attr)))

(def public-names
  (mapv public-name attr-order))

(def ^:private by-public-name
  (zipmap public-names attr-order))

(defn parse-attr [public]
  (get by-public-name public))

(def attr-groups
  {"padding" [:p1 :p2 :p3 :p4]
   "margin" [:m1 :m2 :m3 :m4]
   "borderRadius" [:r1 :r2 :r3 :r4]
   "gap" [:row-gap :column-gap]})

(def attr-names
  (into public-names (keys attr-groups)))

(defn parse-attrs [public]
  (set (or (get attr-groups public) (some-> (parse-attr public) vector))))

(defn- type-name [token-type]
  (camel (name token-type)))

(defn- listing [attrs]
  (str/join ", " (map public-name (filter attrs attr-order))))

(defn- shape-attrs [shape layout-child?]
  (cond-> (or (ctt/shape-type->attributes (:type shape) (some? (:layout shape))) #{})
    layout-child? (into ctt/spacing-margin-keys)))

(defn- default-attrs [token-type attributes layout-child?]
  (if (and (= :spacing token-type) layout-child?)
    ctt/spacing-margin-keys
    attributes))

(defn- check-attr! [attr token-attrs allowed type shape-type]
  (when-not (token-attrs attr)
    (throw (tool/user-error (str "Attribute " (public-name attr) " does not take a " (type-name type)
                                 " token; allowed: " (listing token-attrs)))))
  (when-not (allowed attr)
    (throw (tool/user-error (str "Attribute " (public-name attr) " cannot be set on a " shape-type
                                 " shape; allowed for this token: " (listing (set/intersection token-attrs allowed)))))))

(defn target-attrs [{:keys [type]} shape objects attrs]
  (let [{:keys [attributes all-attributes]} (get token-properties type)
        token-attrs   (or all-attributes attributes)
        _             (when-not token-attrs
                        (throw (tool/user-error (str "A " (type-name type) " token cannot be applied to shapes"))))
        layout-child? (ctsl/any-layout-immediate-child? objects shape)
        allowed       (shape-attrs shape layout-child?)
        shape-type    (name (:type shape))]
    (if (seq attrs)
      (do (doseq [attr (filter (set attrs) attr-order)]
            (check-attr! attr token-attrs allowed type shape-type))
          (set attrs))
      (let [targets (set/intersection (default-attrs type attributes layout-child?) allowed)]
        (when (empty? targets)
          (throw (tool/user-error (str "A " (type-name type) " token cannot be applied to a " shape-type " shape"))))
        targets))))
