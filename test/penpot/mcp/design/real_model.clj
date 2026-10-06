(ns penpot.mcp.design.real-model
  (:require
   [penpot.mcp.design.fixture :as fixture]))

(defn default-combination []
  (first (filter :default? (:combinations (fixture/model)))))

(defn default-tokens []
  (:tokens (default-combination)))

(defn colors []
  (filter #(= :color (get-in % [:value :kind])) (default-tokens)))

(defn uniform-tokens []
  (let [uniform (set (:uniform (fixture/model)))]
    (filter #(uniform (:name %)) (default-tokens))))

(defn hex [{:keys [r g b]}]
  (format "#%02x%02x%02x" r g b))

(defn rounded [x]
  (/ (Math/round (* 10000.0 (double x))) 10000.0))
