(ns ^:integration penpot.mcp.tools.token-usage-it-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.test-client :as mcp]
   [penpot.mcp.tools.plugin-it-test :as p]))

(defn- data [session tool-name args]
  (let [result (mcp/call-tool session tool-name args)]
    (when (:isError result)
      (throw (ex-info (str tool-name " failed: " (get-in result [:content 0 :text])) {})))
    (json/read-str (get-in result [:content 0 :text]))))

(defn- report [result]
  (or (get result "full_result") result))

(deftest token-usage-of-the-real-file-matches-in-the-editor-and-the-saved-file
  (let [client  (it/client)
        team-id (:default-team-id (rpc/call client :get-profile {}))]
    (it/with-test-data-copy client
      (fn [_ file]
        (let [fid   (str (:id file))
              s     (mcp/connect (str p/mcp-url "?userToken=" (get it/env "PENPOT_MCP_KEY")))
              proc  (@#'p/open-editor team-id (:id file))
              live  (try
                      (data s "token_usage" {:file_id fid})
                      (finally (@#'p/close-editor proc)))
              saved (data s "token_usage" {:file_id fid})]
          (testing "through the open editor"
            (is (true? (get-in (report live) ["summary" "values_compared"])))
            (is (pos? (get-in (report live) ["summary" "tokens"] 0))))
          (testing "from the saved file without the editor"
            (is (= (get (report live) "summary") (get (report saved) "summary")))))))))
