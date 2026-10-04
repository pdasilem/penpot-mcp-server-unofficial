(ns ^:integration penpot.mcp.tools.plugin-it-test
  (:require
   [app.common.files.changes-builder :as pcb]
   [app.common.types.tokens-lib :as ctob]
   [app.common.uuid :as uuid]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.changes :as changes]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.test-client :as mcp]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools :as tools])
  (:import
   (java.util Base64)
   (java.util.concurrent TimeUnit)))

(def mcp-url (get it/env "PENPOT_IT_MCP_URL" "http://localhost:9001/mcp/stream"))
(def public-url (get it/env "PENPOT_IT_PUBLIC_URL" "http://localhost:9001"))
(def token-id (uuid/next))
(def set-name "it-brand")

(defn- add-tokens [client file-id]
  (changes/commit! client file-id
                   (fn []
                     (let [f      (file/fetch client file-id)
                           set-id (uuid/next)
                           lib    (-> (or (get-in f [:data :tokens-lib]) (ctob/make-tokens-lib))
                                      (ctob/add-set (ctob/make-token-set :id set-id :name set-name))
                                      (ctob/add-token set-id (ctob/make-token :id token-id :name "it.primary" :type :color :value "#3366FF"))
                                      (ctob/toggle-set-in-theme ctob/hidden-theme-id set-name))]
                       (-> (pcb/empty-changes)
                           (pcb/with-library-data (:data f))
                           (pcb/set-tokens-lib lib))))))

(defn- open-editor [team-id file-id]
  (let [pb   (doto (ProcessBuilder. ["node" "test/e2e/open-editor.js"])
               (.redirectErrorStream true))
        env  (.environment pb)]
    (doseq [[k v] {"PENPOT_URL" public-url "PENPOT_EMAIL" (get it/env "PENPOT_EMAIL")
                   "PENPOT_PASSWORD" (get it/env "PENPOT_PASSWORD") "TEAM_ID" (str team-id) "FILE_ID" (str file-id)}]
      (.put env k v))
    (when-let [chrome (get it/env "CHROME")] (.put env "CHROME" chrome))
    (let [proc      (.start pb)
          connected (promise)]
      (future
        (with-open [r (io/reader (.getInputStream proc))]
          (doseq [line (line-seq r)]
            (when (= "connected" line) (deliver connected true)))))
      (when-not (deref connected 90000 false)
        (.destroy proc)
        (throw (ex-info "Penpot plugin did not connect" {})))
      proc)))

(defn- close-editor [^Process proc]
  (.close (.getOutputStream proc))
  (when-not (.waitFor proc 15 TimeUnit/SECONDS)
    (.destroyForcibly proc)))

(defn- tool [session tool-name args]
  (let [result (mcp/call-tool session tool-name args)]
    (when (:isError result)
      (throw (ex-info (str tool-name " failed: " (get-in result [:content 0 :text])) {})))
    result))

(defn- data [session tool-name args]
  (json/read-str (get-in (tool session tool-name args) [:content 0 :text])))

(defn- shape-of [session file-id shape-id]
  (get (data session "get_shape" {:file_id file-id :shape_id shape-id}) "shape"))

