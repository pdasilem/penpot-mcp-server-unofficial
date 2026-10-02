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
