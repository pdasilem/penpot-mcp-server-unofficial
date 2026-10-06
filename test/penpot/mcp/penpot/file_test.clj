(ns penpot.mcp.penpot.file-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.real-file :as real]
   [penpot.mcp.replay :as replay]))

(defn- error-of [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e e)))

(defn- arg [scenario k]
  (get (:args (replay/recording scenario)) k))

(defn- file-id [scenario]
  (parse-uuid (arg scenario "file_id")))

(defn- left [ctx]
  (map :cmd (filter #(= :rpc (:kind %)) @(:replay/pending ctx))))

(deftest pages-come-in-file-order-and-are-found-by-id
  (let [f (real/file)]
    (is (= (get-in f [:data :pages]) (map :id (file/pages f))))
    (doseq [{:keys [id name]} (file/pages f)]
      (is (= name (:name (file/page f id)))))))

(deftest every-shape-is-located-on-its-page
  (doseq [{:keys [shape page]} (take 300 (real/shapes))]
    (is (= {:page-id (:id page) :shape shape} (file/locate-shape (real/file) (:id shape))))))

(deftest unknown-pages-and-shapes-are-user-errors
  (let [absent (parse-uuid (arg "shapes/list-absent-page" "page_id"))]
    (is (= :tool/user-error (:type (ex-data (error-of #(file/page (real/file) absent))))))
    (is (= :tool/user-error (:type (ex-data (error-of #(file/locate-shape (real/file) absent))))))))

(deftest one-page-is-read-without-the-file
  (let [ctx  (replay/context "shapes/list-model")
        page (file/read-page ctx (file-id "shapes/list-model") (parse-uuid (arg "shapes/list-model" "page_id")))]
    (is (= "Model" (:name page)))
    (is (empty? (left ctx)))))

(deftest a-page-the-file-does-not-have-is-a-user-error
  (let [ctx (replay/context "shapes/list-absent-page")]
    (is (re-find #"not found" (ex-message (error-of #(file/read-page ctx (file-id "shapes/list-absent-page") (parse-uuid (arg "shapes/list-absent-page" "page_id")))))))))

(deftest the-revision-comes-from-the-project-listing
  (let [ctx (replay/context "files/get-file-editor")
        rev (file/revision (:rpc ctx) (file-id "files/get-file-editor"))]
    (is (= (:revn (real/file)) (:revn rev)))
    (is (empty? (left ctx)))))

(deftest a-whole-file-under-the-limit-is-read-once-per-revision
  (let [ctx (assoc (replay/context "library/colors" "library/colors") :file-cache (atom nil))
        fid (file-id "library/colors")]
    (is (= fid (:id (file/read-whole ctx fid))))
    (is (= fid (:id (file/read-whole ctx fid))))
    (is (= [:get-file] (left ctx)) "the second read takes the cached file")))

(deftest a-whole-file-over-the-limit-is-refused-without-downloading-it
  (let [ctx (replay/context "library/components-large-file")
        ex  (error-of #(file/read-whole ctx (file-id "library/components-large-file")))]
    (is (re-find #"more than the 5000" (ex-message ex)))
    (is (empty? (left ctx)))))

(deftest the-open-editor-gives-the-pages-and-each-page-is-read-on-demand
  (let [ctx   (replay/context "token-usage/editor")
        names (map :name (first (replay/editor-answers "token-usage/editor")))
        pages (file/read-pages ctx (file-id "token-usage/editor"))]
    (is (= (:name (first (file/pages (real/file)))) (:name (first pages))))
    (is (= (dec (count (get-in (real/file) [:data :pages]))) (count (left ctx))) "only the first page is read so far")
    (is (= (map :name (file/pages (real/file))) (map :name pages)))
    (is (seq names))))

(deftest without-the-editor-the-pages-come-from-the-whole-file
  (let [ctx   (replay/context "shapes/search-file-saved")
        pages (file/read-pages ctx (file-id "shapes/search-file-saved"))]
    (is (= (map :name (file/pages (real/file))) (map :name pages)))
    (is (empty? (left ctx)))))

(deftest a-shape-is-read-from-the-given-page
  (let [ctx (replay/context "shapes/get-path")
        {:keys [page shape]} (file/read-shape ctx (file-id "shapes/get-path") (parse-uuid (arg "shapes/get-path" "shape_id")) (parse-uuid (arg "shapes/get-path" "page_id")))]
    (is (= (arg "shapes/get-path" "page_id") (str (:id page))))
    (is (= :path (:type shape)))))

(deftest the-editor-finds-the-page-of-a-shape
  (let [ctx (replay/context "shapes/get-without-page-editor")
        {:keys [page]} (file/read-shape ctx (file-id "shapes/get-without-page-editor") (parse-uuid (arg "shapes/get-without-page-editor" "shape_id")) nil)]
    (is (= "Model" (:name page)))))

(deftest without-page-and-editor-a-shape-asks-for-the-page
  (let [ctx (replay/context "shapes/get-without-page")
        ex  (error-of #(file/read-shape ctx (file-id "shapes/get-without-page") (parse-uuid (arg "shapes/get-without-page" "shape_id")) nil))]
    (is (re-find #"^Pass page_id" (ex-message ex)))))

(deftest an-idle-whole-file-is-dropped-from-the-cache
  (let [cache (atom nil)
        ctx   (assoc (replay/context "library/colors") :file-cache cache)]
    (file/read-whole ctx (file-id "library/colors"))
    (let [used (:used @cache)]
      (file/evict-idle! cache (+ used file/cache-idle-ms))
      (is (some? @cache))
      (file/evict-idle! cache (+ used file/cache-idle-ms 1))
      (is (nil? @cache)))))