(deftest plugin-tools-against-live-editor
  (let [client  (it/client)
        team-id (:default-team-id (rpc/call client :get-profile {}))]
    (it/with-temp-project client
      (fn [project]
        (let [file  (rpc/call client :create-file {:project-id (:id project) :name "it-plugin"})
              fid   (str (:id file))
              _     (add-tokens client (:id file))
              proc  (open-editor team-id (:id file))]
          (try
            (let [s     (mcp/connect (str mcp-url "?userToken=" (get it/env "PENPOT_MCP_KEY")))
                  board (get-in (data s "create_board" {:file_id fid :x 0 :y 0 :width 400 :height 300 :name "Card"})
                                ["shape" "id"])
                  _     (data s "set_fills" {:file_id fid :shape_id board :fills [{:color "#FFFFFF"}]})
                  _     (data s "set_toolset" {:name "export" :enabled true})]
              (testing "flex layout positions children"
                (data s "set_flex_layout" {:file_id fid :board_id board :dir "row" :column_gap 10
                                           :padding {:top 20 :right 20 :bottom 20 :left 20}})
                (let [r1 (get-in (data s "create_rect" {:file_id fid :parent_id board :x 0 :y 0 :width 100 :height 50 :name "R1"}) ["shape" "id"])
                      r2 (get-in (data s "create_rect" {:file_id fid :parent_id board :x 0 :y 0 :width 60 :height 60 :name "R2"}) ["shape" "id"])
                      before (into {} (map (fn [id] [id (shape-of s fid id)])) [r1 r2])]
                  (is (= #{20} (set (map #(get % "y") (vals before)))))
                  (is (= #{20 130} (set (map #(get % "x") (vals before)))))
                  (data s "resize" {:file_id fid :shape_id r1 :width 150 :height 50})
                  (let [after (into {} (map (fn [id] [id (shape-of s fid id)])) [r1 r2])]
                    (is (= 150 (get-in after [r1 "width"])))
                    (is (= #{20 180} (set (map #(get % "x") (vals after))))))
                  (testing "fills, rotation and tokens"
                    (data s "set_fills" {:file_id fid :shape_id r1 :fills [{:color "#FF0000" :opacity 0.5}]})
                    (is (= "#FF0000" (str/upper-case (get-in (shape-of s fid r1) ["fills" 0 "fill_color"]))))
                    (is (= 45 (get-in (data s "rotate" {:file_id fid :shape_id r2 :angle 45}) ["shape" "changed" "rotation"])))
                    (let [changed #(get-in (data s %1 (merge {:file_id fid :shape_id r1} %2)) ["shape" "changed"])]
                      (is (= {"fill" "it.primary"} (get (changed "set_token" {:token_id (str token-id)}) "tokens")))
                      (is (not (contains? (changed "set_token" {:token_id (str token-id)}) "tokens")))
                      (is (= {"fill" "it.primary" "strokeColor" "it.primary"}
                             (get (changed "set_token" {:token_id (str token-id) :attr "strokeColor"}) "tokens")))
                      (is (= {"strokeColor" "it.primary"} (get (changed "remove_token" {:attr "fill"}) "tokens")))
                      (is (not (contains? (changed "remove_token" {:attr "fill"}) "tokens")))
                      (is (= {} (get (changed "remove_token" {:token_id (str token-id)}) "tokens")))))))
              (testing "text"
                (let [t (get-in (data s "create_text" {:file_id fid :x 0 :y 400 :text "Hello MCP"}) ["shape" "id"])]
                  (is (= "32" (get-in (data s "set_text_style" {:file_id fid :shape_id t :font_size 32}) ["shape" "changed" "fontSize"])))
                  (is (= "Bye" (get-in (data s "set_text_content" {:file_id fid :shape_id t :text "Bye"}) ["shape" "changed" "characters"])))
                  (is (= board (get-in (data s "move_to_parent" {:file_id fid :shape_id t :parent_id board}) ["shape" "changed" "parentId"])))))
              (testing "pages are switched and concurrent calls are serialized"
                (let [page2 (get (data s "create_page" {:file_id fid :name "Second"}) "page_id")
                      _     (Thread/sleep 1500)
                      p2r   (data s "create_rect" {:file_id fid :page_id page2 :x 10 :y 10 :width 20 :height 20 :name "OnSecond"})
                      back  (data s "set_opacity" {:file_id fid :shape_id board :opacity 0.8})
                      calls [(future (data s "set_opacity" {:file_id fid :shape_id (get-in p2r ["shape" "id"]) :opacity 0.5}))
                             (future (data s "rename_shape" {:file_id fid :shape_id board :name "Card 2"}))]]
                  (is (= page2 (get-in p2r ["shape" "pageId"])))
                  (is (= 0.8 (get-in back ["shape" "changed" "opacity"])))
                  (is (= [0.5 "Card 2"] [(get-in @(first calls) ["shape" "changed" "opacity"]) (get-in @(second calls) ["shape" "changed" "name"])]))
                  (is (= ["OnSecond"] (mapv #(get % "name") (get (data s "list_shapes" {:file_id fid :page_id page2}) "shapes"))))
                  (is (= "image" (get-in (tool s "export_shape" {:file_id fid :shape_id (get-in p2r ["shape" "id"])}) [:content 0 :type])))
                  (testing "reads run inside the editor for shapes on a page that is not open"
                    (data s "rename_shape" {:file_id fid :shape_id board :name "Card 3"})
                    (is (= [[(get-in p2r ["shape" "id"]) page2 "rectangle"]]
                           (mapv (juxt #(get % "id") #(get % "page_id") #(get % "type"))
                                 (get (data s "search_shapes" {:file_id fid :query "onsecond"}) "shapes"))))
                    (is (= ["Card 3"] (mapv #(get % "name") (get (data s "search_shapes" {:file_id fid :query "card" :type "board"}) "shapes"))))
                    (is (str/starts-with? (str/trim (get (data s "get_shape_svg" {:file_id fid :shape_id (get-in p2r ["shape" "id"])}) "svg")) "<svg"))
                    (is (some? (get (data s "create_comment" {:file_id fid :frame_id board :x 5 :y 5 :content "On the card"}) "thread_id")))
                    (is (str/includes? (get-in (mcp/call-tool s "create_comment" {:file_id fid :page_id page2 :frame_id board :x 5 :y 5 :content "x"})
                                               [:content 0 :text])
                                       "not found on page")))))
              (testing "group, component and delete"
                (let [e1 (get-in (data s "create_ellipse" {:file_id fid :x 600 :y 0 :width 40 :height 40}) ["shape" "id"])
                      e2 (get-in (data s "create_ellipse" {:file_id fid :x 660 :y 0 :width 40 :height 40}) ["shape" "id"])
                      g  (get-in (data s "create_group" {:file_id fid :shape_ids [e1 e2] :name "Dots"}) ["shape" "id"])
                      c  (data s "create_component" {:file_id fid :shape_ids [g] :name "Dots"})]
                  (is (some? (get c "componentId")))
                  (is (= [[(get-in c ["shape" "id"]) (get c "componentId") fid true]]
                         (mapv (juxt #(get % "id") #(get % "component_id") #(get % "component_file") #(get % "is_main"))
                               (get (data s "get_component_instances" {:file_id fid :component_id (get c "componentId")}) "instances"))))
                  (is (= ["Dots"] (mapv #(get % "name") (get (data s "list_components" {:file_id fid}) "components"))))
                  (is (= [(get-in c ["shape" "id"])] (get (data s "delete_shapes" {:file_id fid :shape_ids [(get-in c ["shape" "id"])]}) "deleted")))))
              (testing "components and variants"
                (let [component (fn [x nm]
                                  (let [b (get-in (data s "create_board" {:file_id fid :x x :y 1000 :width 100 :height 40 :name nm}) ["shape" "id"])]
                                    (get (data s "create_component" {:file_id fid :shape_ids [b] :name nm}) "componentId")))
                      ca        (component 1000 "Primary")
                      cb        (component 1200 "Secondary")
                      inst      (data s "create_component_instance" {:file_id fid :component_id ca :x 1000 :y 1200})
                      props     #(get-in % ["variants" "properties"])]
                  (is (= ca (get inst "componentId")))
                  (is (= [1000 1200] [(get-in inst ["shape" "x"]) (get-in inst ["shape" "y"])]))
                  (is (= 2 (count (get-in (data s "create_variants" {:file_id fid :component_ids [ca cb]}) ["variants" "components"]))))
                  (data s "set_variant_property" {:file_id fid :component_id ca :property "State" :value "default"})
                  (let [v (data s "set_variant_property" {:file_id fid :component_id cb :property "State" :value "hover"})]
                    (is (some #{"State"} (props v)))
                    (is (= (props v) (props (data s "set_variant_property" {:file_id fid :component_id cb :property "State" :value "hover"})))))
                  (let [switched (data s "switch_variant" {:file_id fid :shape_id (get-in inst ["shape" "id"]) :property "State" :value "hover"})
                        copy     (get-in switched ["shape" "id"])]
                    (is (= cb (get switched "componentId")))
                    (is (= "hover" (get-in switched ["variantProperties" "State"])))
                    (let [renamed (props (data s "rename_variant_property" {:file_id fid :component_id ca :property "State" :new_name "Mode"}))]
                      (is (some #{"Mode"} renamed))
                      (is (not-any? #{"State"} renamed)))
                    (let [swapped (data s "swap_component" {:file_id fid :shape_id copy :component_id ca})
                          copy    (get-in swapped ["shape" "id"])]
                      (is (= ca (get swapped "componentId")))
                      (is (= ca (get (data s "reset_overrides" {:file_id fid :shape_id copy}) "componentId")))
                      (is (nil? (get (data s "detach_instance" {:file_id fid :shape_id copy}) "componentId")))))
                  (is (not-any? #{"Mode"} (props (data s "remove_variant_property" {:file_id fid :component_id ca :property "Mode"}))))))
              (testing "variant property edits keep copy overrides"
                (let [component (fn [x nm]
                                  (let [b (get-in (data s "create_board" {:file_id fid :x x :y 2000 :width 120 :height 40 :name nm}) ["shape" "id"])]
                                    (data s "create_text" {:file_id fid :parent_id b :x (+ x 10) :y 2010 :text "Label"})
                                    (get (data s "create_component" {:file_id fid :shape_ids [b] :name nm}) "componentId")))
                      c1    (component 1000 "Chip")
                      c2    (component 1200 "Chip Alt")
                      _     (data s "create_variants" {:file_id fid :component_ids [c1 c2]})
                      inst  (get (data s "create_component_instance" {:file_id fid :component_id c1 :x 1000 :y 2200}) "shape")
                      label (get-in (data s "get_shape_tree" {:file_id fid :page_id (get inst "pageId") :root_id (get inst "id") :depth 1})
                                    ["children" 0 "id"])
                      text? #(str/includes? (json/write-str (shape-of s fid label)) "Overridden")]
                  (data s "set_text_content" {:file_id fid :shape_id label :text "Overridden"})
                  (is (text?) "override before")
                  (data s "set_variant_property" {:file_id fid :component_id c1 :property "Size" :value "small"})
                  (is (text?) "after adding a property")
                  (data s "set_variant_property" {:file_id fid :component_id c1 :property "Size" :value "large"})
                  (is (text?) "after changing a value")
                  (data s "rename_variant_property" {:file_id fid :component_id c1 :property "Size" :new_name "Scale"})
                  (is (text?) "after renaming a property")
                  (data s "remove_variant_property" {:file_id fid :component_id c1 :property "Scale"})
                  (is (text?) "after removing a property")
                  (data s "reset_overrides" {:file_id fid :shape_id (get inst "id")})
                  (is (not (text?)) "reset restores the main component text")
                  (let [t0 (System/nanoTime)]
                    (data s "reset_overrides" {:file_id fid :shape_id (get inst "id")})
                    (text?)
                    (is (< (quot (- (System/nanoTime) t0) 1000000) 10000) "a reset without overrides does not block reads"))))
              (testing "appearance"
                (let [st      (fn [tool-name args] (get (data s tool-name (assoc args :file_id fid)) "state"))
                      flexb   (get-in (data s "create_board" {:file_id fid :x 0 :y 3000 :width 300 :height 100 :name "Flex"}) ["shape" "id"])
                      _       (data s "set_flex_layout" {:file_id fid :board_id flexb :dir "row"})
                      child   (get-in (data s "create_rect" {:file_id fid :parent_id flexb :x 0 :y 0 :width 50 :height 50}) ["shape" "id"])
                      plain   (get-in (data s "create_board" {:file_id fid :x 400 :y 3000 :width 200 :height 100 :name "Plain"}) ["shape" "id"])
                      inner   (get-in (data s "create_rect" {:file_id fid :parent_id plain :x 410 :y 3010 :width 50 :height 50}) ["shape" "id"])]
                  (is (= "fill" (get (st "set_layout_child" {:shape_id child :horizontal_sizing "fill"}) "horizontalSizing")))
                  (is (< 50 (get (shape-of s fid child) "width")))
                  (is (= {"top" 1 "right" 2 "bottom" 3 "left" 4}
                         (get (st "set_layout_child" {:shape_id child :margin {:top 1 :right 2 :bottom 3 :left 4}}) "margin")))
                  (is (= 10 (get (st "set_layout_child" {:shape_id child :min_width 10}) "minWidth")))
                  (is (str/includes? (get-in (mcp/call-tool s "set_layout_child" {:file_id fid :shape_id inner :absolute true}) [:content 0 :text])
                                     "not inside a flex or grid layout"))
                  (is (= [{"style" "drop-shadow" "offsetX" 0 "offsetY" 4 "blur" 8 "spread" 0 "hidden" false
                           "color" {"color" "#000000" "opacity" 0.25}}]
                         (st "set_shadows" {:shape_id inner :shadows [{:offset_y 4 :blur 8 :color "#000000" :opacity 0.25}]})))
                  (is (= [] (st "set_shadows" {:shape_id inner :shadows []})))
                  (is (= {"layerBlur" 5} (st "set_blur" {:shape_id inner :layer_blur 5})))
                  (is (= {} (st "set_blur" {:shape_id inner :layer_blur nil})))
                  (is (= "multiply" (st "set_blend_mode" {:shape_id inner :mode "multiply"})))
                  (is (= {"horizontal" "leftright" "vertical" "top"} (st "set_constraints" {:shape_id inner :horizontal "leftright" :vertical "top"})))
                  (is (true? (st "set_proportion_lock" {:shape_id inner :locked true})))
                  (is (= {"horizontal" true "vertical" false} (st "set_flip" {:shape_id inner :horizontal true})))
                  (is (= {"horizontal" true "vertical" false} (st "set_flip" {:shape_id inner :horizontal true})))
                  (is (not= inner (get-in (data s "duplicate_shape" {:file_id fid :shape_id inner}) ["shape" "id"])))
                  (let [grid (get-in (data s "create_board" {:file_id fid :x 700 :y 3000 :width 200 :height 200 :name "Grid"}) ["shape" "id"])
                        _    (data s "set_grid_layout" {:file_id fid :board_id grid
                                                        :rows [{:type "flex" :value 1} {:type "flex" :value 1}]
                                                        :columns [{:type "flex" :value 1} {:type "flex" :value 1}]})
                        cell (get-in (data s "create_rect" {:file_id fid :parent_id grid :x 0 :y 0 :width 20 :height 20}) ["shape" "id"])]
                    (is (= [2 2] ((juxt #(get % "row") #(get % "column")) (st "set_grid_cell" {:shape_id cell :row 2 :column 2})))))))
              (testing "text ranges and library styles"
                (let [txt   (get-in (data s "create_text" {:file_id fid :x 0 :y 4000 :text "Hello world"}) ["shape" "id"])
                      rect  (get-in (data s "create_rect" {:file_id fid :x 300 :y 4000 :width 60 :height 60}) ["shape" "id"])
                      _     (data s "set_strokes" {:file_id fid :shape_id rect :strokes [{:color "#000000" :width 3}]})
                      ranged (get (data s "set_text_range_style" {:file_id fid :shape_id txt :start 0 :end 5 :font_weight "700"
                                                                  :fills [{:color "#FF0000"}]}) "range")
                      color (get-in (data s "create_library_color" {:file_id fid :name "Brand Red" :path "Brand" :color "#FF0000"}) ["color" "id"])
                      typo  (data s "create_library_typography" {:file_id fid :name "Heading" :font_family "Work Sans" :font_size 24 :font_weight "700"})]
                  (is (= ["Hello" "700" "#FF0000"] [(get ranged "characters") (get ranged "fontWeight")
                                                    (str/upper-case (get-in ranged ["fills" 0 "fillColor"]))]))
                  (is (= color (get-in (data s "apply_library_color" {:file_id fid :shape_id rect :color_id color}) ["shape" "changed" "fills" 0 "fillColorRefId"])))
                  (let [stroke (get-in (data s "apply_library_color" {:file_id fid :shape_id rect :color_id color :target "stroke"}) ["shape" "changed" "strokes" 0])]
                    (is (= [color 3] [(get stroke "strokeColorRefId") (get stroke "strokeWidth")])))
                  (is (= ["Work Sans" "700" "24"] ((juxt #(get % "fontFamily") #(get % "fontWeight") #(get % "fontSize")) (get typo "typography"))))
                  (is (= ["world" "Work Sans"] ((juxt #(get % "characters") #(get % "fontFamily"))
                                                (get (data s "apply_typography" {:file_id fid :shape_id txt :typography_id (get-in typo ["typography" "id"])
                                                                                 :start 6 :end 11}) "range"))))
                  (is (= ["Brand Red"] (mapv #(get % "name") (get (data s "get_colors" {:file_id fid}) "colors"))))
                  (is (some? (get-in (data s "set_image_fill" {:file_id fid :shape_id rect :url (get it/env "PENPOT_IT_MEDIA_URL" "https://raw.githubusercontent.com/penpot/penpot/2.18.1/frontend/resources/images/favicon.png")})
                                     ["shape" "changed" "fills" 0 "fillImage"])))))
              (testing "token catalog"
                (let [timed  (fn [f] (let [t0 (System/nanoTime) r (f)] [r (quot (- (System/nanoTime) t0) 1000000)]))
                      light  (get-in (data s "create_token_set" {:file_id fid :name "mode/light"}) ["set" "id"])
                      dark   (get-in (data s "create_token_set" {:file_id fid :name "mode/dark" :active false}) ["set" "id"])
                      token  (data s "create_token" {:file_id fid :set_id light :type "color" :name "bg" :value "#FFFFFF"})
                      _      (data s "create_token" {:file_id fid :set_id dark :type "color" :name "bg" :value "#000000"})
                      _      (data s "create_token" {:file_id fid :set_id light :type "spacing" :name "space.1" :value "4"})
                      space  (data s "create_token" {:file_id fid :set_id light :type "spacing" :name "space.2" :value "{space.1} * 2"})
                      theme  (get (data s "create_token_theme" {:file_id fid :group "mode" :name "dark" :set_ids [dark]}) "theme")
                      tokens #(data s "get_design_tokens" {:file_id fid})]
                  (is (= "#FFFFFF" (str/upper-case (get-in token ["token" "resolvedValue"]))))
                  (is (= ["{space.1} * 2" "8"] [(get-in space ["token" "value"]) (get-in space ["token" "resolvedValue"])]))
                  (is (= ["mode" "dark" ["mode/dark"]] [(get theme "group") (get theme "name") (mapv #(get % "name") (get theme "sets"))]))
                  (is (true? (get-in (data s "set_token_theme_active" {:file_id fid :theme_id (get theme "id") :active true}) ["theme" "active"])))
                  (let [[_ ms] (timed #(data s "set_token_theme_active" {:file_id fid :theme_id (get theme "id") :active true}))
                        [t read-ms] (timed tokens)]
                    (is (< (+ ms read-ms) 10000) "repeating an unchanged state does not block reads")
                    (is (= [true] (mapv #(get % "active") (filter #(= "mode/dark" (get % "name")) (get t "sets"))))))
                  (is (= "#111111" (str/upper-case (get-in (data s "update_token" {:file_id fid :token_id (get-in token ["token" "id"]) :value "#111111"})
                                                           ["token" "value"]))))
                  (is (= ["mode/light"] (mapv #(get % "name") (get-in (data s "set_theme_sets" {:file_id fid :theme_id (get theme "id") :set_ids [light]}) ["theme" "sets"]))))
                  (is (nil? (get-in (data s "set_theme_sets" {:file_id fid :theme_id (get theme "id") :set_ids []}) ["theme" "sets"])))
                  (data s "delete_token" {:file_id fid :token_id (get-in token ["token" "id"])})
                  (data s "delete_token_theme" {:file_id fid :theme_id (get theme "id")})
                  (data s "delete_token_set" {:file_id fid :set_id dark})
                  (let [t (tokens)]
                    (is (empty? (get t "themes")))
                    (is (not-any? #(= "mode/dark" (get % "name")) (get t "sets")))
                    (is (not-any? #(= "bg" (get % "name")) (mapcat #(get % "tokens") (filter #(= "mode/light" (get % "name")) (get t "sets"))))))))
              (testing "structure"
                (let [rect  (fn [x y] (get-in (data s "create_rect" {:file_id fid :x x :y y :width 40 :height 40}) ["shape" "id"]))
                      xs    #(mapv (fn [sh] (get sh "x")) (get % "shapes"))
                      [a b] [(rect 0 5000) (rect 20 5020)]
                      bool  (get (data s "create_boolean" {:file_id fid :shape_ids [a b] :operation "union"}) "shape")
                      [c d] [(rect 200 5000) (rect 240 5000)]
                      grp   (get-in (data s "create_group" {:file_id fid :shape_ids [c d]}) ["shape" "id"])
                      icon  (get (data s "import_svg" {:file_id fid :x 400 :y 5000 :name "Icon"
                                                       :svg "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\"><circle cx=\"12\" cy=\"12\" r=\"10\"/></svg>"})
                                 "shape")
                      row   (mapv #(rect % 5200) [0 30 200])]
                  (is (= "boolean" (get bool "type")))
                  (is (true? (get-in (data s "set_mask" {:file_id fid :group_id grp :mask true}) ["shape" "isMask"])))
                  (is (true? (get-in (data s "set_mask" {:file_id fid :group_id grp :mask true}) ["shape" "isMask"])))
                  (is (false? (get-in (data s "set_mask" {:file_id fid :group_id grp :mask false}) ["shape" "isMask"])))
                  (is (= #{c d} (set (get (data s "ungroup" {:file_id fid :group_id grp}) "shapeIds"))))
                  (is (= ["Icon" 400 5000] [(get icon "name") (get icon "x") (get icon "y")]))
                  (is (= ["path"] (mapv #(get % "type") (get (data s "flatten" {:file_id fid :shape_ids [(get bool "id")]}) "shapes"))))
                  (is (= [0 0 0] (xs (data s "align_shapes" {:file_id fid :shape_ids row :horizontal "left"}))))
                  (data s "set_position" {:file_id fid :shape_id (nth row 1) :x 30 :y 5200})
                  (data s "set_position" {:file_id fid :shape_id (nth row 2) :x 200 :y 5200})
                  (is (= [0 100 200] (xs (data s "distribute_shapes" {:file_id fid :shape_ids row :axis "horizontal"}))))
                  (let [t0 (System/nanoTime)]
                    (data s "distribute_shapes" {:file_id fid :shape_ids row :axis "horizontal"})
                    (shape-of s fid (first row))
                    (is (< (quot (- (System/nanoTime) t0) 1000000) 10000) "an unchanged distribution does not block reads"))))
              (testing "export"
                (let [png (tool s "export_shape" {:file_id fid :shape_id board})
                      bytes (.decode (Base64/getDecoder) ^String (get-in png [:content 0 :data]))]
                  (is (= "image" (get-in png [:content 0 :type])))
                  (is (= [-119 80 78 71] (vec (take 4 bytes)))))
                (is (str/includes? (get (data s "export_shape" {:file_id fid :shape_id board :format "svg"}) "svg") "<svg")))
              (testing "layout removal"
                (is (nil? (get (data s "remove_layout" {:file_id fid :board_id board}) "layout"))))
              (testing "reads through the open editor match the saved file"
                (data s "list_media" {:file_id fid})
                (let [saved     {:rpc client :config {:full-file-shapes-max 100000} :version-error (constantly nil)}
                      from-file (fn [tool-name]
                                  (let [t (first (filter #(= tool-name (:name %)) tools/all))]
                                    (json/read-str (get-in (tool/invoke t saved {"file_id" fid}) [:content 0 :text]))))]
                  (doseq [tool-name ["list_components" "get_colors" "get_design_tokens"]]
                    (is (= (from-file tool-name) (data s tool-name {:file_id fid})) tool-name))
                  (let [without-line-height (fn [r] (update r "typographies" #(mapv (fn [t] (dissoc t "line_height")) %)))]
                    (is (= (without-line-height (from-file "get_typographies"))
                           (data s "get_typographies" {:file_id fid}))))
                  (let [editor (data s "get_file" {:file_id fid})
                        whole  (from-file "get_file")]
                    (is (= (get whole "pages") (get editor "pages")))
                    (is (= (dissoc (get whole "counts") "media") (get editor "counts"))))))
              (testing "file must be open in the editor"
                (let [other  (str (:id (rpc/call client :create-file {:project-id (:id project) :name "closed"})))
                      result (mcp/call-tool s "set_opacity" {:file_id other :shape_id board :opacity 0.5})]
                  (is (true? (:isError result)))
                  (is (str/starts-with? (get-in result [:content 0 :text]) (str "Open file " other))))))
            (finally
              (close-editor proc))))))))
