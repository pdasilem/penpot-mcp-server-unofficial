(ns ^:integration penpot.mcp.tools.design-system-it-test
  (:require
   [app.common.files.changes-builder :as pcb]
   [app.common.types.tokens-lib :as ctob]
   [app.common.uuid :as uuid]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.changes :as changes]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.test-client :as mcp]
   [penpot.mcp.tools.plugin-it-test :as p])
  (:import
   (java.io ByteArrayInputStream)
   (java.net URI)
   (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers)
   (java.util.zip ZipInputStream)))

(def ^:private radius-id (uuid/next))
(def ^:private overlay-id (uuid/next))

(defn- token [set-id id name type value]
  (fn [lib] (ctob/add-token lib set-id (ctob/make-token :id id :name name :type type :value value))))

(defn- tokens-lib [base]
  (let [core (uuid/next) light (uuid/next) dark (uuid/next) light-theme (uuid/next) dark-theme (uuid/next)]
    (reduce (fn [lib f] (f lib))
            (-> (or base (ctob/make-tokens-lib))
                (ctob/add-set (ctob/make-token-set :id core :name "core"))
                (ctob/add-set (ctob/make-token-set :id light :name "mode/light"))
                (ctob/add-set (ctob/make-token-set :id dark :name "mode/dark")))
            [(token core (uuid/next) "space.base" :spacing "4")
             (token core (uuid/next) "space.lg" :spacing "{space.base} * 4")
             (token core radius-id "radius.card" :border-radius "{space.base} * 2")
             (token core (uuid/next) "font.size.body" :font-size "16")
             (token core (uuid/next) "opacity.muted" :opacity "50%")
             (token core (uuid/next) "type.body" :typography {:font-family ["Inter"] :font-size "{font.size.body}" :line-height "150%"})
             (token core (uuid/next) "shadow.lift" :shadow [{:offset-x "0" :offset-y "2" :blur "4" :spread "0" :color "rgba(#000000, 0.25)" :inset false}])
             (token light (uuid/next) "color.bg" :color "#FFFFFF")
             (token light overlay-id "color.overlay" :color "rgba({color.bg}, 0.5)")
             (token dark (uuid/next) "color.bg" :color "#111111")
             (token dark (uuid/next) "color.overlay" :color "rgba({color.bg}, 0.5)")
             #(ctob/add-theme % (ctob/make-token-theme :id light-theme :group "mode" :name "light" :sets #{"core" "mode/light"}))
             #(ctob/add-theme % (ctob/make-token-theme :id dark-theme :group "mode" :name "dark" :sets #{"core" "mode/dark"}))
             #(ctob/activate-theme % light-theme)])))

(defn- add-design-system [client file-id]
  (changes/commit! client file-id
                   (fn []
                     (let [f (file/fetch client file-id)]
                       (-> (pcb/empty-changes)
                           (pcb/with-library-data (:data f))
                           (pcb/set-tokens-lib (tokens-lib (get-in f [:data :tokens-lib]))))))))

(defn- data [session tool-name args]
  (let [result (mcp/call-tool session tool-name args)]
    (when (:isError result)
      (throw (ex-info (str tool-name " failed: " (get-in result [:content 0 :text])) {})))
    (json/read-str (get-in result [:content 0 :text]))))

(defn- download [id]
  (let [resp (.send (HttpClient/newHttpClient)
                    (.build (.GET (HttpRequest/newBuilder (URI/create (str p/mcp-url "?export=" id)))))
                    (HttpResponse$BodyHandlers/ofByteArray))]
    {:status (.statusCode resp) :body (.body resp)}))

(defn- unzip [^bytes data]
  (with-open [z (ZipInputStream. (ByteArrayInputStream. data))]
    (loop [acc {}]
      (if-let [e (.getNextEntry z)]
        (recur (assoc acc (.getName e) (String. (.readAllBytes z) "UTF-8")))
        acc))))

(defn- export! [session fid platform options]
  (let [result (data session "export_design_system" {:file_id fid :platform platform :options options})
        id     (get result "export_id")
        first  (download id)
        again  (download id)]
    (is (= 200 (:status first)) platform)
    (is (= 404 (:status again)) platform)
    {:result result :files (unzip (:body first))}))

(def ^:private platforms
  [["css" {:prefix "it" :color_scheme_group "mode"}] ["scss" {}] ["tailwind" {}] ["tailwind" {:version 3}]
   ["typescript" {}] ["dtcg" {}] ["kotlin" {:package "com.acme.it"}] ["swiftui" {}]])

(deftest design-system-export-against-live-editor
  (let [client  (it/client)
        team-id (:default-team-id (rpc/call client :get-profile {}))]
    (it/with-temp-project client
      (fn [project]
        (let [file (rpc/call client :create-file {:project-id (:id project) :name "it-design-system"})
              fid  (str (:id file))
              _    (add-design-system client (:id file))
              proc (@#'p/open-editor team-id (:id file))]
          (try
            (let [s (mcp/connect (str p/mcp-url "?userToken=" (get it/env "PENPOT_MCP_KEY")))]
              (data s "set_toolset" {:name "export" :enabled true})
              (data s "create_library_color" {:file_id fid :name "Основной" :path "Brand" :color "#3366FF"})
              (data s "create_library_typography" {:file_id fid :name "Body" :path "Text" :font_family "Work Sans" :font_size 16 :font_weight "700"})
              (testing "every platform exports and downloads once through Penpot's nginx"
                (doseq [[platform options] platforms]
                  (let [{:keys [result files]} (export! s fid platform options)]
                    (is (= ["mode=light" "mode=dark"] (get result "combinations")) platform)
                    (is (contains? files "problems.json") platform)
                    (is (< 1 (count files)) platform))))
              (testing "computed values match what Penpot applies to shapes"
                (let [css   (get (:files (export! s fid "css" {:prefix "it"})) "tokens.css")
                      rect  (get-in (data s "create_rect" {:file_id fid :x 0 :y 0 :width 100 :height 100 :name "Probe"}) ["shape" "id"])
                      _     (data s "set_token" {:file_id fid :shape_id rect :token_id (str radius-id)})
                      _     (data s "set_token" {:file_id fid :shape_id rect :token_id (str overlay-id)})
                      shape (str/join "\n" (vals (data s "get_shape_css" {:file_id fid :shape_id rect})))]
                  (is (str/includes? css "--it-radius-card: 8px;"))
                  (is (str/includes? css "--it-color-overlay: rgba(255, 255, 255, 0.5);"))
                  (is (str/includes? css "--it-library-color-brand-основной: #3366ff;"))
                  (is (re-find #"border-radius:\s*8px" shape) shape)
                  (is (re-find #"(?i)rgba\(255,\s*255,\s*255,\s*0\.5\)|#FFFFFF80|#ffffff80" shape) shape))))
            (finally (@#'p/close-editor proc))))))))
