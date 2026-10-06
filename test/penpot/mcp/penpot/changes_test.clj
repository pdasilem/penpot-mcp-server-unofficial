(ns penpot.mcp.penpot.changes-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.transit :as transit]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools :as all]))

(def ^:private tools (into {} (map (juxt :name identity)) all/all))

(defn- run [scenario]
  (let [r (replay/run (tools (:tool (replay/recording scenario))) scenario)]
    (is (empty? (:left r)) (str scenario " left recorded requests unused"))
    (replay/data r)))

(defn- update-params [scenario]
  (some #(when (= :update-file (:cmd %)) (transit/decode (:params %))) (:entries (replay/recording scenario))))

(defn- listed-revision [scenario file-id]
  (some (fn [files] (some #(when (= file-id (:id %)) %) files))
        (replay/penpot-answers scenario :get-project-files)))

(deftest a-page-change-is-committed-at-the-revision-penpot-lists
  (doseq [[scenario change] [["manage/api-page" :add-page] ["manage/api-page-rename" :mod-page] ["manage/api-page-delete" :del-page]]
          :let [params (update-params scenario)
                listed (listed-revision scenario (:id params))]]
    (is (nil? (:error (run scenario))) scenario)
    (is (= [(:revn listed) (:vern listed)] [(:revn params) (:vern params)]) scenario)
    (is (= [change] (mapv :type (:changes params))) scenario)))

(deftest the-page-name-is-what-the-agent-asked-for
  (is (= (get (:args (replay/recording "manage/api-page")) "name") (:name (first (:changes (update-params "manage/api-page"))))))
  (is (= (get (:args (replay/recording "manage/api-page-rename")) "name") (:name (first (:changes (update-params "manage/api-page-rename")))))))

(deftest a-page-change-never-downloads-the-file
  (doseq [scenario ["manage/api-page" "manage/api-page-rename" "manage/api-page-delete"]]
    (is (not-any? #{:get-file} (replay/requests scenario)) scenario)))
