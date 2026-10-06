---
name: penpot
description: Work on Penpot design files through the penpot MCP server - build and change screens, layouts, text, styles, components and variants, design tokens and themes, comments, exports. Use when a task involves a Penpot file, board, component or design token.
---

# Penpot

## Toolsets

The server starts with the `read` and `edit` groups. Enable another group with `set_toolset` before using it; `list_toolsets` shows the current state.

| Group | Use for |
|---|---|
| `read` | Finding files, pages and shapes; CSS and SVG of shapes; library; design tokens and their usage; comments |
| `edit` | Every change on the canvas, components, variants, design tokens, pages, comments, media upload |
| `manage` | Creating, renaming, duplicating and deleting projects and files; versions (snapshots); webhooks |
| `export` | `export_shape`: a board or shape as PNG or SVG; `export_design_system`: design tokens, library colors and typographies as code |
| `import` | Importing a static HTML design as native boards: [references/import.md](references/import.md) |

## Finding things

1. `get_profile` gives the default team; `list_projects`, `list_files` or `search_files` give the file id.
2. `get_file` lists pages; `get_shape_tree` or `list_shapes` give shape ids; `search_shapes` finds shapes by name.
3. Lists return up to 100 items; pass `next_cursor` back as `cursor` for more.

## Editing rules

- Tools marked `[editor]` need the file open in a Penpot browser tab with MCP enabled. Without it they fail with "Open file ... in the Penpot editor".
- An edit returns `{id, changed}`; `changed` holds only what the call changed and is empty when nothing changed. Use `get_shape` for the full state.
- Reads right after edits see the edits; no waiting is needed.
- Calls are idempotent where Penpot allows it: repeating `set_*` with the same values changes nothing.

## Building a screen

1. `create_board` with size and position; `set_fills` for its background.
2. `set_flex_layout` or `set_grid_layout` on the board.
3. Create children with `parent_id` set to the board: `create_rect`, `create_ellipse`, `create_text`, `create_path`, `create_component_instance`, `import_svg`. Creation takes no colors; use `set_fills` and `set_strokes` afterwards.
4. Inside a grid board, place each child with `set_grid_cell` (`row` and `column` start at 1).
5. `set_layout_child` sets how a child sizes (`fix`, `fill`, `auto`), its margins, alignment and limits.
6. Text: `create_text`, then `set_text_style` for the whole layer or `set_text_range_style` for characters `start`..`end`.
7. Check the result with `export_shape` (group `export`) on the board.

## Library and styles

- `create_library_color`, `create_library_typography` add styles; `apply_library_color` and `apply_typography` link shapes to them.
- Typography fonts must exist in Penpot: bundled fonts, the team's fonts from `list_fonts`, or Google Fonts when the Google Fonts provider is enabled in Penpot (the flag `disable-google-fonts-provider` turns it off).

Components and variants: [references/components.md](references/components.md).
Design tokens and themes: [references/tokens.md](references/tokens.md).

## Penpot behaviour to expect

- Colors come back lowercase.
- `switch_variant` and `swap_component` replace the copy: the returned shape id can differ from the one passed.
- `set_flip` sets the mirrored state; it is not a toggle.
- Size limits set with `set_layout_child` cannot be removed.
- `export_shape` scales an image down so its longer side fits `max_size` (default 768, at most 1568); ask for more only when small details matter.
- A read whose answer would be longer than 30,000 characters returns a short version and `full_result`. In Claude Code, run its `download` curl command with the MCP server URL without its query string in place of `<MCP address>`, unzip the archive and read only the parts the task needs with a script instead of loading the whole file. Other clients give the command to the user. Narrowing the call, for example with `depth`, `root_id`, `query` or `sections`, avoids the download.
- Penpot rejects editing or deleting comments written by another account.
