# Design tokens and themes

## Catalog

- `get_design_tokens` lists sets with their tokens and themes with their sets.
- `create_token_set`, `set_token_set_active`, `delete_token_set` manage sets; names use `/` for groups, e.g. `mode/dark`.
- `create_token`, `update_token`, `delete_token` manage tokens. A referenced token must exist before the token that references it, e.g. create `space.1` before `space.2` with value `{space.1} * 2`.
- `create_token_theme` with `group` and `name`; `set_theme_sets` sets its sets; `set_token_theme_active` activates it. One theme per group is active.

## Values by type

| Type | Value |
|---|---|
| `color` | `#3366FF` or a reference |
| `dimension`, `sizing`, `spacing`, `borderRadius`, `borderWidth`, `fontSizes`, `letterSpacing`, `rotation`, `number`, `opacity` | Number as a string, a reference or an expression such as `{space.1} * 2` |
| `fontFamilies` | List of family names |
| `fontWeights` | Weight such as `700` |
| `textCase` | `uppercase`, `lowercase`, `capitalize`, `none` |
| `textDecoration` | `underline`, `line-through`, `none` |
| `typography` | Object with `fontFamilies`, `fontSizes`, `fontWeight`, `lineHeight`, `letterSpacing`, `textCase`, `textDecoration` |
| `shadow` | List of objects with `color`, `offsetX`, `offsetY`, `blur`, `spread`, `inset` |

## Binding tokens to shapes

- `set_token` without `attr` binds the attributes Penpot uses for the token type: color binds `fill`, borderRadius every corner, sizing and dimensions `width` and `height`, spacing the gaps of a layout board or the margins of a layout child.
- `attr` binds one attribute, e.g. `strokeColor` for a color token. A wrong attribute is rejected with the list of allowed ones.
- `remove_token` takes `token_id` to unbind a token everywhere on the shape, or `attr` to unbind one attribute.
- Groups take no tokens; bind tokens to the shapes inside.

## Auditing token usage

- `token_usage` (group `read`) reads every page of the file once and returns `unused` tokens, `missing` token names that shapes still apply, `referenced_only` tokens, `references` between tokens, `usage` per token, and `raw_values`: plain numbers and colors grouped by top-level board and shape.
- A raw value with `matches` has the same value as those tokens of the default theme combination; one with `off_scale` matches no token. `summary.unresolved_tokens` lists tokens whose value could not be computed, so they are absent from `matches`.
- Component copies are not checked; values overridden on a copy are not reported.
- `sections` returns only the named parts besides the summary. `page_id` narrows `raw_values` to one page; page through `raw_values` with `limit` and `cursor`.

## Export as code

- `export_design_system` (group `export`, file open in the editor) writes the tokens of every theme combination with the library colors and typographies as `css`, `scss`, `tailwind`, `typescript`, `dtcg`, `kotlin` or `swiftui`.
- `kotlin` needs `options.package`; `kotlin` and `swiftui` take `options.type_name`, from the file name by default. `css`, `scss` and `tailwind` take `options.prefix`; `options.color_scheme_group` makes the light and dark themes of that group follow the system color scheme.
- Give the user the `download` curl command with the MCP server URL without its query string in place of `<MCP address>`. The download works once and for an hour.
- `problems.json` in the archive lists what was left out (`severity` `error`) and what to know (`warning`), each with `code`, `subject` and the theme `combinations` it applies to.
