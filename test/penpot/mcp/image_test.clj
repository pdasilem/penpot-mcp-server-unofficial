(ns penpot.mcp.image-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.image :as image])
  (:import
   (java.awt.image BufferedImage)
   (java.io ByteArrayInputStream ByteArrayOutputStream)
   (javax.imageio ImageIO)))

(defn- encoded [w h fmt]
  (let [img (BufferedImage. w h BufferedImage/TYPE_INT_RGB)
        out (ByteArrayOutputStream.)]
    (ImageIO/write img ^String fmt out)
    (.toByteArray out)))

(defn- size-of [^bytes data]
  (let [img (ImageIO/read (ByteArrayInputStream. data))]
    [(.getWidth img) (.getHeight img)]))

(deftest the-longer-side-is-scaled-to-the-limit
  (is (= [768 384] (size-of (:bytes (image/fit (encoded 4000 2000 "png") 768)))))
  (is (= [400 768] (size-of (:bytes (image/fit (encoded 1000 1920 "jpg") 768))))))

(deftest a-scaled-image-is-a-png
  (is (= "image/png" (:mime-type (image/fit (encoded 2000 1000 "jpg") 768)))))

(deftest an-image-within-the-limit-is-returned-as-it-is
  (let [data (encoded 300 200 "jpg")]
    (is (= {:bytes data :mime-type "image/jpeg"} (update (image/fit data 768) :bytes #(if (identical? data %) data %))))))

(deftest data-that-is-not-an-image-is-returned-as-it-is
  (let [data (.getBytes "not an image")]
    (is (= {:bytes data :mime-type nil} (image/fit data 768)))))
