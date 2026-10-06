(ns penpot.mcp.image-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.image :as image]
   [penpot.mcp.replay :as replay])
  (:import
   (java.io ByteArrayInputStream)
   (java.util Base64)
   (javax.imageio ImageIO)))

(defn- recorded-bytes [scenario]
  (let [answer (some #(when (= "base64" (get-in % [:result :__type])) (:result %)) (replay/editor-answers scenario))]
    (.decode (Base64/getDecoder) ^String (:data answer))))

(defn- size-of [^bytes data]
  (let [img (ImageIO/read (ByteArrayInputStream. data))]
    [(.getWidth img) (.getHeight img)]))

(deftest the-fill-image-penpot-stores-can-be-read
  (is (some? (ImageIO/read (ByteArrayInputStream. (recorded-bytes "export/fill-image"))))))

(deftest a-fill-image-larger-than-the-limit-becomes-a-smaller-png
  (let [data  (recorded-bytes "export/fill-image")
        limit (dec (apply max (size-of data)))
        {:keys [bytes mime-type]} (image/fit data limit)]
    (is (= "image/png" mime-type))
    (is (= limit (apply max (size-of bytes))))))

(deftest an-image-within-the-limit-is-returned-as-it-is
  (let [data (recorded-bytes "export/fill-image")
        {:keys [bytes mime-type]} (image/fit data (apply max (size-of data)))]
    (is (identical? data bytes))
    (is (= "image/webp" mime-type))))

(deftest an-exported-png-is-scaled-to-the-limit
  (let [{:keys [bytes]} (image/fit (recorded-bytes "export/png-board") 100)]
    (is (= 100 (apply max (size-of bytes))))))

(deftest data-that-is-not-an-image-is-returned-as-it-is
  (let [data (recorded-bytes "export/svg-board")]
    (is (= {:bytes data :mime-type nil} (image/fit data 100)))))
