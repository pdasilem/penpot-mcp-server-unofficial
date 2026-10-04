(ns penpot.mcp.penpot.revision-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.tool :as tool]))

(def file-id (parse-uuid "11111111-0000-0000-0000-000000000001"))

(defn- ctx [execute]
  (let [scripts (atom [])]
    {:scripts scripts
     :execute (fn [code] (swap! scripts conj code) (execute code))
     :persistence {:dirty (atom #{})}}))

(deftest clean-file-is-read-without-plugin-call
  (let [c (ctx (constantly {:result true :changed false}))]
    (revision/await-clean! c file-id)
    (is (empty? @(:scripts c)))))

(deftest dirty-file-waits-for-editor-save
  (let [c (ctx (constantly {:result true :changed false}))]
    (revision/mark-dirty! c file-id)
    (revision/await-clean! c file-id)
    (is (= 1 (count @(:scripts c))))
    (is (str/includes? (first @(:scripts c)) "storage.lastSaveAt > storage.dirtySince + 3000"))
    (is (empty? @(get-in c [:persistence :dirty])))))

(deftest unsaved-changes-are-an-error
  (let [c (ctx (fn [_] (throw (tool/user-error "Penpot editor reported an error: MCP_ERR:not-saved:x"))))]
    (revision/mark-dirty! c file-id)
    (is (= "Penpot has not saved the latest editor changes of file 11111111-0000-0000-0000-000000000001 yet; try again"
           (try (revision/await-clean! c file-id) nil (catch clojure.lang.ExceptionInfo e (ex-message e)))))
    (is (contains? @(get-in c [:persistence :dirty]) file-id))))

(deftest file-closed-in-editor-counts-as-saved
  (let [c (ctx (fn [_] (throw (tool/user-error "Penpot editor reported an error: MCP_ERR:not-open:other"))))]
    (revision/mark-dirty! c file-id)
    (revision/await-clean! c file-id)
    (is (empty? @(get-in c [:persistence :dirty])))))

(deftest disconnected-editor-counts-as-saved
  (let [c (ctx (fn [_] (throw (ex-info "Penpot editor is not connected" {:type :tool/user-error :plugin/code "not-connected"}))))]
    (revision/mark-dirty! c file-id)
    (revision/await-clean! c file-id)
    (is (empty? @(get-in c [:persistence :dirty])))))

(deftest without-persistence-settings-nothing-happens
  (is (nil? (revision/mark-dirty! {} file-id)))
  (is (nil? (revision/await-clean! {} file-id))))

(deftest concurrent-mutation-is-not-lost-by-a-finishing-read
  (let [lock  (java.util.concurrent.locks.ReentrantLock.)
        dirty (atom #{file-id})
        c     {:plugin-lock {:lock lock :wait-ms 5000}
               :persistence {:dirty dirty}
               :execute (fn [code]
                          (if (str/includes? code "storage.dirtySince === undefined")
                            (do (Thread/sleep 200) {:result true :changed false})
                            {:result :ok :changed true}))}
        reader (future (revision/await-clean! c file-id))]
    (Thread/sleep 50)
    (revision/mutate! c file-id "return 1;" {})
    @reader
    (is (contains? @dirty file-id))))

(deftest timed-out-mutation-marks-file-dirty
  (let [c (assoc (ctx (fn [_] (throw (ex-info "Penpot editor did not answer in time"
                                              {:type :tool/user-error :plugin/code "timeout"}))))
                 :persistence {:dirty (atom #{})})]
    (is (thrown? clojure.lang.ExceptionInfo (revision/mutate! c file-id "return 1;" {})))
    (is (contains? @(get-in c [:persistence :dirty]) file-id))))

(deftest failure-after-a-change-marks-the-file-dirty
  (let [c (ctx (fn [_] (throw (ex-info "Penpot editor reported an error: MCP_CHANGED MCP_ERR:not-updated:x" {}))))]
    (is (thrown? clojure.lang.ExceptionInfo (revision/mutate! c file-id "x" {})))
    (is (= #{file-id} @(get-in c [:persistence :dirty])))))

(deftest failure-before-any-change-keeps-the-file-clean
  (let [c (ctx (fn [_] (throw (ex-info "Penpot editor reported an error: MCP_ERR:shape-not-found:x" {}))))]
    (is (thrown? clojure.lang.ExceptionInfo (revision/mutate! c file-id "x" {})))
    (is (empty? @(get-in c [:persistence :dirty])))))
