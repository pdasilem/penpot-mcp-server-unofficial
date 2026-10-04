(ns penpot.mcp.tools.export
  (:require
   [clojure.string :as str]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.plugin.scripts :as scripts]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common])
  (:import
   (java.nio.charset StandardCharsets)
   (java.util Base64)))

(def ^:private body
  (str/join
   "\n"
   ["const s = await focusShape(args.shapeId);"
    "await settle(250);"
    "const bytes = args.mode === 'shape' && args.format === 'png'"
    "  ? await s.export({ type: 'png', scale: Math.min(1, args.maxSize / Math.max(s.width, s.height)) })"
    "  : await penpotUtils.exportImage(s, args.mode, args.format === 'svg');"
    "let binary = '';"
    "for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));"
    "return { __type: 'base64', data: btoa(binary) };"]))

(defn- decode [^String b64]
  (String. (.decode (Base64/getDecoder) b64) StandardCharsets/UTF_8))

(def ^:private default-max-size 1568)

(defn- export-shape [ctx {:keys [file_id shape_id format mode max_size]}]
  (let [format (or format "png")
        mode   (or mode "shape")]
    (when (and (= "fill" mode) (= "svg" format))
      (throw (tool/user-error "Image fills can only be exported as png")))
    (let [result (scripts/serialized
                  ctx
                  #(do (revision/open-target-page! ctx file_id {:shape-id shape_id})
                       (scripts/run! ctx body (common/compact {:file-id file_id :shape-id shape_id :format format :mode mode
                                                               :max-size (or max_size default-max-size)}))))
          {:keys [base64]} (or (scripts/bytes-envelope result)
                               (throw (ex-info "Penpot editor returned no image data" {})))]
      (if (= "svg" format)
        (tool/json-result {:svg (decode base64)})
        (tool/image-result base64 "image/png")))))

(def tools
  [{:name "export_shape"
    :description (str "Render a shape, for example a board, exactly as Penpot draws it and return it: png as an image the model can see, svg as markup. A png is scaled down so that its longer side fits max_size. Mode fill returns the raw image used as the shape's fill (png only, unscaled). Find board ids with list_shapes or search_shapes." canvas/editor-note)
    :annotations tool/read-only
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:shape_id {:description "Shape to export, e.g. a board id"} :uuid]
                   [:format {:optional true :description "png (default) or svg"} [:enum "png" "svg"]]
                   [:mode {:optional true :description "shape (default) or fill"} [:enum "shape" "fill"]]
                   [:max_size {:optional true :description "Longest side of a png in pixels, default 1568; smaller shapes keep their size"} [:int {:min 64 :max 4096}]]]
    :handler export-shape}])
