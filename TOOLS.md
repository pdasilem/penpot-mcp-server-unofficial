# Tools

Tools of the server, grouped as in the server, plus `list_toolsets` and `set_toolset`.

| Convention | |
|---|---|
| Ids | Strings in UUID format |
| Keys in results | snake_case from API tools; Plugin API names (camelCase) from editor tools |
| Edit results | `{id, changed}`: only the values the call changed; empty and default values are left out of every result |
| Lists | Up to `limit` items (default 100, max 500); `next_cursor` is passed back as `cursor` |
| Shape types | Penpot Plugin API names: `board`, `rectangle`, `ellipse`, `text`, `path`, `group`, `boolean`, `image`, `svg-raw` |
| Colors | `#RRGGBB` |
| Fill | `{color, opacity}` or `{gradient: {type, start_x, start_y, end_x, end_y, stops: [{color, opacity, offset}]}}` |
| Stroke | `{color, opacity, width, style, alignment}` |
| Errors | Tool result with `isError: true` and a message |

Tools marked `[editor]` run in the Penpot editor through the bundled MCP plugin. They need the file open in a browser tab with MCP enabled; when the shape is on another page the editor switches to it. Reads issued after editor changes return the saved state.

Shapes are read one page at a time. When the file is open in the editor, page lists, the page of a shape, the library and design tokens come from the editor, and `create_page`, `rename_page` and `delete_page` run there. Without the editor, tools that need the whole file (`search_shapes` without `page_id`, `get_component_instances`, the library tools, `token_usage`, `list_media`, `compare_snapshots`) refuse files with more shapes than `FULL_FILE_SHAPES_MAX`; `list_media` and `compare_snapshots` refuse them with the editor too.

Groups: `read` and `edit` are enabled by default, `manage`, `export` and `import` are enabled with `set_toolset` or `PENPOT_MCP_TOOLSETS`.

Hints are the MCP tool annotations: read-only tools do not change Penpot data; destructive tools overwrite or delete existing data; idempotent tools have no further effect when repeated with the same arguments.

## Account and projects

### `get_profile`

Show the Penpot account the server works as: id, email, full name, default team id and default project id. Use the team id with list_projects or search_files.

Group: `read`. Hints: read-only, idempotent

### `list_teams`

List the teams of the Penpot account: id, name and whether it is the default team. Team ids are needed by list_projects, create_project, search_files, list_fonts and list_webhooks.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `list_projects`

List projects with id, name, team id, default flag and last modification. Without team_id the projects of all teams are returned. Project ids are needed by list_files and create_file.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `team_id` | uuid | no | Team id |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `create_project`

Create a project in a team. Returns the new project id, name and team id.

Group: `manage`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `team_id` | uuid | yes | Team id |
| `name` | string | yes | Project name |

### `rename_project`

Rename a project. Returns the project id and the new name.

Group: `manage`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `project_id` | uuid | yes | Project id |
| `name` | string | yes | New project name |

## Files

### `list_files`

List the files of a project: id, name, shared-library flag, revision and last modification. File ids are needed by almost every other tool.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `project_id` | uuid | yes | Project id |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `search_files`

Find files of a team whose name contains the query. Returns id, name, project id, shared flag and last modification of each match.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `team_id` | uuid | yes | Team id |
| `query` | string | yes | Text to search in file names |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `get_file`

Summarize a file: name, project and team ids, revision, features, its pages in order with id, name and shape count, and the number of components, colors, typographies, token sets and media in its local library. Start here to learn page ids. When the file is open in the editor, features and the media count are left out; when it is not open and is above the server's size limit, only the ids, revision and the numbers of components, colors and typographies are returned.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |

### `get_file_libraries`

List the shared libraries linked to a file: id, name, project id and shared flag.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |

### `create_file`

Create an empty file with one page in a project. Returns the new file id, name and project id. Open the file in the Penpot editor before using canvas tools on it.

Group: `manage`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `project_id` | uuid | yes | Project id |
| `name` | string | yes | File name |

### `rename_file`

Rename a file. Returns the file id and the new name.

Group: `manage`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `name` | string | yes | New file name |

### `duplicate_file`

Copy a file into the same project, with all pages, shapes and library items. Returns the copy's id, name and project id.

Group: `manage`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `name` | string | no | Name of the copy |

### `delete_file`

Delete a file. Penpot moves it to the team trash, where it can be restored from the Penpot dashboard until the retention period ends. Returns the deleted file id.

Group: `manage`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |

## Versions

### `list_snapshots`

List the saved versions of a file: id, label, creation time, author and revision. Snapshot ids are used by compare_snapshots.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `compare_snapshots`

Compare a saved version with another version or with the current file. Returns added and removed pages, and for each changed page the added, removed and modified shapes with the names of the changed attributes. When the answer would be longer than 30,000 characters, pages carry only the counts of added, removed and modified shapes, and full_result holds a one-time download of the whole comparison.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `from_snapshot_id` | uuid | yes | Snapshot to compare from |
| `to_snapshot_id` | uuid | no | Snapshot to compare to; defaults to the current file |

### `create_snapshot`

Save the current state of a file as a named version that can be restored from Penpot's history panel. Changes made in the editor are saved first. Returns the snapshot id, label and creation time.

Group: `manage`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `label` | string | yes | Version label |

## Pages

### `create_page`

Add an empty page at the end of a file. Returns the new page id and name.

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `name` | string | yes | Page name |

### `rename_page`

Rename a page. Returns the page id and the new name.

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `page_id` | uuid | yes | Page id |
| `name` | string | yes | New page name |

### `delete_page`

Delete a page with everything on it. This cannot be undone except by restoring a snapshot, and the last page of a file cannot be deleted. Returns the deleted page id.

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `page_id` | uuid | yes | Page id |

## Reading shapes and code

### `list_shapes`

List the shapes of a page, sorted top to bottom and left to right: id, name, type, parent id and absolute canvas position and size. The page root is omitted. Use it to find shape ids, for example boards to export.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `page_id` | uuid | no | Page id; defaults to the first page of the file |
| `type` | `board`, `boolean`, `ellipse`, `group`, `image`, `path`, `rectangle`, `svg-raw`, `text` | no | Only shapes of this type |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `get_shape_tree`

Return the layer tree of a page, or of one shape, with id, name, type, geometry and child count per node. Children are listed bottom to top. Use depth to limit the size of the answer; a tree longer than 30,000 characters comes as the root with node_count and a one-time download of the whole tree in full_result.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `page_id` | uuid | no | Page id; defaults to the page of root_id when the file is open in the editor, otherwise to the first page |
| `root_id` | uuid | no | Shape to start from; defaults to the root frame |
| `depth` | integer | no | Levels of children to include (default 3) |

