(ns penpot.mcp.server
  (:require
   [penpot.mcp.auth :as auth]
   [penpot.mcp.exports.endpoint :as exports-endpoint]
   [penpot.mcp.html.upload-endpoint :as upload-endpoint]
   [penpot.mcp.penpot.version :as version]
   [penpot.mcp.tool :as tool])
  (:import
   (com.fasterxml.jackson.databind ObjectMapper)
   (io.modelcontextprotocol.json.jackson2 JacksonMcpJsonMapper)
   (io.modelcontextprotocol.server McpServer McpServerFeatures$SyncToolSpecification McpSyncServer)
   (io.modelcontextprotocol.server.transport HttpServletStreamableServerTransportProvider)
   (io.modelcontextprotocol.spec McpSchema$CallToolRequest McpSchema$CallToolResult McpSchema$ImageContent
                                 McpSchema$ServerCapabilities McpSchema$TextContent McpSchema$Tool McpSchema$ToolAnnotations)
   (jakarta.servlet DispatcherType Filter)
   (java.util EnumSet)
   (java.util.function BiFunction)
   (org.eclipse.jetty.ee11.servlet FilterHolder ServletContextHandler ServletHolder)
   (org.eclipse.jetty.server Server ServerConnector)))

(def server-name "penpot-mcp")

(defn- ->clj [x]
  (cond
    (instance? java.util.Map x) (into {} (map (fn [[k v]] [k (->clj v)])) x)
    (instance? java.util.List x) (mapv ->clj x)
    :else x))

(defn- ->content [{:keys [type text data mime-type]}]
  (case type
    :text (.build (McpSchema$TextContent/builder text))
    :image (.build (McpSchema$ImageContent/builder data mime-type))))

(defn- ->call-result [{:keys [content error?]}]
  (-> (McpSchema$CallToolResult/builder)
      (.content (mapv ->content content))
      (.isError (boolean error?))
      (.build)))

(defn- ->annotations [{:keys [read-only destructive idempotent open-world]}]
  (-> (McpSchema$ToolAnnotations/builder)
      (.readOnlyHint (boolean read-only))
      (.destructiveHint (boolean destructive))
      (.idempotentHint (boolean idempotent))
      (.openWorldHint (boolean open-world))
      (.build)))

(defn- ->tool-spec [{:keys [name description input-schema annotations] :as t} ctx]
  (-> (McpServerFeatures$SyncToolSpecification/builder)
      (.tool (-> (McpSchema$Tool/builder)
                 (.name name)
                 (.description description)
                 (.inputSchema ^java.util.Map (tool/json-schema input-schema))
                 (.annotations (->annotations annotations))
                 (.build)))
      (.callHandler
       (reify BiFunction
         (apply [_ _exchange request]
           (->call-result (tool/invoke t ctx (->clj (.arguments ^McpSchema$CallToolRequest request)))))))
      (.build)))

(defn- toolset-tools [{:keys [tools toolset-summaries]} state mcp ctx]
  (let [names (vec (sort (keys toolset-summaries)))]
    [{:name "list_toolsets"
      :description "List the tool groups of this server, whether each is enabled and what it covers. Enable a group with set_toolset before using its tools."
      :annotations tool/read-only
      :input-schema [:map {:closed true}]
      :handler (fn [_ _]
                 (tool/json-result
                  {:toolsets (mapv (fn [n] {:name n :enabled (contains? (:enabled @state) n) :tools (get toolset-summaries n)}) names)}))}
     {:name "set_toolset"
      :description "Enable or disable a group of tools for every session of this server; the client is told that the tool list changed. The read group stays enabled. Setting the state a group already has changes nothing."
      :annotations tool/overwrite
      :input-schema [:map {:closed true}
                     [:name {:description "Tool group from list_toolsets"} (into [:enum] names)]
                     [:enabled {:description "true enables the group, false disables it"} :boolean]]
      :handler (fn [_ {:keys [name enabled]}]
                 (when (and (= "read" name) (not enabled))
                   (throw (tool/user-error "The read group cannot be disabled")))
                 (locking state
                   (let [[before after] (swap-vals! state update :enabled (if enabled conj disj) name)]
                     (when (not= (:enabled before) (:enabled after))
                       (let [^McpSyncServer server @mcp]
                         (doseq [t (filter #(= name (:toolset %)) tools)]
                           (if enabled
                             (.addTool server (->tool-spec t ctx))
                             (.removeTool server ^String (:name t))))
                         (.notifyToolsListChanged server)))))
                 (tool/json-result {:name name :enabled enabled}))}]))

(defn- build-mcp [transport {:keys [tools instructions toolsets toolset-summaries] :as opts} ctx]
  (let [state   (atom {:enabled (set toolsets)})
        mcp     (promise)
        enabled (if toolset-summaries
                  (concat (filter #(contains? (:enabled @state) (:toolset %)) tools)
                          (toolset-tools opts state mcp ctx))
                  tools)
        server  (-> (McpServer/sync transport)
                    (.serverInfo server-name version/server-version)
                    (.instructions instructions)
                    (.capabilities (-> (McpSchema$ServerCapabilities/builder) (.tools true) (.build)))
                    (.tools ^java.util.List (mapv #(->tool-spec % ctx) enabled))
                    (.build))]
    (deliver mcp server)
    server))

(defn start! [{:keys [host port mcp-key ctx upload-limit] :as opts}]
  (let [transport (-> (HttpServletStreamableServerTransportProvider/builder)
                      (.jsonMapper (JacksonMcpJsonMapper. (ObjectMapper.)))
                      (.mcpEndpoint "/mcp")
                      (.build))
        mcp       (build-mcp transport opts ctx)
        handler   (doto (ServletContextHandler.)
                    (.setContextPath "/")
                    (cond-> (:exports ctx)
                      (.addFilter (FilterHolder. ^Filter (exports-endpoint/export-filter (:exports ctx)))
                                  "/mcp" (EnumSet/of DispatcherType/REQUEST)))
                    (.addFilter (FilterHolder. ^Filter (auth/user-token-filter mcp-key)) "/*" (EnumSet/of DispatcherType/REQUEST))
                    (cond-> (:uploads ctx)
                      (.addFilter (FilterHolder. ^Filter (upload-endpoint/upload-filter (:uploads ctx) (or upload-limit upload-endpoint/default-limit)))
                                  "/mcp" (EnumSet/of DispatcherType/REQUEST)))
                    (.addServlet (ServletHolder. transport) "/mcp"))
        jetty     (Server.)
        connector (doto (ServerConnector. jetty) (.setHost host) (.setPort port))]
    (.addConnector jetty connector)
    (.setHandler jetty handler)
    (try
      (.start jetty)
      (catch Throwable t
        (.close ^McpSyncServer mcp)
        (.stop jetty)
        (throw t)))
    {:jetty jetty :mcp mcp :port (.getLocalPort connector)}))

(defn stop! [{:keys [^Server jetty ^McpSyncServer mcp]}]
  (try
    (.closeGracefully mcp)
    (finally
      (.stop jetty))))
