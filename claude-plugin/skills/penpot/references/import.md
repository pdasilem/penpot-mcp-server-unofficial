# Importing HTML designs

1. Enable the group: `set_toolset` with `name` `import`, `enabled` true.
2. Upload the file with Bash, never by reading it into the conversation:

   ```bash
   curl --data-binary @design.html "$PENPOT_MCP_URL?userToken=$PENPOT_MCP_KEY&upload=html"
   ```

   The answer holds `upload_id`.
3. Find the frame and section selectors in the HTML, e.g. `grep -o 'class="[^"]*"' design.html | sort | uniq -c | sort -rn | head`. A frame is the element of one screen (e.g. `.desk`); a section heading is the element before a group of screens (e.g. `h2`).
4. `import_html` with `file_id`, `upload_id`, `frame_selector` and, for designs split into sections, `section_selector`.
5. Poll `get_import_status` until `done`, `failed` or `cancelled`. The import runs on the server frame by frame; there is nothing to do between polls.
6. On `failed`, read `error` and `failed_frame`; after fixing the cause (for example opening the file in the editor) call `resume_import`.

- The file must stay open in the editor while the import runs.
- Scripts in the HTML are ignored; only the static markup is imported.
- `unsupported` lists CSS the import skipped with counts; `substituted_fonts` lists font families replaced by `font_family`.
- Each section goes to a new page named after its heading; frames are laid out in rows on their page.
