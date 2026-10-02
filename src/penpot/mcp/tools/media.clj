(ns penpot.mcp.tools.media
  (:require
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- list-media [ctx {:keys [file_id] :as args}]
  (tool/json-result
   (common/paged :media (->> (vals (get-in (common/fetch-file ctx file_id) [:data :media]))
                             (sort-by :name)
                             (mapv #(select-keys % [:id :name :width :height :mtype]))) args)))

(defn- list-fonts [{:keys [rpc]} {:keys [team_id] :as args}]
  (tool/json-result
   (common/paged :fonts (mapv #(select-keys % [:id :font-id :font-family :font-weight :font-style])
                              (rpc/call rpc :get-font-variants {:team-id team_id})) args)))

(def http-url
  [:and [:string {:min 1 :max 2048}] [:re #"(?i)^https?://\S+\z"]])

(defn- upload-media [{:keys [rpc]} {:keys [file_id url name]}]
  (tool/json-result
   (select-keys (rpc/call rpc :create-file-media-object-from-url
                          (cond-> {:file-id file_id :is-local true :url url} name (assoc :name name)))
                [:id :name :width :height :mtype])))

(def tools
  [{:name "list_media"
    :description "List the images of the file's local library: id, name, width, height and MIME type."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true} common/file-id-param] common/page-params)
    :handler list-media}
   {:name "list_fonts"
    :description "List the custom fonts uploaded to a team: id, font id, family, weight and style. Font families can be used by create_text and set_text_style."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         [:team_id {:description "Team id"} :uuid]] common/page-params)
    :handler list-fonts}
   {:name "upload_media_from_url"
    :description "Download an image from a public http or https URL into the file's local library. Penpot refuses internal network addresses. Returns the image id, name, size and MIME type."
    :annotations tool/external
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:url {:description "http or https URL of the image"} http-url]
                   [:name {:optional true :description "Name in the library"} [:string {:min 1 :max 250}]]]
    :handler upload-media}])
