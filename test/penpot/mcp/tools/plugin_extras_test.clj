(ns penpot.mcp.tools.plugin-extras-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools.export :as export]
   [penpot.mcp.tools.tokens :as tokens])
  (:import
   (java.io ByteArrayInputStream)
   (java.util Base64)
   (javax.imageio ImageIO)))

(def ^:private tools (into {} (map (juxt :name identity)) (concat tokens/tools export/tools)))

(defn- run [scenario]
  (let [replayed (replay/run (tools (:tool (replay/recording scenario))) scenario)]
    (is (empty? (:left replayed)) (str scenario " left recorded requests unused"))
    (replay/data replayed)))

(defn- args [scenario]
  (:args (replay/recording scenario)))

(defn- changed-tokens [scenario]
  (get-in (run scenario) ["shape" "changed" "tokens"]))

(defn- applies-a-token? [scenario]
  (some #(str/includes? % "applyToken") (replay/editor-scripts scenario)))

(defn- refused-before-asking-penpot [tool-name scenario changes]
  (:error (replay/data (replay/run-without-penpot (tools tool-name) (merge (args scenario) changes)))))

(deftest a-color-token-binds-the-fill-by-default
  (let [result (run "tokens/set-color-default")]
    (is (= (get (args "tokens/set-color-default") "shape_id") (get-in result ["shape" "id"])))
    (is (= ["fill"] (keys (get-in result ["shape" "changed" "tokens"]))))
    (is (empty? (replay/requests "tokens/set-color-default")) "token checks never download the file")))

(deftest an-attribute-binds-only-that-attribute
  (is (= ["strokeColor"] (keys (changed-tokens "tokens/set-color-stroke")))))

(deftest a-token-already-bound-changes-nothing
  (is (= {} (get-in (run "tokens/set-bound-again") ["shape" "changed"]))))

(deftest spacing-on-a-layout-child-binds-its-margins
  (let [bound (changed-tokens "tokens/set-spacing-layout-child")]
    (is (= #{"marginTop" "marginRight" "marginBottom" "marginLeft"} (set (keys bound))))
    (is (= 1 (count (set (vals bound)))))))

(deftest a-group-attribute-binds-and-unbinds-all-its-sides
  (let [sides #{"paddingTop" "paddingRight" "paddingBottom" "paddingLeft"}]
    (is (every? (set (keys (changed-tokens "tokens/set-padding-group"))) sides))
    (is (not-any? (set (keys (changed-tokens "tokens/remove-padding-group"))) sides))))

(deftest a-token-is-removed-by-attribute-or-by-token
  (is (not (contains? (changed-tokens "tokens/remove-by-attribute") "fill")))
  (is (not (contains? (changed-tokens "tokens/remove-by-token") "fill"))))

(deftest wrong-tokens-and-shapes-are-refused-before-anything-changes
  (doseq [[scenario message] [["tokens/set-wrong-attribute" #"does not take a color token"]
                              ["tokens/set-unknown-token" #"Token .* not found"]
                              ["tokens/set-unknown-shape" #"Shape .* not found"]
                              ["tokens/remove-unknown-shape" #"Shape .* not found"]]]
    (is (re-find message (:error (run scenario))) scenario)
    (is (not (applies-a-token? scenario)) scenario)))

(deftest a-closed-editor-asks-to-open-the-file
  (is (re-find #"open the file in Penpot" (:error (run "tokens/set-closed-editor")))))

(deftest arguments-are-checked-before-asking-penpot
  (is (re-find #"attr" (refused-before-asking-penpot "set_token" "tokens/set-color-stroke" {"attr" "notAnAttribute"})))
  (is (re-find #"attr" (refused-before-asking-penpot "set_token" "tokens/set-color-stroke" {"attr" ["fill"]})))
  (is (refused-before-asking-penpot "remove_token" "tokens/remove-by-token" {"attr" "fill"}))
  (is (refused-before-asking-penpot "remove_token" "tokens/remove-by-attribute" {"attr" nil}))
  (is (refused-before-asking-penpot "export_shape" "export/png-board" {"shape_id" nil})))

(defn- image-size* [{:keys [data]}]
  (when-let [img (ImageIO/read (ByteArrayInputStream. (.decode (Base64/getDecoder) ^String data)))]
    [(.getWidth img) (.getHeight img)]))

(defn- image-size [image]
  (or (image-size* image) (throw (ex-info "The image cannot be read" {}))))

(deftest a-png-export-fits-the-requested-size
  (let [image (:image (run "export/png-board"))]
    (is (<= (apply max (image-size image)) 768)))
  (let [image (:image (run "export/png-small"))]
    (is (= "image/png" (:mime-type image)))
    (is (<= (apply max (image-size image)) 300))))

(deftest an-image-fill-is-scaled-to-the-default-size-and-labelled-by-its-format
  (let [image (:image (run "export/fill-image"))]
    (is (some? (image-size* image)) "the fill image of the real file can be read and scaled")
    (when-let [size (image-size* image)]
      (is (<= (apply max size) 768)))))

(deftest image-fills-export-only-as-png
  (is (re-find #"only be exported as png" (:error (run "export/fill-svg")))))

(deftest a-large-svg-export-comes-as-a-download
  (is (re-find #"^curl -o shape-export\.zip " (get-in (run "export/svg-board") ["full_result" "download"]))))

(deftest an-export-opens-the-page-of-the-shape-first
  (is (str/includes? (first (replay/editor-scripts "export/png-board")) "switched")))