### `get_shape`

Return all Penpot attributes of one shape (fills, strokes, layout, text content, tokens and so on), its plugin type and the id of the page it is on. When the answer would be longer than 30,000 characters, content is replaced by its size and full_result holds a one-time download of the whole shape.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `page_id` | uuid | no | Page the shape is on; required when the file is not open in the Penpot editor |

### `search_shapes`

Find shapes whose name contains the query, ignoring case, on every page or on one page. Returns id, name, type, parent id, geometry and page id of each match.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `query` | string | yes | Text to search in shape names |
| `page_id` | uuid | no | Restrict the search to this page |
| `type` | `board`, `boolean`, `ellipse`, `group`, `image`, `path`, `rectangle`, `svg-raw`, `text` | no | Only shapes of this type |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `get_shape_css`

Generate CSS for a shape, and optionally for all its visible descendants: size, position (when not inside a layout), fills and gradients, border, radius, shadows, blur, flex and grid layout, and text styles. Returns one rule per shape and the whole stylesheet as text. When the answer would be longer than 30,000 characters it holds the rule of the shape itself and rule_count, and full_result holds a one-time download of styles.css and rules.json.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `page_id` | uuid | no | Page the shape is on; required when the file is not open in the Penpot editor |
| `include_children` | boolean | no | Also generate rules for all descendants |

### `get_shape_svg`

Render a shape and its descendants as a standalone SVG document. With the file open in the editor the markup comes from Penpot itself; otherwise it is drawn from the saved file data, with text as plain SVG text and images as placeholders. Use export_shape for a raster image. Markup longer than 30,000 characters comes as svg_bytes and a one-time download in full_result.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `page_id` | uuid | no | Page the shape is on; required when the file is not open in the Penpot editor |

## Library

### `list_components`

List the components of the file's local library: id, name, path and the id and page of the main instance. query keeps the components whose path and name, written as path / name, contain it, ignoring case.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `query` | string | no | Text to find in the component path and name |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `get_component_instances`

List the component instances placed in the file, optionally only those of one component: shape id, name, page id, component id, the file the component comes from and whether it is the main instance.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `component_id` | uuid | no | Only instances of this component |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `get_colors`

List the colors of the file's local library: id, name, path, hex color, opacity and gradient or image when present.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `get_typographies`

List the typographies of the file's local library with their font family, size, weight, style, line height, letter spacing and text transform. While the file is open in the editor the line height is missing, because Penpot's plugin API does not report it.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `get_design_tokens`

List the design token sets of the file and whether each is active, with every token's id, name, type, value and description, and the token themes with their id, group, name, whether each is active and the names of their sets. Token ids are used by set_token. set, type and query narrow the tokens; sets without matching tokens are listed with no tokens. When the answer would be longer than 30,000 characters it lists the sets with token_count and the themes, and full_result holds a one-time download of the whole answer: Claude Code can fetch it with the curl command, other clients give it to the user.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `set` | string | no | Only this token set |
| `type` | `boolean`, `border-radius`, `color`, `dimensions`, `font-family`, `font-size`, `font-weight`, `letter-spacing`, `number`, `opacity`, `other`, `rotation`, `shadow`, `sizing`, `spacing`, `string`, `stroke-width`, `text-case`, `text-decoration`, `typography` | no | Only tokens of this type |
| `query` | string | no | Only tokens whose name contains this text, ignoring case |

### `token_usage`

Audit how the file uses its design tokens, across every page including component pages. Returns a summary; unused: tokens no shape applies and no used token references, with their sets and values; missing: token names applied to shapes that are not in the token catalog; referenced_only: tokens used only through other tokens; references: tokens whose value contains other tokens and whether they are used; usage: for each used token the number of shapes, how many of them are component copies, the pages and the attributes it is applied to; raw_values: values set as plain numbers or colors instead of tokens (padding, the gaps the layout uses, radius, fill, stroke color and width, font size, and the size of fixed nested boards), grouped by top-level board and shape. Zeros, library colors and typographies are left out; component copies are not checked, so values overridden on a copy are not reported. Each raw value lists the tokens of the default theme combination with the same value in matches, or off_scale when none has it; summary.unresolved_tokens names tokens whose value could not be computed. sections picks the parts to return besides the summary. page_id narrows raw_values to one page; raw_values are paged with limit and cursor. When the other parts would be longer than 30,000 characters they are left out, archived_sections names them and full_result holds a one-time download of them.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `page_id` | uuid | no | Only report raw values on this page |
| `sections` | array of `unused`, `missing`, `referenced_only`, `references`, `usage`, `raw_values` | no | Parts to return besides the summary; all by default |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

## Comments

### `list_comments`

List the comment threads of a file in order, with page, board, position, resolved state and every comment with its author and time. Optionally only open or only resolved threads.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `resolved` | boolean | no | true: only resolved threads; false: only open threads |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `create_comment`

Start a comment thread at a canvas position on a page, optionally attached to a board. Returns the thread id and its number in the file.

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `page_id` | uuid | no | Page id; defaults to the first page of the file |
| `frame_id` | uuid | no | Board the comment belongs to; defaults to the page root |
| `x` | number | yes | Canvas X position |
| `y` | number | yes | Canvas Y position |
| `content` | string | yes | Comment text |

### `reply_comment`

Add a reply to a comment thread. Returns the new comment id.

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `thread_id` | uuid | yes | Comment thread id |
| `content` | string | yes | Reply text |

### `resolve_comment`

Mark a comment thread as resolved, or reopen it with resolved=false. Returns the thread id and its resolved state.

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `thread_id` | uuid | yes | Comment thread id |
| `resolved` | boolean | no | Resolved state to set (default true) |

### `update_comment`

Replace the text of a comment. Penpot allows editing only comments written by the account the server works as. Returns the comment id and its new text.

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `comment_id` | uuid | yes | Comment id from list_comments |
| `content` | string | yes | New comment text |

### `delete_comment`

Delete one comment from a thread. Penpot allows deleting only comments written by the account the server works as.

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `comment_id` | uuid | yes | Comment id from list_comments |

### `delete_comment_thread`

Delete a comment thread with all its replies. Penpot allows deleting only threads started by the account the server works as.

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `thread_id` | uuid | yes | Comment thread id |

## Media and fonts

### `list_media`

List the images of the file's local library: id, name, width, height and MIME type.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `list_fonts`

