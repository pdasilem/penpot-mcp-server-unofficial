(ns penpot.mcp.plugin.scripts
  (:refer-clojure :exclude [run!])
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str])
  (:import
   (java.util.concurrent TimeUnit)
   (java.util.concurrent.locks ReentrantLock)))

(def ^:private prelude
  (str/join
   "\n"
   ["const fail = (code, detail) => { throw new Error('MCP_ERR:' + code + ':' + (detail ?? '')); };"
    "if (!storage.saveTracking) { storage.lastSaveAt = 0; storage.saveTracking = penpot.on('contentsave', () => { storage.lastSaveAt = Date.now(); }); }"
    "const startedAt = Date.now();"
    "let changed = false;"
    "const markChanged = () => { changed = true; storage.dirtySince = Math.max(storage.dirtySince ?? 0, startedAt); };"
    "const settle = (ms) => new Promise((resolve) => setTimeout(resolve, ms ?? 150));"
    "const ensureFile = () => { const f = penpot.currentFile; if (!f || f.id !== args.fileId) fail('not-open', f ? f.id : ''); };"
    "const waitFor = async (check, ms) => { const until = Date.now() + (ms ?? 3000); for (;;) { const v = check(); if (v) return v; if (Date.now() > until) return null; await settle(20); } };"
    "const openPage = async (page) => {"
    "  if (penpot.currentPage.id === page.id) return;"
    "  const opening = penpot.openPage(page);"
    "  if (opening && opening.catch) opening.catch(() => {});"
    "  if (!(await waitFor(() => penpot.currentPage.id === page.id, 25000))) fail('page-not-opened', page.id);"
    "};"
    "const focusPage = async (pageId) => {"
    "  if (!pageId) return penpot.currentPage;"
    "  const page = penpotUtils.getPageById(pageId);"
    "  if (!page) fail('page-not-found', pageId);"
    "  await openPage(page);"
    "  return penpot.currentPage;"
    "};"
    "const locateShape = (id) => {"
    "  const here = penpot.currentPage.getShapeById(id);"
    "  if (here) return { page: penpot.currentPage, shape: here };"
    "  for (const page of penpot.currentFile.pages) {"
    "    if (page.id === penpot.currentPage.id) continue;"
    "    const s = page.getShapeById(id);"
    "    if (s) return { page, shape: s };"
    "  }"
    "  return null;"
    "};"
    "const focusShape = async (id) => {"
    "  const found = locateShape(id) ?? fail('shape-not-found', id);"
    "  await openPage(found.page);"
    "  return (await waitFor(() => penpot.currentPage.getShapeById(id))) ?? fail('shape-not-found', id);"
    "};"
    "const defaults = { rotation: 0, opacity: 1, visible: true, blocked: false };"
    "const withoutDefaults = (o) => { for (const [k, v] of Object.entries(defaults)) if (o[k] === v || o[k] == null) delete o[k]; return o; };"
    "const info = (s) => withoutDefaults({"
    "  id: s.id, name: s.name, type: s.type, pageId: penpot.currentPage.id,"
    "  parentId: s.parent ? s.parent.id : null, parentIndex: s.parentIndex,"
    "  x: s.x, y: s.y, width: s.width, height: s.height, rotation: s.rotation,"
    "  opacity: s.opacity, visible: s.visible, blocked: s.blocked,"
    "  fills: s.fills, strokes: s.strokes, tokens: s.tokens"
    "});"
    "const diff = (id, before, after) => {"
    "  const changed = {};"
    "  for (const k of new Set([...Object.keys(before), ...Object.keys(after)]))"
    "    if (JSON.stringify(before[k]) !== JSON.stringify(after[k])) changed[k] = k in after ? after[k] : (k in defaults ? defaults[k] : null);"
    "  return { id, changed };"
    "};"
    "const changes = (before, s) => diff(s.id, before, info(s));"
    "const layoutState = (l) => l ? { dir: l.dir, wrap: l.wrap, rowGap: l.rowGap, columnGap: l.columnGap,"
    "  alignItems: l.alignItems, alignContent: l.alignContent, justifyItems: l.justifyItems, justifyContent: l.justifyContent,"
    "  padding: [l.topPadding, l.rightPadding, l.bottomPadding, l.leftPadding], sizing: [l.horizontalSizing, l.verticalSizing],"
    "  rows: l.rows, columns: l.columns } : null;"
    "const fingerprint = (s) => JSON.stringify([info(s),"
    "  [s.borderRadiusTopLeft, s.borderRadiusTopRight, s.borderRadiusBottomRight, s.borderRadiusBottomLeft],"
    "  s.type === 'text' ? [s.characters, s.fontFamily, s.fontSize, s.fontWeight, s.fontStyle, s.lineHeight, s.letterSpacing,"
    "    s.textTransform, s.textDecoration, s.align, s.verticalAlign, s.direction, s.growType] : null,"
    "  s.type === 'board' ? [layoutState(s.flex), layoutState(s.grid), s.clipContent] : null,"
    "  [s.shadows, s.blur ?? null, s.backgroundBlur ?? null, s.blendMode, s.constraintsHorizontal, s.constraintsVertical,"
    "    s.proportionLock, s.flipX, s.flipY, s.layoutChild ? [s.layoutChild.horizontalSizing, s.layoutChild.verticalSizing,"
    "    s.layoutChild.alignSelf, s.layoutChild.absolute, s.layoutChild.zIndex, s.layoutChild.topMargin, s.layoutChild.rightMargin,"
    "    s.layoutChild.bottomMargin, s.layoutChild.leftMargin, s.layoutChild.minWidth, s.layoutChild.maxWidth,"
    "    s.layoutChild.minHeight, s.layoutChild.maxHeight] : null]]);"
    "const treeFingerprint = (s) => JSON.stringify([fingerprint(s), ...((s.children ?? []).map(treeFingerprint))]);"
    "ensureFile();"]))

