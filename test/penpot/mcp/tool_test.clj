(ns penpot.mcp.tool-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.tool :as tool]))

(def schema:sample
  [:map {:closed true}
   [:file_id {:description "File id"} :uuid]
   [:format {:optional true} [:enum "png" "svg"]]
   [:scale {:optional true} [:and number? [:> 0]]]])

(deftest json-schema-uses-string-keys-and-values
  (let [js (tool/json-schema schema:sample)]
    (is (= "object" (get js "type")))
    (is (= ["file_id"] (get js "required")))
    (is (= ["png" "svg"] (get-in js ["properties" "format" "enum"])))
    (is (every? string? (keys (get js "properties"))))))

(deftest json-schema-flattens-all-of
  (is (= {"exclusiveMinimum" 0 "type" "number"} (get-in (tool/json-schema schema:sample) ["properties" "scale"])))
  (is (= {"description" "Ratio" "type" "number" "minimum" 0 "maximum" 1}
         (get-in (tool/json-schema [:map [:r {:description "Ratio"} [:and number? [:>= 0] [:<= 1]]]]) ["properties" "r"])))
  (is (= {"type" "string" "pattern" "^#[0-9a-f]{6}$"}
         (get-in (tool/json-schema [:map [:c [:re #"^#[0-9a-f]{6}$"]]]) ["properties" "c"])))
  (is (= {"type" "string" "minLength" 1 "pattern" "^a"}
         (get-in (tool/json-schema [:map [:s [:and [:string {:min 1}] [:re #"^a"]]]]) ["properties" "s"])))
  (is (= {"allOf" [{"type" "number" "minimum" 0} {"type" "number" "minimum" 1}]}
         (get-in (tool/json-schema [:map [:n [:and [:>= 0] [:>= 1]]]]) ["properties" "n"]))
      "parts with different values for one keyword stay apart"))

(deftest coerce-args-decodes-json-arguments
  (let [id "d05b6569-e539-818f-8008-babe0368eab1"
        {:keys [value error]} (tool/coerce-args schema:sample {"file_id" id "format" "png"})]
    (is (nil? error))
    (is (= (parse-uuid id) (:file_id value)))
    (is (= "png" (:format value)))))

(deftest coerce-args-reports-invalid-arguments
  (testing "a wrong enum and a missing required key"
    (let [{:keys [value error]} (tool/coerce-args schema:sample {"format" "gif"})]
      (is (nil? value))
      (is (string? error))
      (is (re-find #"file_id" error))
      (is (re-find #"format" error)))))

(deftest result-helpers-build-mcp-content
  (is (= {:content [{:type :text :text "{\"a\":1}"}] :error? false} (tool/json-result {:a 1})))
  (is (= {:content [{:type :text :text "{\"a\":[],\"b\":{\"c\":1}}"}] :error? false}
         (tool/json-result {:a [] :b {:c 1 :d nil :e []} :f nil})))
  (is (= {:content [{:type :text :text "boom"}] :error? true} (tool/error-result "boom")))
  (is (= {:content [{:type :image :data "AAAA" :mime-type "image/png"}] :error? false}
         (tool/image-result "AAAA" "image/png"))))

(deftest invoke-runs-handler-with-coerced-args
  (let [t {:name "sample"
           :input-schema schema:sample
           :handler (fn [_ctx args] (tool/json-result {:format (:format args)}))}
        res (tool/invoke t {:version-error (constantly nil)} {"file_id" "d05b6569-e539-818f-8008-babe0368eab1" "format" "svg"})]
    (is (= (tool/json-result {:format "svg"}) res))))

(deftest invoke-blocks-every-tool-on-version-error
  (let [called (atom false)
        t {:name "sample"
           :input-schema schema:sample
           :handler (fn [_ _] (reset! called true) (tool/json-result {}))}
        res (tool/invoke t {:version-error (constantly "Unsupported Penpot version 2.19.0")} {"file_id" "d05b6569-e539-818f-8008-babe0368eab1"})]
    (is (false? @called))
    (is (= (tool/error-result "Unsupported Penpot version 2.19.0") res))))

(deftest invoke-returns-user-facing-error-messages
  (let [t {:name "sample"
           :input-schema schema:sample
           :handler (fn [_ _] (throw (tool/user-error "Shape not found")))}
        res (tool/invoke t {:version-error (constantly nil)} {"file_id" "d05b6569-e539-818f-8008-babe0368eab1"})]
    (is (= (tool/error-result "Shape not found") res))))

(deftest invoke-hides-internal-exception-messages
  (let [t {:name "sample"
           :input-schema schema:sample
           :handler (fn [_ _] (throw (ex-info "POST http://penpot/api?token=secret failed" {})))}
        res (tool/invoke t {:version-error (constantly nil)} {"file_id" "d05b6569-e539-818f-8008-babe0368eab1"})]
    (is (= (tool/error-result "Internal error in tool sample") res))))

(deftest invoke-rejects-non-map-arguments
  (let [t {:name "sample" :input-schema schema:sample :handler (fn [_ _] (tool/json-result {}))}]
    (is (true? (:error? (tool/invoke t {:version-error (constantly nil)} ["not" "a" "map"]))))))

(deftest running-out-of-memory-is-reported-as-a-tool-error
  (let [t {:name "big" :input-schema [:map] :handler (fn [_ _] (throw (OutOfMemoryError. "Java heap space")))}
        result (tool/invoke t {:version-error (constantly nil)} {})]
    (is (:error? result))
    (is (re-find #"memory" (get-in result [:content 0 :text])))))
