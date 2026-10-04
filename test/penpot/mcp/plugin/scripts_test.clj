(ns penpot.mcp.plugin.scripts-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.plugin.scripts :as scripts]
   [penpot.mcp.tool :as tool]))

(def file-id (parse-uuid "11111111-0000-0000-0000-000000000001"))

(deftest script-embeds-arguments-as-json-literal
  (let [code (scripts/script "return args.name;" {:file-id file-id :name "a\"b</script> c"})]
    (is (str/starts-with? code "const args = {"))
    (is (str/includes? code "\"fileId\":\"11111111-0000-0000-0000-000000000001\""))
    (is (str/includes? code "a\\\"b<\\/script>\\u2028c"))
    (is (str/includes? code "return args.name;"))))

(deftest script-converts-argument-keys-to-camel-case
  (is (str/includes? (scripts/script "" {:file-id file-id :border-radius-top-left 4 :shape-ids [file-id]})
                     "\"borderRadiusTopLeft\":4")))

(deftest script-always-checks-open-file
  (is (str/includes? (scripts/script "return 1;" {:file-id file-id}) "ensureFile();")))

(defn- run-with [result]
  (scripts/run! {:execute (fn [_] (if (instance? Throwable result) (throw result) {:result result :changed false}))}
                "return 1;" {:file-id file-id}))

(defn- message [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e
                 (when (= :tool/user-error (:type (ex-data e))) (ex-message e)))))

(deftest maps-file-not-open-error
  (is (= "Open file 11111111-0000-0000-0000-000000000001 in the Penpot editor; the editor has file 22222222-0000-0000-0000-000000000002 open"
         (message #(run-with (tool/user-error "Penpot editor reported an error: Error handling task: MCP_ERR:not-open:22222222-0000-0000-0000-000000000002"))))))

(deftest maps-file-not-open-without-current-file
  (is (= "Open file 11111111-0000-0000-0000-000000000001 in the Penpot editor; no file is open"
         (message #(run-with (tool/user-error "Penpot editor reported an error: MCP_ERR:not-open:"))))))

(deftest maps-missing-shape-error
  (is (= "Shape 33333333-0000-0000-0000-000000000003 not found in the open file"
         (message #(run-with (tool/user-error "Penpot editor reported an error: MCP_ERR:shape-not-found:33333333-0000-0000-0000-000000000003"))))))

(deftest keeps-other-editor-errors
  (is (= "Penpot editor reported an error: Value not valid: -1"
         (message #(run-with (tool/user-error "Penpot editor reported an error: Value not valid: -1"))))))

(deftest returns-plugin-result
  (is (= {:ok true} (run-with {:ok true}))))

(deftest decodes-base64-envelope
  (is (= {:base64 "AAEC"} (scripts/bytes-envelope {:__type "base64" :data "AAEC"})))
  (is (nil? (scripts/bytes-envelope {:x 1}))))

(deftest maps-all-known-error-codes
  (doseq [[code expected] [["not-a-board" "Shape x is not a board"]
                           ["not-a-container" "Shape x cannot contain other shapes"]
                           ["not-text" "Shape x is not a text"]
                           ["token-not-applied" "Penpot did not apply the token to x; check that the attribute fits the token type"]
                           ["create-failed" "Penpot could not create the x"]
                           ["mixed-pages" "All shapes must be on the same page; x is on another page"]]]
    (is (= expected (message #(run-with (tool/user-error (str "Penpot editor reported an error: MCP_ERR:" code ":x"))))) code)))

(deftest serializes-plugin-executions
  (let [active  (atom 0)
        maximum (atom 0)
        ctx     {:plugin-lock {:lock (java.util.concurrent.locks.ReentrantLock.) :wait-ms 5000}
                 :execute (fn [_]
                            (swap! maximum max (swap! active inc))
                            (Thread/sleep 50)
                            (swap! active dec)
                            {:result :ok})}
        runs    (doall (repeatedly 4 #(future (scripts/run! ctx "return 1;" {:file-id file-id}))))]
    (is (every? #(= :ok @%) runs))
    (is (= 1 @maximum))))

(deftest script-wraps-body-and-reports-changes
  (let [code (scripts/script "return 1;" {:file-id file-id})]
    (is (str/includes? code "storage.saveTracking = penpot.on('contentsave'"))
    (is (str/ends-with? code "return { result, changed };"))
    (is (str/includes? code "if (changed) throw new Error('MCP_CHANGED ' + (e && e.message ? e.message : String(e)));"))))

(deftest changed-marker-is-kept-in-ex-data-and-removed-from-the-message
  (let [ctx {:execute (fn [_] (throw (ex-info "Penpot editor reported an error: MCP_CHANGED MCP_ERR:not-updated:x" {})))}
        e   (try (scripts/execute! ctx "x" {:file-id file-id}) (catch clojure.lang.ExceptionInfo e e))]
    (is (true? (:plugin/changed (ex-data e))))
    (is (= "Penpot did not apply the change to x" (ex-message e)))))

(deftest busy-editor-fails-fast
  (let [lock (java.util.concurrent.locks.ReentrantLock.)
        ctx  {:plugin-lock {:lock lock :wait-ms 50}}
        held (promise)
        done (promise)
        t    (future (scripts/serialized ctx (fn [] (deliver held true) @done)))]
    @held
    (is (= "Penpot editor is busy with other requests; try again"
           (message #(scripts/serialized ctx (constantly :x)))))
    (deliver done true)
    @t))

(deftest shapes-are-looked-up-on-the-open-page-first-and-by-id-elsewhere
  (let [code (scripts/script "return 1;" {:file-id "f"})]
    (is (str/includes? code "const here = penpot.currentPage.getShapeById(id);"))
    (is (str/includes? code "const s = page.getShapeById(id);"))
    (is (not (str/includes? code "penpotUtils.findShapeById")))
    (is (not (str/includes? code "penpotUtils.getPageForShape")))))

(deftest a-change-is-saved-only-by-a-save-after-the-editor-debounce
  (let [code (scripts/script "return 1;" {:file-id "f"})]
    (is (str/includes? code "storage.lastSaveAt = Date.now();"))
    (is (str/includes? code "const startedAt = Date.now();"))
    (is (str/includes? code "storage.dirtySince = Math.max(storage.dirtySince ?? 0, startedAt);"))))
