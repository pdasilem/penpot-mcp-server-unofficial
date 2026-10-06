(ns penpot.mcp.tools.large-result-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.exports :as exports]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools :as all]
   [penpot.mcp.tools.large-result :as large-result])
  (:import
   (java.io ByteArrayInputStream)
   (java.util.zip ZipInputStream)))

(def ^:private tools (into {} (map (juxt :name identity)) all/all))

(defn- unzip [^bytes data]
  (with-open [z (ZipInputStream. (ByteArrayInputStream. data))]
    (loop [acc {}]
      (if-let [e (.getNextEntry z)]
        (recur (assoc acc (.getName e) (String. (.readAllBytes z) "UTF-8")))
        acc))))

(defn- run [scenario]
  (let [r (replay/run (tools (:tool (replay/recording scenario))) scenario)]
    (is (empty? (:left r)) scenario)
    {:answer (get-in r [:result :content 0 :text]) :ctx (:ctx r)}))

(defn- archive [{:keys [answer ctx]}]
  (let [id (second (re-find #"export=([0-9a-f]{32})" (get-in (json/read-str answer) ["full_result" "download"])))]
    (unzip (exports/take! (:exports ctx) id))))

(deftest the-limit-keeps-every-answer-within-what-claude-code-accepts
  (is (= 30000 large-result/max-inline-chars)))

(deftest a-real-answer-under-the-limit-is-returned-as-it-is
  (let [{:keys [answer]} (run "library/tokens-type")]
    (is (< (count answer) large-result/max-inline-chars))
    (is (nil? (get (json/read-str answer) "full_result")))))

(deftest a-real-answer-over-the-limit-gives-the-brief-and-a-one-time-download
  (let [{:keys [answer] :as r} (run "token-usage/saved")
        result (json/read-str answer)]
    (is (< (count answer) large-result/max-inline-chars))
    (is (re-find #"^curl -o token-usage\.zip \"<MCP address>\?export=" (get-in result ["full_result" "download"])))
    (is (= 60 (get-in result ["full_result" "expires_in_minutes"])))
    (is (< large-result/max-inline-chars (get-in result ["full_result" "size_bytes"])))
    (is (some? (get result "summary")))
    (is (seq (json/read-str (get (archive r) "token-usage.json"))))))

(deftest a-real-token-catalog-over-the-limit-is-archived-whole
  (let [{:keys [answer] :as r} (run "library/tokens")
        files (archive r)]
    (is (some? (get (json/read-str answer) "full_result")))
    (is (= 1 (count files)))
    (is (seq (get (json/read-str (val (first files))) "sets")))))

(deftest a-full-store-is-a-user-error
  (let [{:keys [args]} (replay/recording "token-usage/saved")
        c (assoc (replay/context "token-usage/saved") :exports (exports/store {:now (constantly 0) :max-bytes 10}))
        r (tool/invoke (tools "token_usage") c args)]
    (is (re-find #"narrow" (get-in r [:content 0 :text])))))
