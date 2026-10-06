(ns ^:integration penpot.mcp.tools.design-system-it-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.penpot.tokens-lib :as ctob]
   [penpot.mcp.test-client :as mcp]
   [penpot.mcp.tools.plugin-it-test :as p])
  (:import
   (java.io ByteArrayInputStream)
   (java.net URI)
   (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers)
   (java.util.zip ZipInputStream)))

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
  [["css" {:prefix "it" :color_scheme_group "Scheme"}] ["scss" {}] ["tailwind" {}] ["tailwind" {:version 3}]
   ["typescript" {}] ["dtcg" {}] ["kotlin" {:package "com.recorded.it"}] ["swiftui" {}]])

(defn- css-var [css token-name]
  (second (re-find (re-pattern (str "--it-" (java.util.regex.Pattern/quote (str/replace (str/lower-case token-name) #"[^a-z0-9]+" "-")) ": ([^;]+);")) css)))

(deftest design-system-export-against-live-editor
  (let [client  (it/client)
        team-id (:default-team-id (rpc/call client :get-profile {}))]
    (it/with-test-data-copy client
      (fn [_ file]
        (let [fid    (str (:id file))
              themes (count (remove ctob/hidden-theme? (ctob/get-themes (get-in (file/fetch client (:id file)) [:data :tokens-lib]))))
              radius (@#'p/real-token client (:id file) :border-radius)
              color  (@#'p/real-token client (:id file) :color)
              proc   (@#'p/open-editor team-id (:id file))]
          (try
            (let [s (mcp/connect (str p/mcp-url "?userToken=" (get it/env "PENPOT_MCP_KEY")))]
              (data s "set_toolset" {:name "export" :enabled true})
              (testing "every platform exports and downloads once through Penpot's nginx"
                (doseq [[platform options] platforms]
                  (let [{:keys [result files]} (export! s fid platform options)]
                    (is (= themes (count (get result "combinations"))) platform)
                    (is (contains? files "problems.json") platform)
                    (is (< 1 (count files)) platform))))
              (testing "computed values match what Penpot applies to shapes"
                (let [css   (get (:files (export! s fid "css" {:prefix "it"})) "tokens.css")
                      rect  (get-in (data s "create_rect" {:file_id fid :x 0 :y -2000 :width 100 :height 100 :name "Probe"}) ["shape" "id"])
                      _     (data s "set_token" {:file_id fid :shape_id rect :token_id (str (:id radius))})
                      _     (data s "set_token" {:file_id fid :shape_id rect :token_id (str (:id color))})
                      shape (str/lower-case (str/join "\n" (vals (data s "get_shape_css" {:file_id fid :shape_id rect}))))]
                  (is (some? (css-var css (:name radius))) (:name radius))
                  (is (str/includes? shape (str "border-radius: " (css-var css (:name radius)))) shape)
                  (is (str/includes? shape (str/lower-case (css-var css (:name color)))) shape)
                  (is (re-find #"(?m)^\s*--it-library-color-" css)))))
            (finally (@#'p/close-editor proc))))))))