List the custom fonts uploaded to a team: id, font id, family, weight and style. Font families can be used by create_text and set_text_style.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `team_id` | uuid | yes | Team id |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `upload_media_from_url`

Download an image from a public http or https URL into the file's local library. Penpot refuses internal network addresses. Returns the image id, name, size and MIME type.

Group: `edit`. Hints: changes data, open world

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `url` | string | yes | http or https URL of the image |
| `name` | string | no | Name in the library |

## Integrations and presence

### `list_webhooks`

List the webhooks of a team: id, target URL, payload type, whether it is active and its error count.

Group: `manage`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `team_id` | uuid | yes | Team id |
| `limit` | integer | no | Maximum number of items to return, default 100 |
| `cursor` | string | no | next_cursor from the previous call, to get the next items |

### `get_active_users`

List the users who have the file open in Penpot right now, with their name and number of open sessions. Presence is collected for about 1.5 seconds, so an editor that answers later can be missed.

Group: `read`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |

## Creating shapes (editor)

### `create_board`

Create a board (frame) at absolute canvas coordinates, on a page or inside another board or group. Inside a board with flex or grid layout the layout decides the position. Returns the new shape. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `width` | number | yes | Width |
| `file_id` | uuid | yes | Penpot file id |
| `height` | number | yes | Height |
| `constraint_vertical` | `top`, `bottom`, `topbottom`, `center`, `scale` | no | top, bottom, topbottom, center or scale |
| `x` | number | yes | Canvas X |
| `page_id` | uuid | no | Page to create the shape on; defaults to the page open in the editor |
| `constraint_horizontal` | `left`, `right`, `leftright`, `center`, `scale` | no | left, right, leftright, center or scale |
| `name` | string | no | Layer name |
| `y` | number | yes | Canvas Y |
| `parent_id` | uuid | no | Board or group to put the shape into |
| `clip_content` | boolean | no | Clip children to the board bounds (default true) |
| `absolute` | boolean | no | true places the shape out of the flex or grid layout of parent_id, at x and y |

### `create_rect`

Create a rectangle at absolute canvas coordinates, on a page or inside a board or group; color it with set_fills and set_strokes. Returns the new shape. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `width` | number | yes | Width |
| `file_id` | uuid | yes | Penpot file id |
| `height` | number | yes | Height |
| `border_radius` | number | no | Corner radius for all corners |
| `constraint_vertical` | `top`, `bottom`, `topbottom`, `center`, `scale` | no | top, bottom, topbottom, center or scale |
| `x` | number | yes | Canvas X |
| `page_id` | uuid | no | Page to create the shape on; defaults to the page open in the editor |
| `constraint_horizontal` | `left`, `right`, `leftright`, `center`, `scale` | no | left, right, leftright, center or scale |
| `name` | string | no | Layer name |
| `y` | number | yes | Canvas Y |
| `parent_id` | uuid | no | Board or group to put the shape into |
| `absolute` | boolean | no | true places the shape out of the flex or grid layout of parent_id, at x and y |

### `create_ellipse`

Create an ellipse that fills the given bounding box, on a page or inside a board or group. Returns the new shape. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `width` | number | yes | Width |
| `file_id` | uuid | yes | Penpot file id |
| `height` | number | yes | Height |
| `constraint_vertical` | `top`, `bottom`, `topbottom`, `center`, `scale` | no | top, bottom, topbottom, center or scale |
| `x` | number | yes | Canvas X |
| `page_id` | uuid | no | Page to create the shape on; defaults to the page open in the editor |
| `constraint_horizontal` | `left`, `right`, `leftright`, `center`, `scale` | no | left, right, leftright, center or scale |
| `name` | string | no | Layer name |
| `y` | number | yes | Canvas Y |
| `parent_id` | uuid | no | Board or group to put the shape into |
| `absolute` | boolean | no | true places the shape out of the flex or grid layout of parent_id, at x and y |

### `create_text`

Create a text layer. Penpot measures the text with the real font: with grow_type auto-width (the default) the box fits the text, with auto-height the width is fixed and the height grows. A typography token and a color token can be bound in the same call. Returns the new shape. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `color_token_id` | uuid | no | Color token to bind to the text fill, from get_design_tokens |
| `file_id` | uuid | yes | Penpot file id |
| `font_family` | string | no | Font family, e.g. sourcesanspro or a family from list_fonts |
| `grow_type` | `fixed`, `auto-width`, `auto-height` | no | Default auto-width |
| `constraint_vertical` | `top`, `bottom`, `topbottom`, `center`, `scale` | no | top, bottom, topbottom, center or scale |
| `typography_token_id` | uuid | no | Typography token to bind, from get_design_tokens |
| `x` | number | yes | Canvas X |
| `page_id` | uuid | no | Page to create the shape on; defaults to the page open in the editor |
| `constraint_horizontal` | `left`, `right`, `leftright`, `center`, `scale` | no | left, right, leftright, center or scale |
| `name` | string | no | Layer name |
| `text` | string | yes | Text content |
| `font_size` | number | no | Font size in pixels |
| `font_weight` | `100`, `200`, `300`, `400`, `500`, `600`, `700`, `800`, `900` | no | Font weight |
| `y` | number | yes | Canvas Y |
| `parent_id` | uuid | no | Board or group to put the shape into |
| `absolute` | boolean | no | true places the shape out of the flex or grid layout of parent_id, at x and y |

### `create_path`

Create a path from SVG path data (the d attribute, absolute coordinates). Optional x and y move the finished path. Returns the new shape. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `d` | string | yes | SVG path data, e.g. M0 0 L100 0 L100 100 Z |
| `file_id` | uuid | yes | Penpot file id |
| `constraint_vertical` | `top`, `bottom`, `topbottom`, `center`, `scale` | no | top, bottom, topbottom, center or scale |
| `x` | number | no | Canvas X to move the path to |
| `page_id` | uuid | no | Page to create the shape on; defaults to the page open in the editor |
| `constraint_horizontal` | `left`, `right`, `leftright`, `center`, `scale` | no | left, right, leftright, center or scale |
| `name` | string | no | Layer name |
| `y` | number | no | Canvas Y to move the path to |
| `parent_id` | uuid | no | Board or group to put the shape into |
| `absolute` | boolean | no | true places the shape out of the flex or grid layout of parent_id, at x and y |

### `create_group`

