(ns penpot.mcp.image
  (:import
   (java.awt RenderingHints)
   (java.awt.image BufferedImage)
   (java.io ByteArrayInputStream ByteArrayOutputStream)
   (javax.imageio ImageIO ImageReader)))

(defn- reader-for [stream]
  (let [readers (ImageIO/getImageReaders stream)]
    (when (.hasNext readers)
      (doto ^ImageReader (.next readers) (.setInput stream true true)))))

(defn- mime-type [^ImageReader reader]
  (first (.getMIMETypes (.getOriginatingProvider reader))))

(defn- decode-subsampled [^ImageReader reader factor]
  (let [param (.getDefaultReadParam reader)]
    (.setSourceSubsampling param factor factor 0 0)
    (.read reader 0 param)))

(defn- resized [^BufferedImage img w h]
  (let [out (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g   (.createGraphics out)]
    (try
      (.setRenderingHint g RenderingHints/KEY_INTERPOLATION RenderingHints/VALUE_INTERPOLATION_BILINEAR)
      (.drawImage g img 0 0 w h nil)
      out
      (finally (.dispose g)))))

(defn- png [^BufferedImage img]
  (let [out (ByteArrayOutputStream.)]
    (ImageIO/write img "png" out)
    (.toByteArray out)))

(defn- scaled [^ImageReader reader w h max-side]
  (let [longer (max w h)
        ratio  (/ (double max-side) longer)
        target [(max 1 (Math/round (* w ratio))) (max 1 (Math/round (* h ratio)))]
        img    (decode-subsampled reader (int (max 1 (quot longer max-side))))]
    (png (resized img (first target) (second target)))))

(defn fit [^bytes data max-side]
  (with-open [stream (ImageIO/createImageInputStream (ByteArrayInputStream. data))]
    (if-let [reader (some-> stream reader-for)]
      (try
        (let [w (.getWidth reader 0) h (.getHeight reader 0)]
          (if (<= (max w h) max-side)
            {:bytes data :mime-type (mime-type reader)}
            {:bytes (scaled reader w h max-side) :mime-type "image/png"}))
        (finally (.dispose reader)))
      {:bytes data :mime-type nil})))
