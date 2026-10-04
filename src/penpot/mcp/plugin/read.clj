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
   ["const found = penpotUtils.findShapeById(args.shapeId);"
    "if (!found) return null;"
    "const page = penpotUtils.getPageForShape(found);"
    "return page ? page.id : null;"]))

(def page-counts-body
  (str/join
   "\n"
   ["return penpot.currentFile.pages.map((p) => ({"
    "  id: p.id, name: p.name,"
    "  shapeCount: p.findShapes().filter((s) => s.id !== '00000000-0000-0000-0000-000000000000').length"
    "}));"]))

(def library-counts-body
  (str/join
   "\n"
   ["const lib = penpot.library.local;"
    "return { components: lib.components.length, colors: lib.colors.length,"
    "  typographies: lib.typographies.length, tokenSets: lib.tokens.sets.length };"]))

(def components-body
  (str/join
   "\n"
   ["return penpot.library.local.components.map((c) => {"
    "  const main = c.mainInstance();"
    "  const page = main ? penpotUtils.getPageForShape(main) : null;"
    "  return { id: c.id, name: c.name, path: c.path, mainInstanceId: main ? main.id : null, mainInstancePage: page ? page.id : null };"
    "});"]))

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
    "return {"
    "  sets: catalog.sets.map((s) => ({ id: s.id, name: s.name, active: s.active,"
    "    tokens: s.tokens.map((t) => ({ id: t.id, name: t.name, type: t.type, value: t.value, description: t.description })) })),"
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
