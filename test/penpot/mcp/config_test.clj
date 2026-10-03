(ns penpot.mcp.config-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.config :as config]))

(def required-env
  {"PENPOT_BASE_URL" "http://penpot-frontend:8080"
   "PENPOT_ACCESS_TOKEN" "access-secret"
   "PENPOT_EMAIL" "user@example.com"
   "PENPOT_PASSWORD" "password-secret"
   "PENPOT_MCP_KEY" "mcp-secret-0123456789abcdef0123456789"})

(deftest applies-defaults-for-optional-settings
  (let [cfg (config/load-config required-env)]
    (is (= "http://penpot-frontend:8080" (:penpot-base-url cfg)))
    (is (= "127.0.0.1" (:mcp-host cfg)))
    (is (= 4401 (:mcp-port cfg)))
    (is (= "127.0.0.1" (:ws-host cfg)))
    (is (= 4402 (:ws-port cfg)))
    (is (= 300 (:version-check-interval cfg)))
    (is (= "info" (:log-level cfg)))))

(deftest parses-numeric-settings-from-strings
  (let [cfg (config/load-config (assoc required-env "MCP_PORT" "5000" "VERSION_CHECK_INTERVAL" "60"))]
    (is (= 5000 (:mcp-port cfg)))
    (is (= 60 (:version-check-interval cfg)))))

(deftest strips-trailing-slash-from-base-url
  (is (= "http://penpot:8080" (:penpot-base-url (config/load-config (assoc required-env "PENPOT_BASE_URL" "http://penpot:8080/"))))))

(deftest rejects-missing-required-settings-by-env-name
  (let [ex (try (config/load-config (dissoc required-env "PENPOT_ACCESS_TOKEN" "PENPOT_MCP_KEY"))
                nil
                (catch clojure.lang.ExceptionInfo e e))]
    (is (some? ex))
    (is (str/includes? (ex-message ex) "PENPOT_ACCESS_TOKEN"))
    (is (str/includes? (ex-message ex) "PENPOT_MCP_KEY"))))

(deftest rejects-invalid-port
  (let [ex (try (config/load-config (assoc required-env "MCP_PORT" "not-a-port"))
                nil
                (catch clojure.lang.ExceptionInfo e e))]
    (is (some? ex))
    (is (str/includes? (ex-message ex) "MCP_PORT"))))

(deftest error-message-never-contains-secret-values
  (testing "an invalid configuration with secrets present"
    (let [ex (try (config/load-config (assoc required-env "WS_PORT" "70000" "PENPOT_BASE_URL" ""))
                  nil
                  (catch clojure.lang.ExceptionInfo e e))
          text (str (ex-message ex) (pr-str (ex-data ex)))]
      (is (some? ex))
      (doseq [secret ["access-secret" "password-secret" "mcp-secret-0123456789abcdef0123456789"]]
        (is (not (str/includes? text secret)))))))

(deftest redacted-hides-secrets
  (let [cfg (config/redacted (config/load-config required-env))]
    (is (= "***" (:penpot-access-token cfg)))
    (is (= "***" (:penpot-password cfg)))
    (is (= "***" (:penpot-mcp-key cfg)))
    (is (= "user@example.com" (:penpot-email cfg)))))

(deftest mcp-key-must-be-long
  (let [ex (try (config/load-config (assoc required-env "PENPOT_MCP_KEY" "short")) nil
                (catch clojure.lang.ExceptionInfo e e))]
    (is (some? ex))
    (is (str/includes? (ex-message ex) "PENPOT_MCP_KEY"))))

(deftest toolsets-default-to-read-and-edit
  (is (= #{"read" "edit"} (:toolsets (config/load-config required-env)))))

(deftest toolsets-always-include-read
  (is (= #{"read" "edit" "export"} (:toolsets (config/load-config (assoc required-env "PENPOT_MCP_TOOLSETS" "edit, export")))))
  (is (= #{"read"} (:toolsets (config/load-config (assoc required-env "PENPOT_MCP_TOOLSETS" "read"))))))

(deftest rejects-unknown-toolsets
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"PENPOT_MCP_TOOLSETS"
                        (config/load-config (assoc required-env "PENPOT_MCP_TOOLSETS" "edit,admin")))))
