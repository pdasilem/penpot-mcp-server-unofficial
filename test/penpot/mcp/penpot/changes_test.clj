(ns penpot.mcp.penpot.changes-test
  (:require
   [app.common.files.changes-builder :as pcb]
   [app.common.uuid :as uuid]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.changes :as changes]))

(def file-id (uuid/next))
(def session-id (uuid/next))

(defn- file-at [revn]
  {:id file-id :revn revn :vern 0 :data {:pages [] :pages-index {}}})

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

(defn- add-page [_file]
  (pcb/add-empty-page (pcb/empty-changes) (uuid/next) "Page"))

(def conflict (ex-info "conflict" {:penpot/code :vern-conflict}))

(deftest commits-validated-changes-with-file-revision
  (let [client (fake-client [(file-at 7) [{:revn 8}]])
        result (changes/commit! client file-id add-page)
        [[_ get-params] [update-cmd update-params]] @(:calls client)]
    (is (= [{:revn 8}] result))
    (is (= file-id (:id get-params)))
    (is (= :update-file update-cmd))
    (is (= 7 (:revn update-params)))
    (is (= 0 (:vern update-params)))
    (is (= session-id (:session-id update-params)))
    (is (= [:add-page] (mapv :type (:changes update-params))))))

(deftest retries-once-after-revision-conflict
  (let [client (fake-client [(file-at 7) conflict (file-at 9) [{:revn 10}]])
        result (changes/commit! client file-id add-page)]
    (is (= [{:revn 10}] result))
    (is (= 9 (:revn (second (last @(:calls client))))))))

(deftest gives-up-after-second-conflict
  (let [client (fake-client [(file-at 7) conflict (file-at 9) conflict])
        ex (try (changes/commit! client file-id add-page) nil (catch clojure.lang.ExceptionInfo e e))]
    (is (= :tool/user-error (:type (ex-data ex))))
    (is (= "The Penpot file changed concurrently; try again" (ex-message ex)))))

(deftest refuses-invalid-changes-without-sending
  (let [client (fake-client [(file-at 7)])
        ex (try (changes/commit! client file-id (fn [_] {:redo-changes [{:type :not-a-change}]}))
                nil
                (catch clojure.lang.ExceptionInfo e e))]
    (is (some? ex))
    (is (= 1 (count @(:calls client))))))
