(ns penpot.mcp.fixtures
  (:require
   [app.common.types.path :as path]
   [app.common.types.shape :as cts]
   [app.common.types.tokens-lib :as ctob]
   [app.common.uuid :as uuid]
   [clojure.data.json :as json]
   [penpot.mcp.tool :as tool]))

(def file-id (parse-uuid "11111111-0000-0000-0000-000000000001"))
(def page-id (parse-uuid "11111111-0000-0000-0000-000000000002"))
(def page2-id (parse-uuid "11111111-0000-0000-0000-000000000003"))
(def board-id (parse-uuid "22222222-0000-0000-0000-000000000001"))
(def rect-id (parse-uuid "22222222-0000-0000-0000-000000000002"))
(def text-id (parse-uuid "22222222-0000-0000-0000-000000000003"))
(def ellipse-id (parse-uuid "22222222-0000-0000-0000-000000000004"))
(def path-id (parse-uuid "22222222-0000-0000-0000-000000000005"))
(def instance-id (parse-uuid "22222222-0000-0000-0000-000000000006"))
(def component-id (parse-uuid "33333333-0000-0000-0000-000000000001"))
(def color-id (parse-uuid "44444444-0000-0000-0000-000000000001"))
(def typography-id (parse-uuid "55555555-0000-0000-0000-000000000001"))

(def root
  (cts/setup-shape {:id uuid/zero :type :frame :name "Root Frame" :x 0 :y 0 :width 0.01 :height 0.01
                    :frame-id uuid/zero :parent-id uuid/zero :shapes [board-id ellipse-id path-id instance-id]}))

(def board
  (cts/setup-shape {:id board-id :type :frame :name "Login Card" :x 0 :y 0 :width 400 :height 300
                    :frame-id uuid/zero :parent-id uuid/zero :shapes [rect-id text-id]
                    :layout :flex :layout-flex-dir :column :layout-gap-type :multiple
                    :layout-gap {:row-gap 12 :column-gap 0} :layout-align-items :center
                    :layout-justify-content :start :layout-wrap-type :nowrap
                    :layout-padding-type :multiple :layout-padding {:p1 24 :p2 16 :p3 24 :p4 16}
                    :fills [{:fill-color "#FFFFFF" :fill-opacity 1}]}))

(def rect
  (cts/setup-shape {:id rect-id :type :rect :name "Submit Button" :x 16 :y 24 :width 120 :height 40
                    :frame-id board-id :parent-id board-id :r1 8 :r2 8 :r3 8 :r4 8
                    :fills [{:fill-color "#3366FF" :fill-opacity 0.5}]
                    :strokes [{:stroke-color "#000000" :stroke-opacity 1 :stroke-width 2
                               :stroke-style :solid :stroke-alignment :inner}]
                    :shadow [{:id (uuid/next) :style :drop-shadow :offset-x 0 :offset-y 4 :blur 8 :spread 0
                              :hidden false :color {:color "#000000" :opacity 0.25}}]
                    :opacity 0.9}))

(def text
  (cts/setup-shape {:id text-id :type :text :name "Title" :x 16 :y 80 :width 200 :height 30
                    :frame-id board-id :parent-id board-id :grow-type :auto-width
                    :content {:type "root"
                              :children [{:type "paragraph-set"
                                          :children [{:type "paragraph" :text-align "center"
                                                      :children [{:text "Sign in" :font-family "Inter" :font-size "24"
                                                                  :font-weight "700" :font-style "normal"
                                                                  :line-height "1.2" :letter-spacing "0"
                                                                  :text-transform "uppercase" :text-decoration "none"
                                                                  :fills [{:fill-color "#111111" :fill-opacity 1}]}]}]}]}}))

(def ellipse
  (cts/setup-shape {:id ellipse-id :type :circle :name "Avatar" :x 500 :y 10 :width 50 :height 50
                    :frame-id uuid/zero :parent-id uuid/zero
                    :fills [{:fill-color-gradient {:type :linear :start-x 0 :start-y 0 :end-x 1 :end-y 1 :width 1
                                                   :stops [{:color "#FF0000" :opacity 1 :offset 0}
                                                           {:color "#0000FF" :opacity 1 :offset 1}]}}]}))

