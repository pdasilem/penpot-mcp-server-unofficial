(ns penpot.mcp.recording.scenarios.tokens
  (:require
   [penpot.mcp.recording.data :as d]))

(defn- own? [s] (not (:shape-ref s)))

(defn- plain-rect [f] (d/fresh-shape f #(and (own? %) (= :rect (:type %)) (empty? (:applied-tokens %))) "own rectangle without tokens"))

(defn- with-fill-token [f] (d/fresh-shape f #(and (own? %) (get-in % [:applied-tokens :fill])) "own shape with a fill token"))

(defn- layout-child [f] (d/fresh-shape f #(and (own? %) (#{:rect :frame :text} (:type %)) (:layout (d/parent f %)) (not-any? (or (:applied-tokens %) {}) [:m1 :m2 :m3 :m4])) "own layout child without margin tokens"))

(defn- layout-board [f] (d/fresh-shape f #(and (own? %) (= :frame (:type %)) (:layout %) (not-any? (or (:applied-tokens %) {}) [:p1 :p2 :p3 :p4])) "own layout board without padding tokens"))

(defn- padded-board [f] (d/fresh-shape f #(and (own? %) (:layout %) (every? (or (:applied-tokens %) {}) [:p1 :p2 :p3 :p4])) "own layout board with padding tokens"))

(defn- set-args [f shape token & kvs]
  (merge {"file_id" (:fid f) "shape_id" (str (:id shape)) "token_id" (str (:id token))} (apply hash-map kvs)))

(def scenarios
  [{:name "tokens/set-color-default" :tool "set_token" :file :scratch :editor true
    :args #(set-args % (plain-rect %) (d/token % :color))}
   {:name "tokens/set-color-stroke" :tool "set_token" :file :scratch :editor true
    :args #(set-args % (plain-rect %) (d/token % :color) "attr" "strokeColor")}
   {:name "tokens/set-bound-again" :tool "set_token" :file :scratch :editor true
    :args #(let [s (with-fill-token %)] (set-args % s (d/token-named % (get-in s [:applied-tokens :fill]))))}
   {:name "tokens/set-wrong-attribute" :tool "set_token" :file :scratch :editor true
    :args #(set-args % (plain-rect %) (d/token % :color) "attr" "paddingTop")}
   {:name "tokens/set-unknown-token" :tool "set_token" :file :scratch :editor true
    :args #(assoc (set-args % (plain-rect %) (d/token % :color)) "token_id" (str (:id (first (d/components %)))))}
   {:name "tokens/set-unknown-shape" :tool "set_token" :file :scratch :editor true
    :args #(assoc (set-args % (plain-rect %) (d/token % :color)) "shape_id" (:absent-shape-id %))}
   {:name "tokens/set-closed-editor" :tool "set_token"
    :args #(set-args % (plain-rect %) (d/token % :color))}
   {:name "tokens/set-spacing-layout-child" :tool "set_token" :file :scratch :editor true
    :args #(set-args % (layout-child %) (d/token % :spacing))}
   {:name "tokens/set-padding-group" :tool "set_token" :file :scratch :editor true
    :args #(set-args % (layout-board %) (d/token % :spacing) "attr" "padding")}
   {:name "tokens/remove-padding-group" :tool "remove_token" :file :scratch :editor true
    :args #(hash-map "file_id" (:fid %) "shape_id" (str (:id (padded-board %))) "attr" "padding")}
   {:name "tokens/remove-by-attribute" :tool "remove_token" :file :scratch :editor true
    :args #(hash-map "file_id" (:fid %) "shape_id" (str (:id (with-fill-token %))) "attr" "fill")}
   {:name "tokens/remove-by-token" :tool "remove_token" :file :scratch :editor true
    :args #(let [s (with-fill-token %)] (hash-map "file_id" (:fid %) "shape_id" (str (:id s)) "token_id" (str (:id (d/token-named % (get-in s [:applied-tokens :fill]))))))}
   {:name "tokens/remove-unknown-shape" :tool "remove_token" :file :scratch :editor true
    :args #(hash-map "file_id" (:fid %) "shape_id" (:absent-shape-id %) "attr" "fill")}])