(defn- camel [k]
  (let [[head & tail] (str/split (name k) #"[-_]")]
    (apply str head (map str/capitalize tail))))

(defn- js-literal [value]
  (-> (json/write-str value
                      :key-fn #(if (keyword? %) (camel %) (str %))
                      :value-fn (fn [_ v] (if (uuid? v) (str v) v)))
      (str/replace "</" "<\\/")
      (str/replace (str (char 0x2028)) "\\u2028")
      (str/replace (str (char 0x2029)) "\\u2029")))

(defn script [body args]
  (str "const args = " (js-literal args) ";\n" prelude "\n"
       "let result;\n"
       "try {\n"
       "result = await (async () => {\n" body "\n})();\n"
       "} catch (e) {\n"
       "if (changed) throw new Error('MCP_CHANGED ' + (e && e.message ? e.message : String(e)));\n"
       "throw e;\n"
       "}\n"
       "return { result, changed };"))

(def ^:private error-pattern #"MCP_ERR:([a-z-]+):([^\n]*)")

(defn- friendly [file-id code detail]
  (case code
    "not-open" (str "Open file " file-id " in the Penpot editor; "
                    (if (str/blank? detail) "no file is open" (str "the editor has file " detail " open")))
    "shape-not-found" (str "Shape " detail " not found in the open file")
    "page-not-found" (str "Page " detail " not found in the open file")
    "page-not-opened" (str "Penpot did not open page " detail " within 25 seconds; try again")
    "wrong-token-type" (str "Token " detail)
    "track-occupied" (str "The grid " detail " still holds shapes; move or delete them first, or pass as many tracks as are used")
    "last-page" "A Penpot file must keep at least one page"
    "not-a-board" (str "Shape " detail " is not a board")
    "not-a-container" (str "Shape " detail " cannot contain other shapes")
    "not-text" (str "Shape " detail " is not a text")
    "token-not-found" (str "Token " detail " not found in the open file")
    "token-not-applied" (str "Penpot did not apply the token to " detail "; check that the attribute fits the token type")
    "token-not-removed" (str "Penpot did not remove the token from " detail)
    "mixed-pages" (str "All shapes must be on the same page; " detail " is on another page")
    "parent-on-other-page" (str "Board or group " detail " is on another page; a shape moves only to a parent on its own page")
    "create-failed" (str "Penpot could not create the " detail)
    "component-not-found" (str "Component " detail " not found in the library")
    "library-not-connected" (str "Library " detail " is not connected to the open file; see get_file_libraries")
    "not-a-variant" (str "Component " detail " is not part of a variant set")
    "already-variant" (str "Component " detail " is already part of a variant set")
    "property-not-found" (str "Variant property not found: " detail)
    "property-exists" (str "The variant set already has a property named " detail)
    "value-not-found" (str "No variant has this property value: " detail)
    "variant-not-updated" (str "Penpot did not update the variant property " detail)
    "not-a-copy" (str "Shape " detail " is not the root of a component copy")
    "not-detached" (str "Penpot did not detach the copy " detail)
    "not-in-layout" (str "Shape " detail " is not inside a flex or grid layout")
    "not-in-grid" (str "Shape " detail " is not inside a grid layout")
    "not-a-group" (str "Shape " detail " is not a group")
    "set-not-found" (str "Token set " detail " not found")
    "theme-not-found" (str "Token theme " detail " not found")
    "set-exists" (str "A token set named " detail " already exists")
    "token-exists" (str "The set already has a token named " detail)
    "theme-exists" (str "A token theme " detail " already exists")
    "not-deleted" (str "Penpot did not delete " detail)
    "not-updated" (str "Penpot did not apply the change to " detail)
    "bad-range" (str "The range is outside the text: " detail)
    "color-not-found" (str "Library color " detail " not found")
    "typography-not-found" (str "Typography " detail " not found")
    "color-not-applied" (str "Penpot did not apply the library color " detail)
    "font-not-found" (str "Font family " detail " not found in Penpot")
    "font-variant-not-found" (str "Font variant not found: " detail)
    "no-grid-cell" (str "Shape " detail " is not placed in a grid cell yet; give both row and column")
    "not-saved" (str "Penpot has not saved the latest editor changes of file " file-id " yet; try again")
    nil))

(defn serialized [{:keys [plugin-lock]} f]
  (if-let [{:keys [^ReentrantLock lock wait-ms]} plugin-lock]
    (if (.tryLock lock (long wait-ms) TimeUnit/MILLISECONDS)
      (try (f) (finally (.unlock lock)))
      (throw (ex-info "Penpot editor is busy with other requests; try again"
                      {:type :tool/user-error :plugin/code "busy"})))
    (f)))

(def ^:private changed-marker "MCP_CHANGED ")

(defn execute! [{:keys [execute] :as ctx} body {:keys [file-id] :as args}]
  (try
    (serialized ctx #(execute (script body args)))
    (catch clojure.lang.ExceptionInfo e
      (let [raw      (or (ex-message e) "")
            changed? (str/includes? raw changed-marker)
            text     (str/replace raw changed-marker "")
            [_ code detail] (re-find error-pattern text)
            extra    (cond-> {} changed? (assoc :plugin/changed true))]
        (if-let [message (and code (friendly file-id code detail))]
          (throw (ex-info message (merge {:type :tool/user-error :plugin/code code} extra)))
          (throw (ex-info text (merge (ex-data e) extra) e)))))))

(defn run! [ctx body args]
  (:result (execute! ctx body args)))

(defn bytes-envelope [result]
  (when (and (map? result) (= "base64" (:__type result)))
    {:base64 (:data result)}))
