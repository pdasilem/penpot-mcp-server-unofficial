(ns penpot.mcp.plugin.read
  (:require
   [clojure.string :as str]
   [penpot.mcp.plugin.scripts :as scripts]))

(def ^:private closed-codes
  #{"not-open" "not-connected"})

(defn attempt [{:keys [execute]} f]
  (when execute
    (try
      {:value (f)}
      (catch clojure.lang.ExceptionInfo e
        (when-not (closed-codes (:plugin/code (ex-data e)))
          (throw e))))))

(defn in-editor [ctx file-id body args]
  (attempt ctx #(scripts/run! ctx body (assoc args :file-id file-id))))

(def pages-body
  "return penpot.currentFile.pages.map((p) => ({ id: p.id, name: p.name }));")

(def shape-page-body
  (str/join
   "\n"
   ["const found = locateShape(args.shapeId);"
    "return found ? found.page.id : null;"]))

(def page-counts-body
  (str/join
   "\n"
   ["return penpot.currentFile.pages.map((p) => ({"
    "  id: p.id, name: p.name,"
    "  shapeCount: p.findShapes().filter((s) => s.id !== '00000000-0000-0000-0000-000000000000').length"
    "}));"]))

(def ^:private all-components
  ["const allComponents = () => {"
   "  const found = new Map();"
   "  for (const c of penpot.library.local.components) {"
   "    found.set(c.id, c);"
   "    if (c.isVariant() && c.variants) for (const v of c.variants.variantComponents()) if (v && v.id) found.set(v.id, v);"
   "  }"
   "  return [...found.values()];"
   "};"])

(def library-counts-body
  (str/join
   "\n"
   (concat
    all-components
    ["const lib = penpot.library.local;"
     "return { components: allComponents().length, colors: lib.colors.length,"
     "  typographies: lib.typographies.length, tokenSets: lib.tokens.sets.length };"])))

(def components-body
  (str/join
   "\n"
   (concat
    all-components
    ["return allComponents().map((c) => {"
     "  const main = c.mainInstance();"
     "  const page = main ? locateShape(main.id)?.page : null;"
     "  return { id: c.id, name: c.name, path: c.path, mainInstanceId: main ? main.id : null, mainInstancePage: page ? page.id : null };"
     "});"])))

(def colors-body
  (str/join
   "\n"
   ["return penpot.library.local.colors.map((c) => ({"
    "  id: c.id, name: c.name, path: c.path, color: c.color, opacity: c.opacity, gradient: c.gradient, image: c.image"
    "}));"]))

(def typographies-body
  (str/join
   "\n"
   ["return penpot.library.local.typographies.map((t) => ({"
    "  id: t.id, name: t.name, path: t.path, fontId: t.fontId, fontFamily: t.fontFamily, fontVariantId: t.fontVariantId,"
    "  fontSize: t.fontSize, fontWeight: t.fontWeight, fontStyle: t.fontStyle, lineHeight: t.lineHeight,"
    "  letterSpacing: t.letterSpacing, textTransform: t.textTransform"
    "}));"]))

(def tokens-body
  (str/join
   "\n"
   ["const catalog = penpot.library.local.tokens;"
    "const filter = (typeof args !== 'undefined' && args.tokenFilter) || {};"
    "const query = filter.query ? filter.query.toLowerCase() : null;"
    "const wanted = (t) => (!filter.type || t.type === filter.type) && (!query || t.name.toLowerCase().includes(query));"
    "return {"
    "  sets: catalog.sets.filter((s) => !filter.set || s.name === filter.set).map((s) => ({ id: s.id, name: s.name, active: s.active,"
    "    tokens: s.tokens.filter(wanted).map((t) => ({ id: t.id, name: t.name, type: t.type, value: t.value, description: t.description })) })),"
    "  themes: catalog.themes.map((t) => ({ id: t.id, group: t.group, name: t.name, active: t.active,"
    "    sets: t.activeSets.map((s) => s.name) }))"
    "};"]))

(def token-body
  (str/join
   "\n"
   ["const token = penpot.library.local.tokens.sets.flatMap((s) => s.tokens).find((t) => t.id === args.tokenId);"
    "return token ? { id: token.id, name: token.name, type: token.type } : null;"]))

(defn file-key [k]
  (keyword (str/replace (name k) #"[A-Z]" #(str "-" (str/lower-case %)))))

(defn file-keys [x]
  (cond
    (map? x) (into {} (map (fn [[k v]] [(if (keyword? k) (file-key k) k) (file-keys v)])) x)
    (sequential? x) (mapv file-keys x)
    :else x))

(def ^:private root-id "00000000-0000-0000-0000-000000000000")

(def ^:private pages-to-scan
  ["const pages = args.pageId"
   "  ? [penpotUtils.getPageById(args.pageId) ?? fail('page-not-found', args.pageId)]"
   "  : penpot.currentFile.pages;"])

(def search-body
  (str/join
   "\n"
   (concat
    pages-to-scan
    ["const found = [];"
     "for (const page of pages) {"
     "  for (const s of page.findShapes()) {"
     (str "    if (s.id === '" root-id "') continue;")
     "    if (!(s.name ?? '').toLowerCase().includes(args.query)) continue;"
     "    if (args.type && s.type !== args.type) continue;"
     (str "    found.push({ id: s.id, name: s.name, type: s.type, parent_id: s.parent ? s.parent.id : '" root-id "',")
     "      x: s.x, y: s.y, width: s.width, height: s.height, page_id: page.id });"
     "  }"
     "}"
     "return found;"])))

(def instances-body
  (str/join
   "\n"
   ["const found = [];"
    "for (const page of penpot.currentFile.pages) {"
    "  for (const s of page.findShapes()) {"
    "    if (!s.isComponentRoot()) continue;"
    "    const c = s.component();"
    "    if (!c || (args.componentId && c.id !== args.componentId)) continue;"
    "    const main = c.mainInstance();"
    "    found.push({ id: s.id, name: s.name, page_id: page.id, component_id: c.id, component_file: c.libraryId,"
    "      is_main: !!main && main.id === s.id });"
    "  }"
    "}"
    "return found;"]))

(def shape-info-body
  (str/join
   "\n"
   ["const s = locateShape(args.shapeId)?.shape;"
    "if (!s) return null;"
    "const layoutOf = (b) => b && b.type === 'board' ? (b.grid ? 'grid' : (b.flex ? 'flex' : null)) : null;"
    "return { type: s.type, layout: layoutOf(s), parentLayout: !!layoutOf(s.parent) };"]))

(def comment-target-body
  (str/join
   "\n"
   ["const page = args.pageId ? penpotUtils.getPageById(args.pageId) : penpot.currentFile.pages[0];"
    "if (!page) return { pageId: null, frameFound: false };"
    "return { pageId: page.id, frameFound: !!(args.frameId && page.getShapeById(args.frameId)) };"]))

(def svg-body
  (str/join
   "\n"
   ["const found = locateShape(args.shapeId) ?? fail('shape-not-found', args.shapeId);"
    "if (args.pageId && found.page.id !== args.pageId) fail('shape-not-found', args.shapeId);"
    "const s = found.shape;"
    "return penpot.generateMarkup([s], { type: 'svg' });"]))

(def ^:private brief-js
  (str "const brief = (s) => ({ id: s.id, name: s.name, type: s.type, parent_id: s.parent ? s.parent.id : '" root-id "',"
       " x: s.x, y: s.y, width: s.width, height: s.height });"))

(def ^:private page-or-first
  "const page = args.pageId ? penpotUtils.getPageById(args.pageId) : penpot.currentFile.pages[0];")

(def list-shapes-body
  (str/join
   "\n"
   [page-or-first
    "if (!page) return null;"
    brief-js
    (str "return { pageId: page.id, shapes: page.findShapes()"
         ".filter((s) => s.id !== '" root-id "' && (!args.type || s.type === args.type)).map(brief) };")]))

(def shape-tree-body
  (str/join
   "\n"
   ["const page = args.pageId ? penpotUtils.getPageById(args.pageId)"
    "  : (args.rootId ? (locateShape(args.rootId)?.page ?? penpot.currentFile.pages[0]) : penpot.currentFile.pages[0]);"
    "if (!page) return null;"
    brief-js
    "const root = args.rootId ? page.getShapeById(args.rootId) : page.root;"
    "if (!root) return { pageId: page.id, tree: null };"
    "const kids = (s) => {"
    "  const c = ['board', 'group', 'boolean', 'svg-raw'].includes(s.type) ? (s.children ?? []) : [];"
    "  return s.type === 'board' && s.flex ? [...c].reverse() : [...c];"
    "};"
    "const node = (s, depth) => {"
    "  const c = kids(s);"
    "  const n = { ...brief(s), child_count: c.length };"
    "  if (depth > 0 && c.length) n.children = c.map((k) => node(k, depth - 1));"
    "  return n;"
    "};"
    "return { pageId: page.id, tree: node(root, args.depth) };"]))
