# Components and variants

## Components

- `create_component` turns shapes into a component of the file's library; the shapes become its main instance.
- `create_component_instance` places a copy. Pass `library_file_id` for a component of a connected shared library (`get_file_libraries`).
- `list_components` lists the file's components; `get_component_instances` lists copies.
- On a copy: `swap_component` replaces it with another component, `reset_overrides` restores the main component's values, `detach_instance` turns it into plain shapes.

## Variants

1. Create one component per variant, each from a board.
2. `create_variants` with two or more component ids combines them into a variant set. Their main instances must be on the same page.
3. `set_variant_property` sets a value on one variant. A property the set does not have yet is added to the whole set. Edits of variant properties keep the overrides of existing copies.
4. `rename_variant_property` and `remove_variant_property` change the set's properties.
5. `switch_variant` on a copy picks the variant with the given property value.

Two variants with the same combination of values are reported with Penpot's error in the returned variant set.
