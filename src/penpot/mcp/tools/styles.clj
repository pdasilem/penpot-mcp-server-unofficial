(ns penpot.mcp.tools.styles
  (:require
   [clojure.string :as str]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]
   [penpot.mcp.tools.media :as media]
   [penpot.mcp.tools.text :as text]))

(def ^:private library-param
  [:library_file_id {:optional true :description "Id of the connected shared library the style belongs to; omit for the file's own library"} :uuid])

(def ^:private libraries
  (str/join
   "\n"
   ["const findInLibrary = (kind, id, libraryId) => {"
    "  const libs = libraryId ? penpot.library.connected.filter((l) => l.id === libraryId) : [penpot.library.local];"
    "  if (!libs.length) fail('library-not-connected', libraryId);"
    "  return libs.flatMap((l) => l[kind]).find((x) => x.id === id) ?? fail(kind === 'colors' ? 'color-not-found' : 'typography-not-found', id);"
    "};"
    "const loadText = async (id) => { const s = await focusShape(id); if (s.type !== 'text') fail('not-text', id); return s; };"
    "const rangeOf = (s, start, end) => { if (end > s.characters.length) fail('bad-range', start + '..' + end + ' of ' + s.characters.length); return s.getRange(start, end); };"
    "const rangeState = (r) => ({ characters: r.characters, fontFamily: r.fontFamily, fontSize: r.fontSize, fontWeight: r.fontWeight,"
    "  fontStyle: r.fontStyle, lineHeight: r.lineHeight, letterSpacing: r.letterSpacing, textTransform: r.textTransform,"
    "  textDecoration: r.textDecoration, align: r.align, direction: r.direction, fills: r.fills });"]))

(defn- body [& lines]
  (str/join "\n" (cons libraries lines)))

(def ^:private position
  [:int {:min 0 :max 10000}])

