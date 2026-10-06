(ns penpot.mcp.it
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [penpot.mcp.penpot.rpc :as rpc]))

(defn- read-env-file [path]
  (when (and path (.exists (io/file path)))
    (->> (str/split-lines (slurp path))
         (remove #(or (str/blank? %) (str/starts-with? % "#")))
         (map #(str/split % #"=" 2))
         (into {}))))

(def env
  (merge (into {} (System/getenv))
         (read-env-file (System/getenv "PENPOT_IT_ENV"))))

(def base-url
  (get env "PENPOT_IT_BASE_URL" "http://127.0.0.1:9001"))

(defn client []
  (rpc/client {:base-url base-url :token (get env "PENPOT_ACCESS_TOKEN")}))

(defn with-temp-project [client f]
  (let [team-id (:default-team-id (rpc/call client :get-profile {}))
        project (rpc/call client :create-project {:team-id team-id
                                                  :name (str "it-" (System/currentTimeMillis))})]
    (try
      (f project)
      (finally
        (rpc/call client :delete-project {:id (:id project)})))))

(def test-data-name "Design test data")

(defn test-data-file [client]
  (let [team-id (:default-team-id (rpc/call client :get-profile {}))]
    (or (some (fn [p] (some #(when (= test-data-name (:name %)) %) (rpc/call client :get-project-files {:project-id (:id p)})))
              (rpc/call client :get-projects {:team-id team-id}))
        (throw (ex-info (str "No file named " test-data-name " on the stand") {})))))

(defn with-test-data-copy [client f]
  (with-temp-project client
    (fn [project]
      (let [copy (rpc/call client :duplicate-file {:file-id (:id (test-data-file client)) :name (str "it-" test-data-name)})]
        (rpc/call client :move-files {:ids #{(:id copy)} :project-id (:id project)})
        (f project copy)))))
