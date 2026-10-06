(ns penpot.mcp.tools.text
  (:require
   [clojure.string :as str]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]))

(def ^:private text-state
  (str/join
   "\n"
   ["const textState = (t) => Object.assign(info(t), { characters: t.characters, fontFamily: t.fontFamily, fontSize: t.fontSize,"
    "  fontWeight: t.fontWeight, fontStyle: t.fontStyle, lineHeight: t.lineHeight, letterSpacing: t.letterSpacing,"
    "  textTransform: t.textTransform, textDecoration: t.textDecoration, align: t.align,"
    "  verticalAlign: t.verticalAlign, direction: t.direction, growType: t.growType });"]))

(def ^:private load-text
  (str canvas/focus-shape "if (s.type !== 'text') fail('not-text', args.shapeId);\n" text-state "\n"
       "const before = fingerprint(s);\nconst beforeText = textState(s);\n"))

(def ^:private text-info
  (str/join
   "\n"
   ["await settle();"
    "if (fingerprint(s) !== before) markChanged();"
    "return diff(s.id, beforeText, textState(s));"]))

(def ^:private set-text-content
  (canvas/plugin-tool
   {:name "set_text_content"
    :description "Replace all characters of a text layer, keeping its style. Returns the changes."
    :annotations tool/overwrite
    :input-schema [:map {:closed true} common/file-id-param common/shape-id-param
                   [:text {:description "New characters of the text"} [:string {:min 1 :max 10000}]]]
    :body (str load-text "s.characters = args.text;\n" text-info)
    :args #(hash-map :shape-id (:shape_id %) :text (:text %))}))

(def style-params
  [[:font_family {:optional true :description "Font family, e.g. sourcesanspro or a family from list_fonts"} common/short-text]
   [:font_size {:optional true :description "Font size in pixels"} common/positive-size]
   [:font_weight {:optional true :description "Font weight"} [:enum "100" "200" "300" "400" "500" "600" "700" "800" "900"]]
   [:font_style {:optional true :description "Font style"} [:enum "normal" "italic"]]
   [:line_height {:optional true :description "Multiplier, e.g. 1.2"} common/positive-size]
   [:letter_spacing {:optional true :description "Pixels"} common/safe-number]
   [:text_transform {:optional true :description "Letter case transformation; none removes it"} [:enum "uppercase" "capitalize" "lowercase" "none"]]
   [:text_decoration {:optional true :description "Line decoration; none removes it"} [:enum "underline" "line-through" "none"]]
   [:align {:optional true :description "Horizontal alignment"} [:enum "left" "center" "right" "justify"]]
   [:vertical_align {:optional true :description "Vertical alignment inside the text box"} [:enum "top" "center" "bottom"]]
   [:direction {:optional true :description "Writing direction"} [:enum "ltr" "rtl"]]
   [:grow_type {:optional true :description "fixed box, auto-width fits the text, auto-height grows downwards"} [:enum "fixed" "auto-width" "auto-height"]]])

(def ^:private style-keys
  {:font_family :font-family :font_size :font-size :font_weight :font-weight :font_style :font-style
   :line_height :line-height :letter_spacing :letter-spacing :text_transform :text-transform
   :text_decoration :text-decoration :align :align :vertical_align :vertical-align
   :direction :direction :grow_type :grow-type})

(def ^:private stringly #{:font_size :line_height :letter_spacing})

(defn style [params]
  (let [style (into {}
                    (keep (fn [[k target]]
                            (when-let [v (get params k)]
                              [target (if (stringly k) (str v) v)])))
                    style-keys)]
    (when (empty? (select-keys params (keys style-keys)))
      (throw (tool/user-error "Give at least one style property")))
    style))

(def ^:private set-text-style
  (canvas/plugin-tool
   {:name "set_text_style"
    :description "Change the style of a whole text layer; only the given properties change. Use list_fonts for custom font families. Returns the changes."
    :annotations tool/overwrite
    :input-schema (into [:map {:closed true} common/file-id-param common/shape-id-param] style-params)
    :body (str load-text "for (const [k, v] of Object.entries(args.style)) s[k] = v;\n" text-info)
    :args #(hash-map :shape-id (:shape_id %) :style (style %))}))

(def tools
  [set-text-content set-text-style])
