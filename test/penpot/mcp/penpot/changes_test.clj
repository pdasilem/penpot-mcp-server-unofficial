(ns penpot.mcp.penpot.changes-test
  (:require
   [app.common.files.changes-builder :as pcb]
   [app.common.uuid :as uuid]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.changes :as changes]))

(def file-id (uuid/next))
(def session-id (uuid/next))
(def team-id (uuid/next))
(def project-id (uuid/next))

(defn- revision-at [revn]
  [[{:id team-id}] [{:id project-id}] [{:id file-id :revn revn :vern 0}]])

(defn- fake-client [responses]
  (let [calls (atom [])
        queue (atom responses)]
    {:session-id session-id
     :calls calls
     :send (fn [cmd params]
             (swap! calls conj [cmd params])
             (let [[r & more] @queue]
               (reset! queue more)
               (if (instance? Exception r) (throw r) r)))}))

(defn- add-page []
  (pcb/add-empty-page (pcb/empty-changes) (uuid/next) "Page"))

(def conflict (ex-info "conflict" {:penpot/code :vern-conflict}))

(defn- commands [client]
  (mapv first @(:calls client)))

(deftest commits-validated-changes-with-file-revision
  (let [client (fake-client (conj (revision-at 7) [{:revn 8}]))
        result (changes/commit! client file-id add-page)
        [update-cmd update-params] (last @(:calls client))]
    (is (= [{:revn 8}] result))
    (is (= [:get-teams :get-projects :get-project-files :update-file] (commands client)))
    (is (= :update-file update-cmd))
    (is (= file-id (:id update-params)))
    (is (= 7 (:revn update-params)))
    (is (= 0 (:vern update-params)))
    (is (= session-id (:session-id update-params)))
    (is (= [:add-page] (mapv :type (:changes update-params))))))

(deftest never-downloads-the-file
  (let [client (fake-client (conj (revision-at 7) [{:revn 8}]))]
    (changes/commit! client file-id add-page)
    (is (not-any? #{:get-file} (commands client)))))

(deftest retries-once-after-revision-conflict
  (let [client (fake-client (concat (revision-at 7) [conflict] (revision-at 9) [[{:revn 10}]]))
        result (changes/commit! client file-id add-page)]
    (is (= [{:revn 10}] result))
    (is (= 9 (:revn (second (last @(:calls client))))))))

(deftest gives-up-after-second-conflict
  (let [client (fake-client (concat (revision-at 7) [conflict] (revision-at 9) [conflict]))
        ex (try (changes/commit! client file-id add-page) nil (catch clojure.lang.ExceptionInfo e e))]
    (is (= :tool/user-error (:type (ex-data ex))))
    (is (= "The Penpot file changed concurrently; try again" (ex-message ex)))))

(deftest refuses-invalid-changes-without-sending
  (let [client (fake-client (revision-at 7))
        ex (try (changes/commit! client file-id (fn [] {:redo-changes [{:type :not-a-change}]}))
                nil
                (catch clojure.lang.ExceptionInfo e e))]
    (is (some? ex))
    (is (not-any? #{:update-file} (commands client)))))