Group shapes that are on the same page; the group takes the place of the topmost shape. Returns the new group. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_ids` | array of uuid | yes | Shape ids |
| `name` | string | no | Name of the new group or component |

### `create_component`

Turn shapes into a component of the file's local library; the shapes become its main instance. name is the full component name, with the path before the last /, e.g. Icons / Menu. Returns the component id, name, path and the main instance's state. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_ids` | array of uuid | yes | Shape ids |
| `name` | string | no | Name of the new group or component |

## Components and variants (editor)

### `create_component_instance`

Place a copy (instance) of a component on a page or inside a board or group. The component comes from the file's own library or, with library_file_id, from a connected shared library. Returns the copy's state and its component id. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `constraint_vertical` | `top`, `bottom`, `topbottom`, `center`, `scale` | no | top, bottom, topbottom, center or scale |
| `x` | number | yes | Canvas X |
| `page_id` | uuid | no | Page to create the shape on; defaults to the page open in the editor |
| `component_id` | uuid | yes | Component id from list_components |
| `constraint_horizontal` | `left`, `right`, `leftright`, `center`, `scale` | no | left, right, leftright, center or scale |
| `name` | string | no | Layer name |
| `library_file_id` | uuid | no | Id of the connected shared library the component belongs to; omit for the file's own components |
| `y` | number | yes | Canvas Y |
| `parent_id` | uuid | no | Board or group to put the shape into |
| `absolute` | boolean | no | true places the shape out of the flex or grid layout of parent_id, at x and y |

### `create_variants`

Combine components of the file's own library into one variant set, as Penpot's "Combine as variants" does. Their main instances must be on the same page. Penpot derives the first properties from the component names; property and values name the first property and set each variant's value in the same call. Returns the variant set: its id, property names and each variant component with its property values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `component_ids` | array of uuid | yes | Ids of two or more components of this file |
| `property` | string | no | Name for the first property instead of Penpot's Property 1 |
| `values` | array of string | no | Value of that property for each component, in the order of component_ids |

### `set_variant_property`

Set a property value of one variant component. A property the variant set does not have yet is added to the whole set first. Returns the variant set with each variant's property values; a variant whose combination of values clashes with another one shows Penpot's error. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `component_id` | uuid | yes | Component id from list_components |
| `property` | string | yes | Variant property name |
| `value` | string | yes | Variant property value |

### `rename_variant_property`

Rename a property of the variant set a component belongs to; the values stay. Returns the variant set. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `component_id` | uuid | yes | Component id from list_components |
| `property` | string | yes | Variant property name |
| `new_name` | string | yes | New property name |

### `remove_variant_property`

Remove a property from the variant set a component belongs to, with its values in every variant. Returns the variant set. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `component_id` | uuid | yes | Component id from list_components |
| `property` | string | yes | Variant property name |

### `switch_variant`

Switch a component copy to the variant of its set that has the given property value, keeping its other property values where possible, as the variant selector in Penpot's design panel does. Penpot replaces the copy with a copy of the other variant, so the returned shape id can differ from the one passed. Returns the copy's state, component id and property values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `property` | string | yes | Variant property name |
| `value` | string | yes | Variant property value |

### `swap_component`

Replace a component copy with a copy of another component, keeping overrides where possible, as Penpot's "Swap component" does. The new component comes from the file's own library or, with library_file_id, from a connected shared library. Penpot replaces the copy, so the returned shape id can differ from the one passed. Returns the new copy's state and component id. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `component_id` | uuid | yes | Component id from list_components |
| `library_file_id` | uuid | no | Id of the connected shared library the component belongs to; omit for the file's own components |

### `detach_instance`

Detach a component copy from its component, turning it into ordinary shapes that no longer follow the component. Returns the shape's state. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |

### `reset_overrides`

Reset every override of a component copy and its children, restoring the values of the main component. Returns the copy's state. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |

## Changing shapes (editor)

### `set_position`

Move a shape so that its top-left corner is at the given absolute canvas coordinates. A shape inside a flex or grid layout is positioned by the layout instead; use set_parent_index to reorder it. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `x` | number | yes | Canvas X |
| `y` | number | yes | Canvas Y |

### `resize`

Set the width and height of a shape. Penpot applies constraints to its children and reflows the parent layout. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `width` | number | yes | New width in pixels |
| `height` | number | yes | New height in pixels |

### `rotate`

Rotate a shape around its center by the given angle in degrees, added to its current rotation; negative values rotate counterclockwise. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `angle` | number | yes | Degrees to add to the current rotation |

### `rename_shape`

Rename a layer. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `name` | string | yes | New layer name |

### `set_fills`

Replace all fills of a shape. Fills are listed bottom to top; an empty list removes every fill. Penpot allows at most 8 fills. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `fills` | array of objects | yes | Fills, bottom to top; an empty list removes all fills |

### `set_strokes`

Replace all strokes of a shape; an empty list removes every stroke. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `strokes` | array of objects | yes | Strokes; an empty list removes all strokes |

### `set_opacity`

Set the opacity of a layer and its content, from 0 (invisible) to 1 (opaque). Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `opacity` | number | yes | 0 (invisible) to 1 (opaque) |

### `set_radius`

Round the corners of a rectangle, board or image: one radius for all corners, or individual corners. At least one value is required. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `radius` | number | no | Radius in pixels for all corners |
| `top_left` | number | no | Radius in pixels of the top-left corner |
| `top_right` | number | no | Radius in pixels of the top-right corner |
| `bottom_right` | number | no | Radius in pixels of the bottom-right corner |
| `bottom_left` | number | no | Radius in pixels of the bottom-left corner |

### `set_visible`

Show or hide a layer. Hidden layers stay in the file but are not rendered or exported. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `visible` | boolean | yes | true shows the layer, false hides it |

### `set_blocked`

Lock or unlock a layer. Locked layers cannot be selected or changed on the canvas by people; tools can still change them. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `blocked` | boolean | yes | true locks the layer, false unlocks it |

### `set_parent_index`

Move a shape up or down in the stacking order of its parent; 0 is the bottom. In a flex or grid layout this also changes its place in the layout. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `index` | integer | yes | New stacking index inside the parent; 0 is the bottom |

### `move_to_parent`

Move a shape into another board or group on the same page, on top of its children or at the given stacking index. The shape keeps its canvas position unless the new parent has a layout. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `parent_id` | uuid | yes | Target board or group |
| `index` | integer | no | Stacking index inside the parent; 0 is the bottom |

### `delete_shapes`

