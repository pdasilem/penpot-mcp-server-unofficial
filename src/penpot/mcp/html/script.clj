(ns penpot.mcp.html.script
  (:require
   [clojure.string :as str]))

(def prepare-body
  (str/join
   "\n"
   ["await focusPage(args.pageId);"
    "const page = penpot.currentPage;"
    "const bottoms = page.root.children.map((c) => c.y + c.height);"
    "const pack = (f) => f ? { fontId: f.fontId, fontFamily: f.fontFamily, variants: f.variants.map((v) => ({ id: v.fontVariantId, weight: v.fontWeight, style: v.fontStyle })) } : null;"
    "const fonts = {};"
    "for (const name of args.families) fonts[name] = pack(penpot.fonts.findByName(name));"
    "const fallback = args.fallback ? pack(penpot.fonts.findByName(args.fontFamily) ?? penpot.fonts.findById('sourcesanspro') ?? penpot.fonts.all[0]) : null;"
    "return { pageId: page.id, revn: penpot.currentFile.revn, bottom: bottoms.length ? Math.max(...bottoms) : null, fonts, fallback };"]))

(def finish-body
  (str/join
   "\n"
   ["await focusPage(args.pageId);"
    "const root = await (async () => { for (let i = 0; i < 300; i++) { const r = penpot.currentPage.getShapeById(args.rootId); if (r) return r; await settle(50); } return null; })();"
    "if (!root) fail('shape-not-found', args.rootId);"
    "const imageShape = async (node) => {"
    "  const src = node.src || '';"
    "  let image = null;"
    "  const data = src.match(/^data:([^;,]+)(;base64)?,(.*)$/s);"
    "  if (data) {"
    "    const raw = data[2] ? atob(data[3]) : decodeURIComponent(data[3]);"
    "    const bytes = new Uint8Array(raw.length);"
    "    for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);"
    "    image = await penpot.uploadMediaData(node.name || 'image', bytes, data[1]);"
    "  } else if (/^https?:\\/\\//.test(src)) {"
    "    image = await penpot.uploadMediaUrl(node.name || 'image', src);"
    "  }"
    "  if (!image) return null;"
    "  const r = penpot.createRectangle();"
    "  r.name = node.name || 'image';"
    "  r.resize(image.width, image.height);"
    "  r.fills = [{ fillOpacity: 1, fillImage: image }];"
    "  return r;"
    "};"
    "const sizing = (v) => v === 'auto' ? 'fix' : 'fill';"
    "for (const m of args.media) {"
    "  const holder = penpot.currentPage.getShapeById(m.id);"
    "  if (!holder) continue;"
    "  const shape = m.node.kind === 'image' ? await imageShape(m.node) : penpot.createShapeFromSvg(m.node.markup);"
    "  if (!shape) continue;"
    "  holder.appendChild(shape);"
    "  const self = m.node.self ?? {};"
    "  if (shape.layoutChild) { shape.layoutChild.horizontalSizing = sizing(self.horizontalSizing); shape.layoutChild.verticalSizing = sizing(self.verticalSizing); }"
    "}"
    "const layout = root.flex ?? root.grid;"
    "if (layout) layout.rowGap = layout.rowGap;"
    "markChanged();"
    "const box = () => JSON.stringify([root.width, root.height]);"
    "let previous = '';"
    "for (let i = 0; i < 50; i++) { const now = box(); if (now === previous) break; previous = now; await settle(100); }"
    "return { boardId: root.id, name: root.name, pageId: penpot.currentPage.id, x: root.x, y: root.y, width: root.width, height: root.height };"]))

(def remove-body
  (str/join
   "\n"
   ["const s = locateShape(args.shapeId);"
    "if (s) { await focusShape(args.shapeId); penpot.currentPage.getShapeById(args.shapeId)?.remove(); markChanged(); }"
    "return { removed: !!s };"]))

(def page-body
  (str/join
   "\n"
   ["const page = penpot.createPage();"
    "page.name = args.name;"
    "markChanged();"
    "await waitFor(() => penpotUtils.getPageById(page.id));"
    "return { pageId: page.id, name: page.name };"]))
