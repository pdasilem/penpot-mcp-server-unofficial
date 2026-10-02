(ns penpot.mcp.tools.styles-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.styles :as styles]))

(def fid (str fx/file-id))
(def tid (str fx/text-id))
(def lib-id "66666666-0000-0000-0000-0000000000aa")

(defn- run [tool-name args]
  (let [ctx (fx/plugin-ctx {:shape {:id tid}})]
    {:result (fx/call (fx/find-tool styles/tools tool-name) ctx (merge {"file_id" fid} args))
     :args   (when (seq @(:scripts ctx)) (fx/last-script-args ctx))
     :script (last @(:scripts ctx))}))

(deftest range-style-sends-range-and-style
  (let [{:keys [args script]} (run "set_text_range_style" {"shape_id" tid "start" 0 "end" 4 "font_weight" "700"
                                                           "fills" [{"color" "#FF0000"}]})]
    (is (= {"fileId" fid "shapeId" tid "start" 0 "end" 4 "style" {"fontWeight" "700"}
            "fills" [{"fillColor" "#FF0000" "fillOpacity" 1}]}
           args))
    (is (str/includes? script "s.getRange(args.start, args.end)"))
    (is (str/includes? script "fail('bad-range'"))))

(deftest range-needs-something-to-change-and-a-forward-range
  (is (contains? (:result (run "set_text_range_style" {"shape_id" tid "start" 0 "end" 4})) :error))
  (is (= {:error "end must be greater than start"}
         (:result (run "set_text_range_style" {"shape_id" tid "start" 4 "end" 4 "font_size" 12})))))

(deftest typography-applies-to-whole-text-or-range
  (let [{:keys [args script]} (run "apply_typography" {"shape_id" tid "typography_id" (str fx/typography-id)})]
    (is (= {"fileId" fid "shapeId" tid "typographyId" (str fx/typography-id)} args))
    (is (str/includes? script "applyToText(s)")))
  (let [{:keys [args]} (run "apply_typography" {"shape_id" tid "typography_id" (str fx/typography-id)
                                                "library_file_id" lib-id "start" 2 "end" 5})]
    (is (= {"fileId" fid "shapeId" tid "typographyId" (str fx/typography-id) "libraryId" lib-id "start" 2 "end" 5} args)))
  (is (= {:error "Give both start and end, or neither"}
         (:result (run "apply_typography" {"shape_id" tid "typography_id" (str fx/typography-id) "start" 2})))))

(deftest library-color-targets-fill-or-stroke
  (let [{:keys [args script]} (run "apply_library_color" {"shape_id" tid "color_id" (str fx/color-id) "target" "stroke"})]
    (is (= {"fileId" fid "shapeId" tid "colorId" (str fx/color-id) "target" "stroke"} args))
    (is (str/includes? script "asStroke()")))
  (is (= "fill" (get (:args (run "apply_library_color" {"shape_id" tid "color_id" (str fx/color-id)})) "target"))))

(deftest image-fill-accepts-only-http-urls
  (is (= {"fileId" fid "shapeId" tid "url" "https://example.com/a.png" "name" "a"}
         (:args (run "set_image_fill" {"shape_id" tid "url" "https://example.com/a.png" "name" "a"}))))
  (is (contains? (:result (run "set_image_fill" {"shape_id" tid "url" "file:///etc/passwd"})) :error)))

(deftest creates-library-color
  (let [{:keys [args script]} (run "create_library_color" {"name" "Primary" "path" "Brand" "color" "#3366FF" "opacity" 0.8})]
    (is (= {"fileId" fid "name" "Primary" "path" "Brand" "color" "#3366FF" "opacity" 0.8} args))
    (is (str/includes? script "createColor()"))))

(deftest creates-library-typography
  (let [{:keys [args script]} (run "create_library_typography" {"name" "Heading" "font_family" "Work Sans" "font_size" 24
                                                                "font_weight" "700" "line_height" 1.2})]
    (is (= {"fileId" fid "name" "Heading" "fontFamily" "Work Sans" "fontSize" "24" "fontWeight" "700"
            "fontStyle" "normal" "lineHeight" "1.2"}
           args))
    (is (str/includes? script "penpot.fonts.findByName(args.fontFamily)"))))

(deftest image-name-falls-back-when-the-url-has-no-file-name
  (is (str/includes? (:script (run "set_image_fill" {"shape_id" tid "url" "https://example.com/"}))
                     "args.name || args.url.split('?')[0].split('/').pop() || 'image'")))
