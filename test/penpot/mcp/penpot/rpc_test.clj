(ns penpot.mcp.penpot.rpc-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.penpot.transit :as transit]))

(defn- outcome [status body]
  (try
    {:value (rpc/response->result :get-file status (transit/encode body))}
    (catch clojure.lang.ExceptionInfo e
      {:message (ex-message e) :data (ex-data e)})))

(deftest returns-decoded-body-on-success
  (is (= {:value {:id 1 :name "File"}} (outcome 200 {:id 1 :name "File"}))))

(deftest returns-nil-for-empty-success-body
  (is (nil? (rpc/response->result :delete-file 204 ""))))

(deftest maps-not-found-to-user-error
  (let [{:keys [message data]} (outcome 404 {:type :not-found :code :object-not-found :hint "secret detail"})]
    (is (= :tool/user-error (:type data)))
    (is (= "Penpot get-file failed: object-not-found" message))
    (is (not (str/includes? message "secret detail")))))

(deftest maps-validation-to-user-error-with-code
  (let [{:keys [message data]} (outcome 400 {:type :validation :code :params-validation})]
    (is (= :tool/user-error (:type data)))
    (is (= :params-validation (:penpot/code data)))
    (is (= "Penpot get-file failed: params-validation" message))))

(deftest maps-authentication-failures-to-user-error
  (let [{:keys [message data]} (outcome 401 {:type :authentication})]
    (is (= :tool/user-error (:type data)))
    (is (= "Penpot get-file failed: access denied" message))))

(deftest marks-revision-conflicts
  (is (true? (rpc/conflict? (ex-info "x" {:penpot/code :vern-conflict}))))
  (is (true? (rpc/conflict? (ex-info "x" {:penpot/code :revn-conflict}))))
  (is (false? (rpc/conflict? (ex-info "x" {:penpot/code :object-not-found})))))

(deftest maps-server-errors-to-internal-errors
  (let [{:keys [data]} (outcome 500 {:type :internal :code :unhandled})]
    (is (not= :tool/user-error (:type data)))
    (is (= 500 (:status data)))))

(deftest treats-redirects-as-internal-errors
  (let [{:keys [data]} (outcome 302 {})]
    (is (some? data))
    (is (not= :tool/user-error (:type data)))
    (is (= 302 (:status data)))))

(deftest maps-gateway-errors-to-unreachable
  (doseq [status [502 503 504]]
    (let [{:keys [message data]} (outcome status {})]
      (is (= :tool/user-error (:type data)))
      (is (= "Penpot is unreachable (HTTP " (subs message 0 28))))))

(deftest maps-connection-failures-to-unreachable
  (let [c (rpc/client {:base-url "http://127.0.0.1:1" :token "x"})
        e (try (rpc/call c :get-profile {}) nil (catch clojure.lang.ExceptionInfo e e))]
    (is (= :tool/user-error (:type (ex-data e))))
    (is (= "Penpot is unreachable" (ex-message e)))))