Delete shapes together with their children. Shapes already deleted, for example as children of an earlier shape in the list, are skipped. Returns the ids that were deleted. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_ids` | array of uuid | yes | Shape ids |

## Appearance and arrangement (editor)

### `set_layout_child`

Set how a shape behaves inside its parent's flex or grid layout: sizing along each axis (fix keeps its size, fill takes the free space, auto hugs its content), its own alignment, margins, absolute positioning that takes it out of the flow, stacking order and size limits. Only the given properties change. The parent must have a layout. Returns the resulting values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `z_index` | integer | no | Stacking order among the layout children |
| `margin` | object | no | Margins in pixels |
| `vertical_sizing` | `fix`, `fill`, `auto` | no | fix, fill or auto |
| `horizontal_sizing` | `fix`, `fill`, `auto` | no | fix, fill or auto |
| `max_width` | number | no | Maximum width in pixels |
| `min_width` | number | no | Minimum width in pixels |
| `align_self` | `auto`, `start`, `center`, `end`, `stretch` | no | Alignment of this child across the layout direction; auto follows the layout |
| `min_height` | number | no | Minimum height in pixels |
| `max_height` | number | no | Maximum height in pixels |
| `absolute` | boolean | no | true takes the shape out of the layout flow |
| `shape_id` | uuid | yes | Shape id |

### `set_grid_cell`

Place a child of a grid layout board into a cell: row and column start at 1, spans set how many tracks it covers, area_name names the cell's area. Only the given properties change. The parent must have a grid layout. Returns the resulting values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `row` | integer | no | Row, starting at 1 |
| `column` | integer | no | Column, starting at 1 |
| `row_span` | integer | no | Number of rows covered |
| `column_span` | integer | no | Number of columns covered |
| `area_name` | string | no | Name of the cell's area |

### `set_shadows`

Replace all shadows of a shape, bottom to top; an empty list removes them. Returns the resulting values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `shadows` | array of objects | yes | Shadows, bottom to top |

### `set_blur`

Set the layer blur (blurs the shape itself) and the background blur (blurs what is behind it) in pixels; null removes one, an omitted one stays as it is. Returns the resulting values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `layer_blur` | value | no | Layer blur in pixels; null removes it |
| `background_blur` | value | no | Background blur in pixels; null removes it |

### `set_blend_mode`

Set how a shape blends with what is below it. Returns the resulting values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `mode` | `normal`, `darken`, `multiply`, `color-burn`, `lighten`, `screen`, `color-dodge`, `overlay`, `soft-light`, `hard-light`, `difference`, `exclusion`, `hue`, `saturation`, `color`, `luminosity` | yes | Blend mode |

### `set_constraints`

Set how a shape follows its parent board when the board is resized: horizontally left, right, leftright (stretch), center or scale; vertically top, bottom, topbottom (stretch), center or scale. Only the given axes change. Children of a flex or grid layout are placed by the layout instead. Returns the resulting values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `horizontal` | `left`, `right`, `leftright`, `center`, `scale` | no | left, right, leftright, center or scale |
| `vertical` | `top`, `bottom`, `topbottom`, `center`, `scale` | no | top, bottom, topbottom, center or scale |

### `set_proportion_lock`

Lock or unlock the width-to-height ratio of a shape for resizing in the editor. Returns the resulting values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `locked` | boolean | yes | true locks the proportions |

### `set_flip`

Set whether a shape is mirrored horizontally and vertically. The values are the wanted state, not a toggle: a shape already in that state does not change. Only the given axes change. Returns the resulting values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `horizontal` | boolean | no | true mirrors the shape left to right |
| `vertical` | boolean | no | true mirrors the shape top to bottom |

### `duplicate_shape`

Duplicate a shape with its children, as Penpot's Duplicate does; the copy is placed by Penpot next to the original in the same parent. Returns the copy. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |

### `create_boolean`

Combine shapes into one boolean shape, as Penpot's boolean operations do: union merges them, difference cuts the upper shapes out of the bottom one, intersection keeps the overlap, exclude keeps everything but the overlap. Boards cannot be combined. Returns the new boolean shape. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_ids` | array of uuid | yes | Shape ids, all on the same page |
| `operation` | `union`, `difference`, `intersection`, `exclude` | yes | union, difference, intersection or exclude |

### `set_mask`

Turn a group into a mask group, where its bottom layer clips the layers above it, or back into an ordinary group. A group already in that state does not change. Returns whether the group is a mask. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `group_id` | uuid | yes | Group id |
| `mask` | boolean | yes | true makes the group a mask group, false an ordinary group |

### `ungroup`

Dissolve a group; its children take its place in the parent. Returns the ids of the former children. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `group_id` | uuid | yes | Group id |

### `flatten`

Convert shapes into editable paths, as Penpot's Flatten does; the shapes are replaced by paths. Returns the resulting paths. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_ids` | array of uuid | yes | Shape ids, all on the same page |

### `import_svg`

Import SVG markup as Penpot shapes inside a new group, for example an icon. The group gets the size of the SVG's width and height attributes, not of its viewBox, unless width and height are given. Images referenced by the SVG are fetched and uploaded to the file. Returns the new group. [editor]

Group: `edit`. Hints: changes data, open world

| Parameter | Type | Required | Description |
|---|---|---|---|
| `width` | number | no | Width of the imported group; by default the width and height attributes of the SVG in pixels |
| `file_id` | uuid | yes | Penpot file id |
| `height` | number | no | Height of the imported group |
| `constraint_vertical` | `top`, `bottom`, `topbottom`, `center`, `scale` | no | top, bottom, topbottom, center or scale |
| `x` | number | yes | Canvas X |
| `page_id` | uuid | no | Page to create the shape on; defaults to the page open in the editor |
| `svg` | string | yes | SVG markup starting with <svg |
| `constraint_horizontal` | `left`, `right`, `leftright`, `center`, `scale` | no | left, right, leftright, center or scale |
| `name` | string | no | Layer name |
| `y` | number | yes | Canvas Y |
| `parent_id` | uuid | no | Board or group to put the shape into |
| `absolute` | boolean | no | true places the shape out of the flex or grid layout of parent_id, at x and y |

### `align_shapes`

Align shapes, as Penpot's align buttons do: several shapes are aligned to their common bounds, a single shape to its parent board. Give a horizontal and/or a vertical alignment. Returns the resulting positions. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_ids` | array of uuid | yes | Shape ids, all on the same page |
| `horizontal` | `left`, `center`, `right` | no | left, center or right |
| `vertical` | `top`, `center`, `bottom` | no | top, center or bottom |

### `distribute_shapes`

