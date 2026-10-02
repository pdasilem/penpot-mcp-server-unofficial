(ns penpot.mcp.release-test
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.version :as version]))

(def image "ghcr.io/pdasilem/penpot-mcp-server-unofficial")

(deftest deployment-files-use-the-published-image
  (is (str/includes? (slurp "docker-compose.penpot.yml") (str image ":" version/server-version)))
  (is (str/includes? (slurp "setup.sh") (str "IMAGE=\"" image ":$(server_version)\"")))
  (is (not (re-find #"(?m)^\s*build:" (slurp "docker-compose.penpot.yml"))))
  (doseq [f ["docker-compose.penpot.yml" "README.md" "setup.sh"]]
    (is (not (re-find #"penpot-mcp-server(?!-unofficial)" (slurp f))) f)))

(deftest readme-names-no-release-versions
  (is (nil? (re-find #"\b\d+\.\d+\.\d+(\.\d+)?\b"
                     (str/replace (slurp "README.md") #"\b(127\.0\.0\.1|0\.0\.0\.0)\b" ""))))
  (is (str/includes? (slurp "README.md") (str image ":<version>"))))

(deftest claude-plugin-matches-the-server
  (let [manifest    (json/read-str (slurp "claude-plugin/.claude-plugin/plugin.json"))
        marketplace (json/read-str (slurp ".claude-plugin/marketplace.json"))
        skill       (slurp "claude-plugin/skills/penpot/SKILL.md")]
    (is (= version/server-version (get manifest "version")))
    (is (= "http" (get-in manifest ["mcpServers" "penpot" "type"])))
    (is (= [{"name" "penpot" "source" "./claude-plugin"}]
           (mapv #(select-keys % ["name" "source"]) (get marketplace "plugins"))))
    (is (str/starts-with? skill "---\nname: penpot\ndescription: "))
    (doseq [[_ ref] (re-seq #"\]\((references/[a-z-]+\.md)\)" skill)]
      (is (.exists (io/file "claude-plugin/skills/penpot" ref)) ref))))