(def ^:private range-style-params
  (remove (comp #{:grow_type :vertical_align} first) text/style-params))

(defn- range-args [{:keys [start end]}]
  (when (<= end start)
    (throw (tool/user-error "end must be greater than start")))
  {:start start :end end})

(def ^:private set-text-range-style
  (canvas/plugin-tool
   {:name "set_text_range_style"
    :description "Style part of a text layer: the characters from start (inclusive) to end (exclusive), counted from 0. Only the given properties change; fills color the characters. Returns the resulting style of the range, where mixed means the range has several values."
    :annotations tool/overwrite
    :input-schema (into [:map {:closed true} common/file-id-param common/shape-id-param
                         [:start {:description "First character, from 0"} position]
                         [:end {:description "Character after the last one"} position]
                         [:fills {:optional true :description "Fills of the characters"} common/fills]]
                        range-style-params)
    :body (body "const s = await loadText(args.shapeId);"
                "const r = rangeOf(s, args.start, args.end);"
                "const before = JSON.stringify(rangeState(r));"
                "for (const [k, v] of Object.entries(args.style)) r[k] = v;"
                "if (args.fills) r.fills = args.fills;"
                "await settle();"
                "if (JSON.stringify(rangeState(s.getRange(args.start, args.end))) !== before) markChanged();"
                "return { id: s.id, range: rangeState(s.getRange(args.start, args.end)) };")
    :args (fn [params]
            (let [style-keys (map first range-style-params)
                  style      (if (some #(contains? params %) style-keys) (text/style params) {})]
              (when (and (empty? style) (not (contains? params :fills)))
                (throw (tool/user-error "Give at least one style property or fills")))
              (merge {:shape-id (:shape_id params) :style style}
                     (range-args params)
                     (when-let [fills (:fills params)] {:fills (mapv common/->plugin-fill fills)}))))
    :result-key nil}))

(def ^:private apply-typography
  (canvas/plugin-tool
   {:name "apply_typography"
    :description "Apply a library typography to a whole text layer or, with start and end, to part of it; the text stays linked to the typography. The typography comes from the file's own library or, with library_file_id, from a connected shared library; take ids from get_typographies. Returns the resulting style of the styled characters."
    :annotations tool/overwrite
    :input-schema [:map {:closed true} common/file-id-param common/shape-id-param
                   [:typography_id {:description "Typography id from get_typographies"} :uuid]
                   library-param
                   [:start {:optional true :description "First character, from 0"} position]
                   [:end {:optional true :description "Character after the last one"} position]]
    :body (body "const s = await loadText(args.shapeId);"
                "const t = findInLibrary('typographies', args.typographyId, args.libraryId);"
                "const styled = () => 'start' in args ? s.getRange(args.start, args.end) : s.getRange(0, s.characters.length);"
                "if ('start' in args) rangeOf(s, args.start, args.end);"
                "const before = JSON.stringify([rangeState(styled()), fingerprint(s)]);"
                "if ('start' in args) t.applyToTextRange(styled()); else t.applyToText(s);"
                "const current = () => JSON.stringify([rangeState(styled()), fingerprint(s)]);"
                "await waitFor(() => current() !== before);"
                "if (current() !== before) markChanged();"
                "return { id: s.id, range: rangeState('start' in args ? s.getRange(args.start, args.end) : s.getRange(0, s.characters.length)) };")
    :args (fn [{:keys [shape_id typography_id library_file_id start end] :as params}]
            (when (not= (some? start) (some? end))
              (throw (tool/user-error "Give both start and end, or neither")))
            (merge (common/compact {:shape-id shape_id :typography-id typography_id :library-id library_file_id})
                   (when start (range-args params))))
    :result-key nil}))

(def ^:private apply-library-color
  (canvas/plugin-tool
   {:name "apply_library_color"
    :description "Apply a library color to a shape, as clicking it in Penpot's color palette does: target fill replaces the first fill, target stroke recolors the first stroke and keeps its width and style; a shape without one gets one. The shape stays linked to the library color. Take ids from get_colors. Returns the changes."
    :annotations tool/overwrite
    :input-schema [:map {:closed true} common/file-id-param common/shape-id-param
                   [:color_id {:description "Library color id from get_colors"} :uuid]
                   library-param
                   [:target {:optional true :description "fill (default) or stroke"} [:enum "fill" "stroke"]]]
    :body (body canvas/focus-shape
                "const c = findInLibrary('colors', args.colorId, args.libraryId);"
                "const before = fingerprint(s);"
                "const beforeInfo = info(s);"
                "if (args.target === 'stroke') {"
                "  const linked = c.asStroke();"
                "  const strokes = [...s.strokes];"
                "  strokes[0] = strokes.length ? Object.assign({}, strokes[0], { strokeColor: linked.strokeColor, strokeOpacity: linked.strokeOpacity,"
                "    strokeColorGradient: linked.strokeColorGradient, strokeColorRefFile: linked.strokeColorRefFile, strokeColorRefId: linked.strokeColorRefId }) : linked;"
                "  s.strokes = strokes;"
                "  if (!(await waitFor(() => s.strokes[0] && s.strokes[0].strokeColorRefId === c.id))) fail('color-not-applied', args.colorId);"
                "} else {"
                "  const fills = [...s.fills];"
                "  fills[0] = c.asFill();"
                "  s.fills = fills;"
                "  if (!(await waitFor(() => s.fills[0] && s.fills[0].fillColorRefId === c.id))) fail('color-not-applied', args.colorId);"
                "}"
                "if (fingerprint(s) !== before) markChanged();"
                "return changes(beforeInfo, s);")
    :args #(common/compact {:shape-id (:shape_id %) :color-id (:color_id %) :library-id (:library_file_id %)
                            :target (or (:target %) "fill")})}))

(def ^:private set-image-fill
  (canvas/plugin-tool
   {:name "set_image_fill"
    :description "Download an image from an http or https URL into the file and make it the only fill of a shape, scaled to cover it. Penpot's server fetches the URL and refuses private network addresses. Returns the changes."
    :annotations (assoc tool/external :destructive true)
    :input-schema [:map {:closed true} common/file-id-param common/shape-id-param
                   [:url {:description "http or https URL of the image"} media/http-url]
                   [:name {:optional true :description "Name of the stored image; defaults to the URL's file name"} common/short-text]]
    :body (body canvas/focus-shape
                "const beforeInfo = info(s);"
                "const image = await penpot.uploadMediaUrl(args.name || args.url.split('?')[0].split('/').pop() || 'image', args.url);"
                "if (!image) fail('create-failed', 'image');"
                "s.fills = [{ fillOpacity: 1, fillImage: image }];"
                "markChanged();"
                "if (!(await waitFor(() => s.fills.length === 1 && s.fills[0].fillImage && s.fills[0].fillImage.id === image.id))) fail('create-failed', 'image fill');"
                "return changes(beforeInfo, s);")
    :args #(common/compact {:shape-id (:shape_id %) :url (:url %) :name (:name %)})}))

(def ^:private library-name-params
  [[:name {:description "Name"} common/short-text]
   [:path {:optional true :description "Group path in the library, with / between levels"} common/short-text]])

(def ^:private create-library-color
  (canvas/plugin-tool
   {:name "create_library_color"
    :description "Add a solid color to the file's own library. Returns the new color with its id."
    :annotations tool/additive
    :input-schema (into [:map {:closed true} common/file-id-param]
                        (concat library-name-params
                                [[:color {:description "Color #RRGGBB"} common/hex-color]
                                 [:opacity {:optional true :description "0..1, default 1"} common/unit-interval]]))
    :body (body "const c = penpot.library.local.createColor();"
                "c.name = args.name;"
                "if (args.path !== undefined) c.path = args.path;"
                "c.color = args.color;"
                "c.opacity = args.opacity ?? 1;"
                "markChanged();"
                "await settle();"
                "return { id: c.id, name: c.name, path: c.path, color: c.color, opacity: c.opacity };")
    :args #(common/compact (select-keys % [:name :path :color :opacity]))
    :result-key :color}))

(def ^:private create-library-typography
  (canvas/plugin-tool
   {:name "create_library_typography"
    :description "Add a typography to the file's own library. The font family must be one Penpot knows: its bundled fonts, the team's fonts from list_fonts, or Google Fonts when the Google Fonts provider is enabled in Penpot; weight and style must be a variant of that font. Returns the new typography with its id."
    :annotations tool/additive
    :input-schema (into [:map {:closed true} common/file-id-param]
                        (concat library-name-params
                                [[:font_family {:description "Font family name, e.g. Work Sans"} common/short-text]
                                 [:font_size {:description "Font size in pixels"} common/positive-size]
                                 [:font_weight {:optional true :description "Font weight, default 400"} [:enum "100" "200" "300" "400" "500" "600" "700" "800" "900"]]
                                 [:font_style {:optional true :description "Font style, default normal"} [:enum "normal" "italic"]]
                                 [:line_height {:optional true :description "Multiplier, e.g. 1.2"} common/positive-size]
                                 [:letter_spacing {:optional true :description "Pixels"} common/safe-number]
                                 [:text_transform {:optional true :description "Letter case transformation"} [:enum "uppercase" "capitalize" "lowercase"]]]))
    :body (body "const font = penpot.fonts.findByName(args.fontFamily) ?? fail('font-not-found', args.fontFamily);"
                "const variant = font.variants.find((v) => v.fontWeight === args.fontWeight && v.fontStyle === args.fontStyle)"
                "  ?? fail('font-variant-not-found', args.fontFamily + ' ' + args.fontWeight + ' ' + args.fontStyle + ' (variants: ' + font.variants.map((v) => v.fontWeight + ' ' + v.fontStyle).join(', ') + ')');"
                "const t = penpot.library.local.createTypography();"
                "t.name = args.name;"
                "if (args.path !== undefined) t.path = args.path;"
                "t.setFont(font, variant);"
                "t.fontSize = args.fontSize;"
                "if (args.lineHeight !== undefined) t.lineHeight = args.lineHeight;"
                "if (args.letterSpacing !== undefined) t.letterSpacing = args.letterSpacing;"
                "if (args.textTransform !== undefined) t.textTransform = args.textTransform;"
                "markChanged();"
                "await settle();"
                "return { id: t.id, name: t.name, path: t.path, fontId: t.fontId, fontFamily: t.fontFamily, fontVariantId: t.fontVariantId,"
                "  fontSize: t.fontSize, fontWeight: t.fontWeight, fontStyle: t.fontStyle, lineHeight: t.lineHeight,"
                "  letterSpacing: t.letterSpacing, textTransform: t.textTransform ?? null };")
    :args (fn [{:keys [font_size font_weight font_style line_height letter_spacing] :as params}]
            (common/compact (merge (select-keys params [:name :path])
                                   {:font-family (:font_family params)
                                    :font-size (str font_size)
                                    :font-weight (or font_weight "400")
                                    :font-style (or font_style "normal")
                                    :line-height (some-> line_height str)
                                    :letter-spacing (some-> letter_spacing str)
                                    :text-transform (:text_transform params)})))
    :result-key :typography}))

(def tools
  [set-text-range-style apply-typography apply-library-color set-image-fill create-library-color create-library-typography])
