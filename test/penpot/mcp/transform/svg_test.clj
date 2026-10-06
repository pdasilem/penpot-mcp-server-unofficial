(ns penpot.mcp.transform.svg-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.real-file :as real]
   [penpot.mcp.transform.geometry :as geometry]
   [penpot.mcp.transform.svg :as svg]))

(defn- n [x]
  (let [r (/ (Math/round (* 100.0 (double x))) 100.0)]
    (if (== r (Math/floor r)) (str (long r)) (str r))))

(deftest every-shape-of-the-file-renders-a-standalone-document
  (doseq [{:keys [shape objects]} (real/shapes)
          :let [doc (svg/shape->svg objects shape)
                {:keys [x y width height]} (geometry/bounds shape)]]
    (is (str/starts-with? doc (str "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"" (n x) " " (n y) " " (n width) " " (n height) "\""))
        (str (:id shape)))
    (is (not (re-find #"NaN|null|Infinity" doc)) (str (:id shape)))))

(deftest paths-render-their-content-and-ellipses-render-as-ellipses
  (doseq [{:keys [shape objects]} (real/having #(and (= :path (:type %)) (:content %) (not (:hidden %))) "visible path")]
    (is (re-find #"<path d=\"M" (svg/shape->svg objects shape)) (str (:id shape))))
  (doseq [{:keys [shape objects]} (real/having #(and (= :circle (:type %)) (not (:hidden %))) "visible ellipse")]
    (is (str/includes? (svg/shape->svg objects shape) "<ellipse ") (str (:id shape)))))

(deftest hidden-children-are-left-out
  (doseq [{:keys [shape objects]} (take 50 (real/having #(seq (:shapes %)) "shape with children"))
          :let [hidden (filter #(:hidden (get objects %)) (:shapes shape))]
          :when (seq hidden)]
    (is (= (svg/shape->svg objects shape)
           (svg/shape->svg (apply dissoc objects hidden) (update shape :shapes #(vec (remove (set hidden) %)))))
        (str (:id shape)))))
