(ns penpot.mcp.penpot.version-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.penpot.version :as version]))

(def index-html
  "<script>\n  globalThis.penpotVersion = \"2.18.1\";\n  globalThis.penpotVersionTag = \"2.18.1-1790845164\";\n</script>")

(deftest parses-version-from-index-html
  (is (= "2.18.1" (version/parse-version index-html))))

(deftest parse-returns-nil-without-version
  (is (nil? (version/parse-version "<html></html>")))
  (is (nil? (version/parse-version nil))))

(deftest supported-version-is-pinned
  (is (= "2.18.1" version/supported)))

(deftest incompatible-message-names-both-versions
  (is (= "Unsupported Penpot version 2.19.0; this MCP server supports 2.18.1. Update the MCP server."
         (version/incompatible-message "2.19.0"))))

(deftest undetermined-message-is-distinct
  (is (= "Cannot determine Penpot version; check that Penpot is reachable from the MCP server."
         version/undetermined-message)))

(deftest check-error-depends-on-detected-version
  (is (nil? (version/check-error {:version "2.18.1"})))
  (is (= (version/incompatible-message "2.17.0") (version/check-error {:version "2.17.0"})))
  (is (= version/undetermined-message (version/check-error {})))
  (is (= version/undetermined-message (version/check-error {:version nil}))))

(deftest refresh-stores-fetched-version
  (let [state (atom {:version nil})]
    (version/refresh! state (constantly index-html))
    (is (= "2.18.1" (:version @state)))))

(deftest refresh-keeps-last-known-version-when-fetch-fails
  (testing "a transient Penpot outage does not change the known version"
    (let [state (atom {:version "2.18.1"})]
      (version/refresh! state (fn [] (throw (ex-info "down" {}))))
      (is (= "2.18.1" (:version @state))))))

(deftest refresh-leaves-version-undetermined-when-never-fetched
  (let [state (atom {})]
    (version/refresh! state (fn [] (throw (ex-info "down" {}))))
    (is (= version/undetermined-message (version/check-error @state)))))

(deftest refresh-replaces-version-when-penpot-changes
  (let [state (atom {:version "2.18.1"})]
    (version/refresh! state (constantly "penpotVersion = \"2.19.0\";"))
    (is (= "2.19.0" (:version @state)))))

(deftest checker-refreshes-periodically
  (let [calls (atom 0)
        state (atom {:version nil})
        checker (version/start-checker! state (fn [] (swap! calls inc) index-html) 50)]
    (try
      (loop [n 0]
        (when (and (< n 100) (< @calls 3))
          (Thread/sleep 20)
          (recur (inc n))))
      (is (= "2.18.1" (:version @state)))
      (is (<= 3 @calls))
      (finally
        (version/stop-checker! checker)))))

(deftest server-version-is-penpot-version-plus-fix-number
  (is (= "2.18.1.0" version/server-version))
  (is (str/starts-with? version/server-version (str version/supported "."))))
