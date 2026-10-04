(ns penpot.mcp.tools
  (:require
   [clojure.string :as str]
   [penpot.mcp.tools.appearance :as appearance]
   [penpot.mcp.tools.comments :as comments]
   [penpot.mcp.tools.components :as components]
   [penpot.mcp.tools.create :as create]
   [penpot.mcp.tools.export :as export]
   [penpot.mcp.tools.html-import :as html-import]
   [penpot.mcp.tools.files :as files]
   [penpot.mcp.tools.integrations :as integrations]
   [penpot.mcp.tools.layout :as layout]
   [penpot.mcp.tools.library :as library]
   [penpot.mcp.tools.media :as media]
   [penpot.mcp.tools.modify :as modify]
   [penpot.mcp.tools.pages :as pages]
   [penpot.mcp.tools.presence :as presence]
   [penpot.mcp.tools.profile :as profile]
   [penpot.mcp.tools.projects :as projects]
   [penpot.mcp.tools.shapes :as shapes]
   [penpot.mcp.tools.snapshots :as snapshots]
   [penpot.mcp.tools.structure :as structure]
   [penpot.mcp.tools.styles :as styles]
   [penpot.mcp.tools.text :as text]
   [penpot.mcp.tools.token-catalog :as token-catalog]
   [penpot.mcp.tools.tokens :as tokens]))

(def ^:private manage-tools
  #{"create_project" "rename_project" "create_file" "rename_file" "duplicate_file" "delete_file"
    "create_snapshot" "list_webhooks"})

(def ^:private read-tools
  #{"get_profile" "list_teams" "list_projects" "list_files" "search_files" "get_file" "get_file_libraries"
    "list_snapshots" "compare_snapshots" "list_comments" "list_media" "list_fonts" "get_active_users"})

(def ^:private export-tools
  #{"export_shape"})

(def ^:private import-tools
  (set (map :name html-import/tools)))

(defn- toolset-of [t]
  (cond
    (manage-tools (:name t)) "manage"
    (export-tools (:name t)) "export"
    (import-tools (:name t)) "import"
    (read-tools (:name t)) "read"
    :else "edit"))

(def toolset-summaries
  {"read" "read files, pages, shapes, CSS and SVG, library, design tokens, comments, media and who is online"
   "edit" "create and change shapes, layout, text, styles, components, variants, design tokens, pages, comments and media"
   "manage" "projects, files, versions and webhooks"
   "export" "render shapes as images or SVG"
   "import" "import static HTML designs as native Penpot boards"})

(def all
  (let [tools (vec (concat profile/tools
                           projects/tools
                           files/tools
                           snapshots/tools
                           pages/tools
                           shapes/tools
                           library/tools
                           comments/tools
                           media/tools
                           integrations/tools
                           presence/tools
                           create/tools
                           components/tools
                           modify/tools
                           appearance/tools
                           structure/tools
                           layout/tools
                           text/tools
                           styles/tools
                           tokens/tools
                           token-catalog/tools
                           export/tools
                           html-import/tools))
        reads (set (map :name (concat shapes/tools library/tools)))]
    (mapv #(assoc % :toolset (if (reads (:name %)) "read" (toolset-of %))) tools)))

(def instructions
  (str/join
   " "
   ["Tools for Penpot files, built on Penpot's own data model and plugin API."
    "Tools marked [editor] change the canvas through the Penpot editor: the file must be open in a browser tab with MCP enabled,"
    "and the editor switches to the page of the shape it works on. Other tools use the Penpot API and need no open editor."
    "Shapes are read one page at a time. When the file is open in the editor, page lists, the library and design tokens"
    "are read from the editor; without it, tools that need the whole file refuse files above the server's size limit."
    "A read always sees the result of the edits made before it; there is no need to wait between calls."
    "An [editor] edit returns the shape id and changed: only the values the call changed, empty when nothing changed;"
    "use get_shape for the full state. Create tools return the new shape. Results leave out empty and default values."
    "Ids of files, pages, shapes, components, colors, typographies and tokens come from the read tools."
    "Tools come in groups: read, edit, manage (projects, files, versions), export and import (HTML designs); list_toolsets shows which are enabled"
    "and set_toolset enables another group when a task needs it."]))
