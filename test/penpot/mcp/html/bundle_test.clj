(ns penpot.mcp.html.bundle-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.bundle :as bundle])
  (:import
   (java.io ByteArrayOutputStream)
   (java.util Base64)
   (java.util.zip GZIPOutputStream)))

(def asset-id "11111111-2222-4333-8444-555555555555")
(def page-id "66666666-7777-4888-8999-000000000000")

(defn- gzip-b64 [^String text]
  (let [out (ByteArrayOutputStream.)]
    (with-open [gz (GZIPOutputStream. out)]
      (.write gz (.getBytes text "UTF-8")))
    (.encodeToString (Base64/getEncoder) (.toByteArray out))))

(defn- bundle-html [template manifest page-order]
  (str "<!DOCTYPE html><html><head><script>loader()</script></head><body>"
       "<script type=\"__bundler/manifest\">" (json/write-str manifest) "</script>"
       "<script type=\"__bundler/page_order\">" (json/write-str page-order) "</script>"
       "<script type=\"__bundler/template\">" (str/replace (json/write-str template) "</" "<\\u002F") "</script>"
       "</body></html>"))

(deftest plain-html-is-returned-as-is
  (let [html "<html><body><div class=\"a\">Hi</div></body></html>"]
    (is (= {:html html :pages 0} (bundle/unpack html)))))

(deftest bundle-template-is-unpacked-with-assets-inlined
  (let [template (str "<html><head><style>.x{background:url(" asset-id ")}</style></head>"
                      "<body><img src=\"" asset-id "\" integrity=\"sha-1\" crossorigin=\"anonymous\"></body></html>")
        manifest {asset-id {:mime "image/svg+xml" :compressed true :data (gzip-b64 "<svg/>")}}
        result   (bundle/unpack (bundle-html template manifest []))
        data-uri (str "data:image/svg+xml;base64," (.encodeToString (Base64/getEncoder) (.getBytes "<svg/>" "UTF-8")))]
    (is (= 0 (:pages result)))
    (is (str/includes? (:html result) (str "url(" data-uri ")")))
    (is (str/includes? (:html result) (str "src=\"" data-uri "\"")))
    (is (not (str/includes? (:html result) "integrity=")))
    (is (not (str/includes? (:html result) "crossorigin=")))))

(deftest uncompressed-assets-keep-their-data
  (let [b64      (.encodeToString (Base64/getEncoder) (.getBytes "png-bytes" "UTF-8"))
        result   (bundle/unpack (bundle-html (str "<img src=\"" asset-id "\">") {asset-id {:mime "image/png" :compressed false :data b64}} []))]
    (is (str/includes? (:html result) (str "data:image/png;base64," b64)))))

(deftest nested-pages-are-counted-and-not-inlined
  (let [manifest {page-id {:mime "text/html" :compressed false
                           :data (.encodeToString (Base64/getEncoder) (.getBytes "<p>inner</p>" "UTF-8"))}}
        result   (bundle/unpack (bundle-html (str "<iframe src=\"about:blank#" page-id "\"></iframe>") manifest [page-id]))]
    (is (= 1 (:pages result)))
    (is (str/includes? (:html result) (str "about:blank#" page-id)))))

(deftest unpacked-assets-beyond-the-budget-are-refused
  (let [manifest {asset-id {"mime" "text/plain" "compressed" true "data" (gzip-b64 (apply str (repeat 5000 "a")))}}
        html     (bundle-html (str "<p>" asset-id "</p>") manifest [])]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unpack to more than"
                          (bundle/unpack html {:max-bytes 4000 :max-assets 10})))
    (is (string? (:html (bundle/unpack html {:max-bytes 10000 :max-assets 10}))))))

(deftest too-many-bundle-assets-are-refused
  (let [manifest (into {} (for [i (range 3)] [(str "id-" i) {"mime" "text/plain" "data" "YQ=="}]))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"more than 2 assets"
                          (bundle/unpack (bundle-html "<p></p>" manifest []) {:max-bytes 1000 :max-assets 2})))))