Space three or more shapes evenly along an axis between the outermost ones, as Penpot's distribute buttons do. Returns the resulting positions. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_ids` | array of uuid | yes | Shape ids, all on the same page |
| `axis` | `horizontal`, `vertical` | yes | horizontal or vertical |

## Layout (editor)

### `set_flex_layout`

Give a board a flex layout, replacing a grid layout if it has one, or change its flex settings; only the given settings change. Penpot reflows the children. Returns the resulting layout settings. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `board_id` | uuid | yes | Board id |
| `file_id` | uuid | yes | Penpot file id |
| `align_content` | `start`, `end`, `center`, `space-between`, `space-around`, `space-evenly`, `stretch` | no | Distribution of lines or tracks across the cross axis |
| `vertical_sizing` | `fix`, `auto` | no | fix keeps the height, auto hugs the content |
| `padding` | object | no | Inner padding in pixels; omitted sides keep their value |
| `row_gap` | number | no | Gap between rows in pixels |
| `wrap` | `wrap`, `nowrap` | no | Whether children wrap to new lines |
| `horizontal_sizing` | `fix`, `auto` | no | fix keeps the width, auto hugs the content |
| `column_gap` | number | no | Gap between columns in pixels |
| `dir` | `row`, `row-reverse`, `column`, `column-reverse` | no | Main axis direction |
| `align_items` | `start`, `end`, `center`, `stretch` | no | Alignment of children across the main axis |
| `justify_items` | `start`, `end`, `center`, `stretch` | no | Alignment of children inside their grid cells |
| `justify_content` | `start`, `center`, `end`, `space-between`, `space-around`, `space-evenly`, `stretch` | no | Distribution of children along the main axis |

### `set_grid_layout`

Give a board a grid layout, replacing a flex layout if it has one, or change it; only the given settings change. Given columns or rows change the existing tracks in order, keeping the shapes in their cells; missing tracks are added, and extra tracks are removed only when they hold no shapes, otherwise nothing changes and the error names them. Penpot reflows the children. Returns the resulting layout settings. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `board_id` | uuid | yes | Board id |
| `file_id` | uuid | yes | Penpot file id |
| `align_content` | `start`, `end`, `center`, `space-between`, `space-around`, `space-evenly`, `stretch` | no | Distribution of lines or tracks across the cross axis |
| `columns` | array of objects | no | Column tracks, left to right; existing columns change in order, missing ones are added, extra empty ones are removed |
| `vertical_sizing` | `fix`, `auto` | no | fix keeps the height, auto hugs the content |
| `padding` | object | no | Inner padding in pixels; omitted sides keep their value |
| `row_gap` | number | no | Gap between rows in pixels |
| `horizontal_sizing` | `fix`, `auto` | no | fix keeps the width, auto hugs the content |
| `column_gap` | number | no | Gap between columns in pixels |
| `dir` | `row`, `column` | no | Direction in which children fill the grid |
| `align_items` | `start`, `end`, `center`, `stretch` | no | Alignment of children across the main axis |
| `justify_items` | `start`, `end`, `center`, `stretch` | no | Alignment of children inside their grid cells |
| `rows` | array of objects | no | Row tracks, top to bottom; existing rows change in order, missing ones are added, extra empty ones are removed |
| `justify_content` | `start`, `center`, `end`, `space-between`, `space-around`, `space-evenly`, `stretch` | no | Distribution of children along the main axis |

### `remove_layout`

Remove the flex or grid layout of a board; the children keep their current positions. Returns the resulting layout settings. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `board_id` | uuid | yes | Board id |

## Text (editor)

### `set_text_content`

Replace all characters of a text layer, keeping its style. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `text` | string | yes | New characters of the text |

### `set_text_style`

Change the style of a whole text layer; only the given properties change. Use list_fonts for custom font families. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `vertical_align` | `top`, `center`, `bottom` | no | Vertical alignment inside the text box |
| `file_id` | uuid | yes | Penpot file id |
| `font_family` | string | no | Font family, e.g. sourcesanspro or a family from list_fonts |
| `align` | `left`, `center`, `right`, `justify` | no | Horizontal alignment |
| `text_transform` | `uppercase`, `capitalize`, `lowercase`, `none` | no | Letter case transformation; none removes it |
| `grow_type` | `fixed`, `auto-width`, `auto-height` | no | fixed box, auto-width fits the text, auto-height grows downwards |
| `text_decoration` | `underline`, `line-through`, `none` | no | Line decoration; none removes it |
| `font_style` | `normal`, `italic` | no | Font style |
| `direction` | `ltr`, `rtl` | no | Writing direction |
| `font_size` | number | no | Font size in pixels |
| `font_weight` | `100`, `200`, `300`, `400`, `500`, `600`, `700`, `800`, `900` | no | Font weight |
| `letter_spacing` | number | no | Pixels |
| `line_height` | number | no | Multiplier, e.g. 1.2 |
| `shape_id` | uuid | yes | Shape id |

## Styles and library (editor)

### `set_text_range_style`

Style part of a text layer: the characters from start (inclusive) to end (exclusive), counted from 0. Only the given properties change; fills color the characters. Returns the resulting style of the range, where mixed means the range has several values. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `font_family` | string | no | Font family, e.g. sourcesanspro or a family from list_fonts |
| `align` | `left`, `center`, `right`, `justify` | no | Horizontal alignment |
| `fills` | array of objects | no | Fills of the characters |
| `text_transform` | `uppercase`, `capitalize`, `lowercase`, `none` | no | Letter case transformation; none removes it |
| `text_decoration` | `underline`, `line-through`, `none` | no | Line decoration; none removes it |
| `font_style` | `normal`, `italic` | no | Font style |
| `direction` | `ltr`, `rtl` | no | Writing direction |
| `start` | integer | yes | First character, from 0 |
| `font_size` | number | no | Font size in pixels |
| `font_weight` | `100`, `200`, `300`, `400`, `500`, `600`, `700`, `800`, `900` | no | Font weight |
| `letter_spacing` | number | no | Pixels |
| `line_height` | number | no | Multiplier, e.g. 1.2 |
| `end` | integer | yes | Character after the last one |
| `shape_id` | uuid | yes | Shape id |

### `apply_typography`

Apply a library typography to a whole text layer or, with start and end, to part of it; the text stays linked to the typography. The typography comes from the file's own library or, with library_file_id, from a connected shared library; take ids from get_typographies. Returns the resulting style of the styled characters. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `typography_id` | uuid | yes | Typography id from get_typographies |
| `library_file_id` | uuid | no | Id of the connected shared library the style belongs to; omit for the file's own library |
| `start` | integer | no | First character, from 0 |
| `end` | integer | no | Character after the last one |

