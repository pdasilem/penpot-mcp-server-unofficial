(ns penpot.mcp.tools.plugin-extras-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.exports :as exports]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.plugin.bridge :as bridge]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.export :as export]
   [penpot.mcp.tools.tokens :as tokens]))

(def fid (str fx/file-id))
(def sid (str fx/rect-id))

(def tid (str fx/token-id))

(defn- editor-answer [code]
  (let [args (fx/script-args code)]
    (cond
      (str/includes? code read/token-body)
      (when (= tid (get args "tokenId")) {:id tid :name "color.primary" :type "color"})

      (str/includes? code read/shape-info-body)
      (when (= sid (get args "shapeId")) {:type "rectangle" :layout nil :parentLayout true})

      :else
      {:id sid :tokens {:fill "color.primary"}})))

(defn- token-call [tool-name args]
  (let [ctx (fx/plugin-ctx editor-answer (fx/file-responses fx/file))]
    {:ctx ctx :result (fx/call (fx/find-tool tokens/tools tool-name) ctx args)}))

(defn- changes-sent? [ctx]
  (some #(str/includes? % "applyToken") @(:scripts ctx)))

(deftest token-checks-never-download-the-file
  (doseq [[tool-name args] [["set_token" {"file_id" fid "shape_id" sid "token_id" tid}]
                            ["remove_token" {"file_id" fid "shape_id" sid "attr" "fill"}]]]
    (let [{:keys [ctx]} (token-call tool-name args)]
      (is (empty? (fx/rpc-commands ctx))))))

(deftest spacing-on-a-layout-child-binds-margins-from-the-editor-facts
  (let [ctx (fx/plugin-ctx (fn [code]
                             (cond
                               (str/includes? code read/token-body) {:id tid :name "space.m" :type "spacing"}
                               (str/includes? code read/shape-info-body) {:type "rectangle" :layout nil :parentLayout true}
                               :else {:id sid}))
                           {})]
    (fx/call (fx/find-tool tokens/tools "set_token") ctx {"file_id" fid "shape_id" sid "token_id" tid})
    (is (= #{"margin-top" "margin-right" "margin-bottom" "margin-left"}
           (set (map #(get % "name") (get (fx/last-script-args ctx) "attrs")))))))

(deftest set-token-without-attribute-binds-token-type-defaults
  (let [{:keys [ctx result]} (token-call "set_token" {"file_id" fid "shape_id" sid "token_id" tid})]
    (is (= {"fileId" fid "shapeId" sid "tokenId" tid "attrs" [{"name" "fill" "key" "fill"}]}
           (fx/last-script-args ctx)))
    (is (= {"shape" {"id" sid "tokens" {"fill" "color.primary"}}} result))))

(deftest set-token-with-attribute-binds-that-attribute
  (let [{:keys [ctx]} (token-call "set_token" {"file_id" fid "shape_id" sid "token_id" tid "attr" "strokeColor"})]
    (is (= [{"name" "stroke-color" "key" "strokeColor"}] (get (fx/last-script-args ctx) "attrs")))))

(deftest set-token-script-skips-attributes-already-bound
  (let [{:keys [ctx]} (token-call "set_token" {"file_id" fid "shape_id" sid "token_id" tid})
        script        (last @(:scripts ctx))]
    (is (str/includes? script "s.tokens[a.key] !== token.name"))
    (is (str/includes? script "applyToken(token, missing.map((a) => a.name))"))))

(deftest set-token-rejects-attribute-the-token-type-does-not-take
  (let [{:keys [ctx result]} (token-call "set_token" {"file_id" fid "shape_id" sid "token_id" tid "attr" "width"})]
    (is (= {:error "Attribute width does not take a color token; allowed: fill, strokeColor"} result))
    (is (not (changes-sent? ctx)))))

(deftest set-token-rejects-unknown-attribute-name
  (is (contains? (:result (token-call "set_token" {"file_id" fid "shape_id" sid "token_id" tid "attr" "background"}))
                 :error)))

(deftest set-token-rejects-unknown-token-before-changing-anything
  (let [{:keys [ctx result]} (token-call "set_token" {"file_id" fid "shape_id" sid "token_id" "99999999-0000-0000-0000-0000000000cc"})]
    (is (= {:error "Token 99999999-0000-0000-0000-0000000000cc not found in file 11111111-0000-0000-0000-000000000001"} result))
    (is (not (changes-sent? ctx)))))

(deftest set-token-rejects-unknown-shape-before-changing-anything
  (let [{:keys [ctx result]} (token-call "set_token" {"file_id" fid "shape_id" "22222222-0000-0000-0000-0000000000ff" "token_id" tid})]
    (is (= {:error "Shape 22222222-0000-0000-0000-0000000000ff not found in file 11111111-0000-0000-0000-000000000001"} result))
    (is (not (changes-sent? ctx)))))

(deftest set-token-with-closed-editor-asks-to-open-the-file
  (let [ctx    (fx/closed-editor-ctx (fx/file-responses fx/file))
        result (fx/call (fx/find-tool tokens/tools "set_token") ctx {"file_id" fid "shape_id" sid "token_id" tid})]
    (is (= {:error "Penpot editor is not connected; open the file in Penpot with MCP enabled"} result))
    (is (empty? (fx/rpc-commands ctx)))))

(deftest set-token-rejects-lists
  (is (contains? (:result (token-call "set_token" {"file_id" fid "shape_ids" [sid] "token_id" tid "attrs" ["fill"]}))
                 :error)))

(deftest remove-token-by-attribute
  (let [{:keys [ctx]} (token-call "remove_token" {"file_id" fid "shape_id" sid "attr" "fill"})]
    (is (= {"fileId" fid "shapeId" sid "attrs" [{"name" "fill" "key" "fill"}]} (fx/last-script-args ctx)))))

(deftest remove-token-by-token-unbinds-it-everywhere-on-the-shape
  (let [{:keys [ctx]} (token-call "remove_token" {"file_id" fid "shape_id" sid "token_id" tid})
        args          (fx/last-script-args ctx)]
    (is (= "color.primary" (get args "tokenName")))
    (is (= 36 (count (get args "attrs"))))))

(deftest remove-token-needs-exactly-one-of-token-and-attribute
  (doseq [args [{"file_id" fid "shape_id" sid}
                {"file_id" fid "shape_id" sid "token_id" tid "attr" "fill"}]]
    (let [{:keys [ctx result]} (token-call "remove_token" args)]
      (is (= {:error "Pass exactly one of token_id and attr"} result))
      (is (empty? @(:scripts ctx))))))

(deftest remove-token-of-unknown-shape-changes-nothing
  (let [{:keys [ctx result]} (token-call "remove_token" {"file_id" fid "shape_id" "22222222-0000-0000-0000-0000000000ff" "attr" "fill"})]
    (is (= {:error "Shape 22222222-0000-0000-0000-0000000000ff not found in file 11111111-0000-0000-0000-000000000001"} result))
    (is (not (changes-sent? ctx)))))

(deftest export-png-returns-image-content
  (let [ctx (fx/plugin-ctx {:__type "base64" :data "iVBORw0KGgo="})
        res (tool/invoke (fx/find-tool export/tools "export_shape") ctx {"file_id" fid "shape_id" sid})]
    (is (= {:content [{:type :image :data "iVBORw0KGgo=" :mime-type "image/png"}] :error? false} res))
    (is (= {"fileId" fid "shapeId" sid "format" "png" "mode" "shape" "maxSize" 768} (fx/last-script-args ctx)))))

(deftest export-limits-the-longer-side
  (let [ctx (fx/plugin-ctx {:__type "base64" :data "AA=="})]
    (tool/invoke (fx/find-tool export/tools "export_shape") ctx {"file_id" fid "shape_id" sid "max_size" 800})
    (is (= 800 (get (fx/last-script-args ctx) "maxSize")))
    (is (str/includes? (last @(:scripts ctx)) "Math.min(1, args.maxSize / Math.max(s.width, s.height))")))
  (is (true? (:error? (tool/invoke (fx/find-tool export/tools "export_shape") (fx/plugin-ctx nil)
                                   {"file_id" fid "shape_id" sid "max_size" 1569})))))

(defn- png-b64 [w h]
  (let [img (java.awt.image.BufferedImage. w h java.awt.image.BufferedImage/TYPE_INT_ARGB)
        out (java.io.ByteArrayOutputStream.)]
    (javax.imageio.ImageIO/write img "png" out)
    (.encodeToString (java.util.Base64/getEncoder) (.toByteArray out))))

(defn- size-of [b64]
  (let [img (javax.imageio.ImageIO/read (java.io.ByteArrayInputStream. (.decode (java.util.Base64/getDecoder) ^String b64)))]
    [(.getWidth img) (.getHeight img)]))

(deftest a-fill-image-is-scaled-down-to-max-size
  (let [ctx (fx/plugin-ctx {:__type "base64" :data (png-b64 3000 1500)})
        res (tool/invoke (fx/find-tool export/tools "export_shape") ctx {"file_id" fid "shape_id" sid "mode" "fill"})]
    (is (= [768 384] (size-of (get-in res [:content 0 :data]))))
    (is (= "image/png" (get-in res [:content 0 :mime-type])))))

(deftest a-small-fill-image-keeps-its-size
  (let [ctx (fx/plugin-ctx {:__type "base64" :data (png-b64 300 200)})
        res (tool/invoke (fx/find-tool export/tools "export_shape") ctx {"file_id" fid "shape_id" sid "mode" "fill" "max_size" 1000})]
    (is (= [300 200] (size-of (get-in res [:content 0 :data]))))))

(deftest export-svg-returns-svg-text
  (let [svg "<svg xmlns=\"http://www.w3.org/2000/svg\"/>"
        ctx (fx/plugin-ctx {:__type "base64" :data (.encodeToString (java.util.Base64/getEncoder) (.getBytes svg "UTF-8"))})]
    (is (= {"svg" svg} (fx/call (fx/find-tool export/tools "export_shape") ctx {"file_id" fid "shape_id" sid "format" "svg"})))))

(deftest export-requires-shape-id
  (let [ctx (fx/plugin-ctx {:__type "base64" :data "AA=="})]
    (is (true? (:error? (tool/invoke (fx/find-tool export/tools "export_shape") ctx {"file_id" fid}))))
    (is (true? (:error? (tool/invoke (fx/find-tool export/tools "export_shape") ctx {"file_id" fid "page_id" (str fx/page-id) "shape_id" sid}))))
    (is (empty? @(:scripts ctx)))))

(deftest export-fill-mode-only-png
  (is (contains? (fx/call (fx/find-tool export/tools "export_shape") (fx/plugin-ctx nil)
                          {"file_id" fid "shape_id" sid "format" "svg" "mode" "fill"})
                 :error)))

(deftest export-without-image-data-is-an-error
  (is (= {:content [{:type :text :text "Internal error in tool export_shape"}] :error? true}
         (tool/invoke (fx/find-tool export/tools "export_shape") (fx/plugin-ctx {:unexpected true}) {"file_id" fid "shape_id" sid}))))

(deftest token-scripts-prefer-active-sets-and-verify-removal
  (let [ctx (fx/plugin-ctx editor-answer (fx/file-responses fx/file))]
    (fx/call (fx/find-tool tokens/tools "remove_token") ctx {"file_id" fid "shape_id" sid "attr" "fill"})
    (is (str/includes? (last @(:scripts ctx)) "set.active"))
    (is (str/includes? (last @(:scripts ctx)) "fail('token-not-removed'"))))

(deftest export-encodes-bytes-inside-the-script
  (let [ctx (fx/plugin-ctx {:__type "base64" :data "AA=="})]
    (tool/invoke (fx/find-tool export/tools "export_shape") ctx {"file_id" fid "shape_id" sid})
    (is (str/includes? (last @(:scripts ctx)) "return { __type: 'base64', data: btoa(binary) };"))))

(defn- spacing-call [tool-name args]
  (let [ctx (fx/plugin-ctx (fn [code]
                             (cond
                               (str/includes? code read/token-body) {:id tid :name "space.m" :type "spacing"}
                               (str/includes? code read/shape-info-body) {:type "board" :layout "flex" :parentLayout false}
                               :else {:id sid}))
                           {})]
    {:ctx ctx :result (fx/call (fx/find-tool tokens/tools tool-name) ctx args)}))

(deftest a-group-attribute-binds-all-its-sides-in-one-call
  (let [{:keys [ctx]} (spacing-call "set_token" {"file_id" fid "shape_id" sid "token_id" tid "attr" "padding"})]
    (is (= #{"paddingTop" "paddingRight" "paddingBottom" "paddingLeft"}
           (set (map #(get % "key") (get (fx/last-script-args ctx) "attrs")))))))

(deftest a-group-attribute-unbinds-all-its-sides
  (let [{:keys [ctx]} (spacing-call "remove_token" {"file_id" fid "shape_id" sid "attr" "gap"})]
    (is (= #{"rowGap" "columnGap"} (set (map #(get % "key") (get (fx/last-script-args ctx) "attrs")))))))

(deftest a-group-attribute-is-checked-against-the-token-type
  (let [{:keys [ctx result]} (token-call "set_token" {"file_id" fid "shape_id" sid "token_id" tid "attr" "padding"})]
    (is (re-find #"does not take a color token" (:error result)))
    (is (not (changes-sent? ctx)))))

(deftest export-opens-the-page-of-the-shape-first
  (let [ctx (fx/plugin-ctx (fn [code] (if (str/includes? code "return { switched") {:switched true} {:__type "base64" :data "AA=="})))]
    (tool/invoke (fx/find-tool export/tools "export_shape") ctx {"file_id" fid "shape_id" sid})
    (is (= 2 (count @(:scripts ctx))))
    (is (str/includes? (first @(:scripts ctx)) "await openPage(page);"))))

(deftest export-waits-longer-than-an-edit
  (let [seen (atom [])
        ctx  (assoc (fx/plugin-ctx {:__type "base64" :data "AA=="})
                    :execute (fn [code] (swap! seen conj bridge/*task-timeout-ms*)
                               {:result (if (str/includes? code "return { switched") {:switched false} {:__type "base64" :data "AA=="}) :changed false}))]
    (tool/invoke (fx/find-tool export/tools "export_shape") ctx {"file_id" fid "shape_id" sid})
    (is (= [120000] (distinct (rest @seen))))))

(deftest a-large-svg-export-gives-its-size-and-a-download
  (let [svg (str "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"400\" height=\"300\">" (apply str (repeat 20000 "<rect/>")) "</svg>")
        ctx (assoc (fx/plugin-ctx {:__type "base64" :data (.encodeToString (java.util.Base64/getEncoder) (.getBytes svg "UTF-8"))})
                   :exports (exports/store {:now (constantly 0)}))
        res (fx/call (fx/find-tool export/tools "export_shape") ctx {"file_id" fid "shape_id" sid "format" "svg"})]
    (is (= {"width" "400" "height" "300"} (select-keys res ["width" "height"])))
    (is (re-find #"curl -o shape-export\.zip" (get-in res ["full_result" "download"])))))