(def path-shape
  (let [content (path/from-plain [{:command :move-to :params {:x 600 :y 0}}
                                  {:command :line-to :params {:x 650 :y 50}}])]
    (cts/setup-shape {:id path-id :type :path :name "Divider" :x 600 :y 0 :width 50 :height 50
                      :frame-id uuid/zero :parent-id uuid/zero :content content
                      :strokes [{:stroke-color "#999999" :stroke-opacity 1 :stroke-width 1}]})))

(def instance
  (cts/setup-shape {:id instance-id :type :rect :name "Button Instance" :x 700 :y 0 :width 120 :height 40
                    :frame-id uuid/zero :parent-id uuid/zero
                    :component-id component-id :component-file file-id :component-root true}))

(def token-set-id (parse-uuid "88888888-0000-0000-0000-000000000001"))
(def token-id (parse-uuid "88888888-0000-0000-0000-000000000002"))

(def tokens-lib
  (-> (ctob/make-tokens-lib)
      (ctob/add-set (ctob/make-token-set :id token-set-id :name "brand"))
      (ctob/add-token token-set-id (ctob/make-token :id token-id :name "color.primary" :type :color :value "#3366FF"))))

(def page
  {:id page-id :name "Screens"
   :objects {uuid/zero root board-id board rect-id rect text-id text ellipse-id ellipse path-id path-shape instance-id instance}})

(def page2
  {:id page2-id :name "Archive"
   :objects {uuid/zero (assoc root :shapes [])}})

(def file
  {:id file-id :name "Login" :project-id (parse-uuid "66666666-0000-0000-0000-000000000001")
   :team-id (parse-uuid "77777777-0000-0000-0000-000000000001")
   :revn 12 :vern 0 :is-shared false :features #{"components/v2"}
   :data {:pages [page-id page2-id]
          :pages-index {page-id page page2-id page2}
          :components {component-id {:id component-id :name "Button" :path "Forms"
                                     :main-instance-id rect-id :main-instance-page page-id}}
          :colors {color-id {:id color-id :name "Primary" :path "Brand" :color "#3366FF" :opacity 1}}
          :tokens-lib tokens-lib
          :media {(parse-uuid "99999999-0000-0000-0000-000000000001")
                  {:id (parse-uuid "99999999-0000-0000-0000-000000000001") :name "logo.png" :width 64 :height 64 :mtype "image/png"}}
          :typographies {typography-id {:id typography-id :name "Heading" :path "" :font-family "Inter"
                                        :font-size "24" :font-weight "700" :font-style "normal"
                                        :line-height "1.2" :letter-spacing "0" :text-transform "none"}}}})

(defn ctx
  ([responses] (ctx responses nil))
  ([responses bridge]
   (let [calls (atom [])]
     {:calls calls
      :rpc {:session-id (uuid/next)
            :send (fn [cmd params]
                    (swap! calls conj [cmd params])
                    (let [r (get responses cmd)]
                      (cond
                        (fn? r) (r params)
                        (contains? responses cmd) r
                        :else (throw (ex-info (str "unexpected rpc " cmd) {})))))}
      :bridge bridge
      :version-error (constantly nil)})))

(defn call [tool-def ctx args]
  (let [{:keys [content error?]} (tool/invoke tool-def ctx args)
        text (get-in content [0 :text])]
    (if error?
      {:error text}
      (json/read-str text))))

(defn find-tool [tools tool-name]
  (or (first (filter #(= tool-name (:name %)) tools))
      (throw (ex-info (str "no tool " tool-name) {}))))

(defn plugin-ctx
  ([result] (plugin-ctx result {}))
  ([result responses]
   (let [scripts (atom [])
         base    (ctx responses)]
     (assoc base
            :scripts scripts
            :execute (fn [code]
                       (swap! scripts conj code)
                       {:result (if (fn? result) (result code) result) :changed true})))))

(defn script-args [code]
  (json/read-str (second (re-find #"(?s)^const args = (.*?);\n" code))))

(defn last-script-args [ctx]
  (script-args (last @(:scripts ctx))))