### `apply_library_color`

Apply a library color to a shape, as clicking it in Penpot's color palette does: target fill replaces the first fill, target stroke recolors the first stroke and keeps its width and style; a shape without one gets one. The shape stays linked to the library color. Take ids from get_colors. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `color_id` | uuid | yes | Library color id from get_colors |
| `library_file_id` | uuid | no | Id of the connected shared library the style belongs to; omit for the file's own library |
| `target` | `fill`, `stroke` | no | fill (default) or stroke |

### `set_image_fill`

Download an image from an http or https URL into the file and make it the only fill of a shape, scaled to cover it. Penpot's server fetches the URL and refuses private network addresses. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, open world

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `url` | string | yes | http or https URL of the image |
| `name` | string | no | Name of the stored image; defaults to the URL's file name |

### `create_library_color`

Add a solid color to the file's own library. Returns the new color with its id. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `name` | string | yes | Name |
| `path` | string | no | Group path in the library, with / between levels |
| `color` | string | yes | Color #RRGGBB |
| `opacity` | number | no | 0..1, default 1 |

### `create_library_typography`

Add a typography to the file's own library. The font family must be one Penpot knows: its bundled fonts, the team's fonts from list_fonts, or Google Fonts when the Google Fonts provider is enabled in Penpot; weight and style must be a variant of that font. Returns the new typography with its id. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `font_family` | string | yes | Font family name, e.g. Work Sans |
| `text_transform` | `uppercase`, `capitalize`, `lowercase` | no | Letter case transformation |
| `path` | string | no | Group path in the library, with / between levels |
| `name` | string | yes | Name |
| `font_style` | `normal`, `italic` | no | Font style, default normal |
| `font_size` | number | yes | Font size in pixels |
| `font_weight` | `100`, `200`, `300`, `400`, `500`, `600`, `700`, `800`, `900` | no | Font weight, default 400 |
| `letter_spacing` | number | no | Pixels |
| `line_height` | number | no | Multiplier, e.g. 1.2 |

## Design tokens (editor)

### `set_token`

Bind a design token to a shape, as Penpot's token panel does. Without attr the token binds the attributes Penpot uses for its type: color binds fill, borderRadius every corner, sizing and dimensions width and height, spacing the gaps of a layout board or the margins of a layout child, typography the text typography; other types bind their own attribute. Pass attr to bind one specific attribute, for example strokeColor for a color token, or a group: padding, margin or borderRadius for all four sides or corners, gap for both gaps. A different token bound to a target attribute is unbound first; attributes already bound to this token are left as they are, so repeating the call changes nothing. The token type and attribute are checked against the shape type before anything changes. Take token ids from get_design_tokens. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `token_id` | uuid | yes | Token id from get_design_tokens |
| `attr` | `fill`, `strokeColor`, `strokeWidth`, `shadow`, `opacity`, `rotation`, `borderRadiusTopLeft`, `borderRadiusTopRight`, `borderRadiusBottomRight`, `borderRadiusBottomLeft`, `x`, `y`, `width`, `height`, `layoutItemMinW`, `layoutItemMaxW`, `layoutItemMinH`, `layoutItemMaxH`, `rowGap`, `columnGap`, `paddingTop`, `paddingRight`, `paddingBottom`, `paddingLeft`, `marginTop`, `marginRight`, `marginBottom`, `marginLeft`, `fontFamily`, `fontSize`, `fontWeight`, `lineHeight`, `letterSpacing`, `textCase`, `textDecoration`, `typography`, `padding`, `margin`, `borderRadius`, `gap` | no | Shape attribute, as a Penpot Plugin API token property name, or a group: padding, margin and borderRadius for all four sides or corners, gap for both gaps |

### `remove_token`

Unbind design tokens from a shape; attributes keep their current values. Pass exactly one of token_id, to unbind that token from every attribute of the shape, or attr, to unbind whatever token is bound to that attribute or to each attribute of a group (padding, margin, borderRadius, gap). Nothing changes if nothing is bound. Returns the changes. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape id |
| `token_id` | uuid | no | Token id from get_design_tokens; unbinds it from every attribute of the shape |
| `attr` | `fill`, `strokeColor`, `strokeWidth`, `shadow`, `opacity`, `rotation`, `borderRadiusTopLeft`, `borderRadiusTopRight`, `borderRadiusBottomRight`, `borderRadiusBottomLeft`, `x`, `y`, `width`, `height`, `layoutItemMinW`, `layoutItemMaxW`, `layoutItemMinH`, `layoutItemMaxH`, `rowGap`, `columnGap`, `paddingTop`, `paddingRight`, `paddingBottom`, `paddingLeft`, `marginTop`, `marginRight`, `marginBottom`, `marginLeft`, `fontFamily`, `fontSize`, `fontWeight`, `lineHeight`, `letterSpacing`, `textCase`, `textDecoration`, `typography`, `padding`, `margin`, `borderRadius`, `gap` | no | Shape attribute, as a Penpot Plugin API token property name, or a group: padding, margin and borderRadius for all four sides or corners, gap for both gaps |

### `create_token_set`

Create a design token set in the file; use / in the name to group sets. A new set is active unless active is false. As in Penpot's token panel, activating a set switches off the active themes, since their sets no longer match; deactivatedThemes lists them, and set_theme_sets adds the new set to a theme so that it can be switched on again with the set. If a set with this name exists it is returned unchanged. Returns the set. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `name` | string | yes | Set name, e.g. brand/dark |
| `active` | boolean | no | Whether the set is active, default true |

### `delete_token_set`

Delete a design token set with all its tokens. Shapes keep the values of tokens bound from it. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `set_id` | uuid | yes | Token set id from get_design_tokens |

### `set_token_set_active`

Activate or deactivate a design token set; active sets provide the token values Penpot resolves. Setting the state a set already has changes nothing. Returns the set. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `set_id` | uuid | yes | Token set id from get_design_tokens |
| `active` | boolean | yes | true activates the set |

### `create_token`

