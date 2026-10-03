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