Create a design token in a set. Penpot validates the value for the type and rejects invalid ones. Repeating the call with the same name, type and value returns the existing token; a different token with the same name is an error. Returns the token with the value Penpot resolves from the active sets. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `set_id` | uuid | yes | Token set id from get_design_tokens |
| `type` | `borderRadius`, `shadow`, `color`, `dimension`, `fontFamilies`, `fontSizes`, `fontWeights`, `letterSpacing`, `number`, `opacity`, `rotation`, `sizing`, `spacing`, `borderWidth`, `textCase`, `textDecoration`, `typography` | yes | Token type |
| `name` | string | yes | Token name, dot separated, e.g. color.primary |
| `value` | value | yes | Token value as Penpot's token editor takes it: a string such as #3366FF, 16, 1.5 or a reference like {spacing.base} * 2; a list of names for fontFamilies; an object with fontFamilies, fontSizes, fontWeight, lineHeight, letterSpacing, textCase and textDecoration for typography; a list of objects with color, offsetX, offsetY, blur, spread and inset for shadow |
| `description` | string | no | Description |

### `update_token`

Change the name, value or description of a design token; only the given fields change. Penpot validates the value for the token's type. Returns the token. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `token_id` | uuid | yes | Token id from get_design_tokens |
| `name` | string | no | New token name |
| `value` | value | no | Token value as Penpot's token editor takes it: a string such as #3366FF, 16, 1.5 or a reference like {spacing.base} * 2; a list of names for fontFamilies; an object with fontFamilies, fontSizes, fontWeight, lineHeight, letterSpacing, textCase and textDecoration for typography; a list of objects with color, offsetX, offsetY, blur, spread and inset for shadow |
| `description` | string | no | New description |

### `delete_token`

Delete a design token. Shapes keep the values the token gave them. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `token_id` | uuid | yes | Token id from get_design_tokens |

### `create_token_theme`

Create a token theme: a named preset of token sets, such as dark in group mode. Only one theme per group is active at a time; activating a theme activates its sets. Returns the theme. [editor]

Group: `edit`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `group` | string | no | Theme group, e.g. mode or brand; default no group |
| `name` | string | yes | Theme name, e.g. dark |
| `set_ids` | array of uuid | no | Token sets the theme activates |

### `delete_token_theme`

Delete a token theme; its sets stay. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `theme_id` | uuid | yes | Token theme id from get_design_tokens |

### `set_token_theme_active`

Activate or deactivate a token theme. Activating deactivates the other theme of the same group and activates the theme's sets. Setting the state a theme already has changes nothing. Returns the theme. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `theme_id` | uuid | yes | Token theme id from get_design_tokens |
| `active` | boolean | yes | true activates the theme |

### `set_theme_sets`

Set the token sets of a theme: activating the theme activates exactly these sets. The list replaces the theme's sets; sets left out stay in the file. Returns the theme. [editor]

Group: `edit`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `theme_id` | uuid | yes | Token theme id from get_design_tokens |
| `set_ids` | array of uuid | yes | All token sets of the theme; empty removes them all |

## Export (editor)

### `export_shape`

Render a shape, for example a board, exactly as Penpot draws it and return it: png as an image the model can see, svg as markup; markup longer than 30,000 characters comes as svg_bytes and a one-time download in full_result. A png is scaled down so that its longer side fits max_size. Mode fill returns the image used as the shape's fill, scaled down the same way. Find board ids with list_shapes or search_shapes. [editor]

Group: `export`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `shape_id` | uuid | yes | Shape to export, e.g. a board id |
| `format` | `png`, `svg` | no | png (default) or svg |
| `mode` | `shape`, `fill` | no | shape (default) or fill |
| `max_size` | integer | no | Longest side of the image in pixels, default 768, at most 1568; smaller images keep their size |

### `export_design_system`

Export the file's design tokens for every theme combination, with the local library colors and typographies, as css, scss, tailwind, typescript, dtcg, kotlin or swiftui files. Values are computed like Penpot computes them. The result is a one-time download kept for an hour: give the user the curl command with the MCP server URL without its query string in place of <MCP address>. problems.json in the archive lists tokens left out and why. [editor]

Group: `export`. Hints: read-only

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `platform` | `css`, `scss`, `tailwind`, `typescript`, `dtcg`, `kotlin`, `swiftui` | yes | Output format |
| `options` | object | no | Format options: prefix, color_scheme_group, version, package, type_name |
| `options.prefix` | string | no | CSS, SCSS and Tailwind variable prefix |
| `options.color_scheme_group` | string | no | Theme group that follows the light or dark mode of the operating system: a theme counts as light or dark when its name has the word light or dark, and the pair is taken from the palette of the active theme |
| `options.version` | `3`, `4` | no | Tailwind version, 4 by default |
| `options.package` | string | no | Kotlin package, required for kotlin |
| `options.type_name` | string | no | Kotlin or Swift type name, from the file name by default |

## Import of HTML designs (editor)

### `import_html`

Start importing a static HTML design, such as a Claude Design export, into a Penpot file as native boards with flex and grid layouts, text, fills, strokes and shadows. Upload the file first, without passing it through the model: curl --data-binary @design.html "<MCP URL>&upload=html" returns upload_id. Each element matching frame_selector becomes a board; with section_selector each section heading starts a new page named after it. Scripts are ignored. The import runs in the background frame by frame; poll get_import_status. Returns the job id and the number of frames and sections. [editor]

Group: `import`. Hints: changes data, open world

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file_id` | uuid | yes | Penpot file id |
| `upload_id` | uuid | yes | upload_id returned by the upload |
| `frame_selector` | string | no | CSS selector of the frames, e.g. .screen; without it the whole page is one board |
| `section_selector` | string | no | CSS selector of section headings, e.g. h2; each section goes to a new page |
| `page_id` | uuid | no | Page for frames outside sections; defaults to the page open in the editor |
| `viewport_width` | integer | no | Viewport width in pixels for percentages and media queries, default 1440 |
| `font_family` | string | no | Font for families Penpot does not have, default sourcesanspro |

### `get_import_status`

Show the progress of an HTML import. While it runs: status (pending, running, cancelling), frames done of total and the frame in progress. Once finished (done, failed, cancelled): also the created boards with their pages, the error and failed frame, unsupported CSS with counts and fonts replaced by the fallback. Poll at most once a minute.

Group: `import`. Hints: read-only, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `job_id` | uuid | yes | Job id from import_html |

### `cancel_import`

Stop an HTML import after the frame in progress; created boards stay. Returns the job status.

Group: `import`. Hints: changes data, destructive, idempotent

| Parameter | Type | Required | Description |
|---|---|---|---|
| `job_id` | uuid | yes | Job id from import_html |

### `resume_import`

Continue a failed or cancelled HTML import from its first frame that was not created, for example after opening the file in the editor again. Returns the job status.

Group: `import`. Hints: changes data

| Parameter | Type | Required | Description |
|---|---|---|---|
| `job_id` | uuid | yes | Job id from import_html |

