(ns penpot.mcp.tools.components
  (:require
   [clojure.string :as str]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]
   [penpot.mcp.tools.create :as create]))

(def ^:private lookup
  (str/join
   "\n"
   ["const findComponent = (id, libraryId) => {"
    "  const libs = libraryId ? penpot.library.connected.filter((l) => l.id === libraryId) : [penpot.library.local];"
    "  if (!libs.length) fail('library-not-connected', libraryId);"
    "  const all = libs.flatMap((l) => l.components).flatMap((c) => (c.isVariant() && c.variants) ? c.variants.variantComponents() : [c]);"
    "  return all.find((c) => c.id === id) ?? fail('component-not-found', id);"
    "};"
    "const variantState = (v) => ({"
    "  id: v.id, properties: v.properties,"
    "  components: v.variantComponents().map((c) => ({ id: c.id, name: c.name, properties: c.variantProps, error: c.variantError || null }))"
    "});"
    "const instanceState = (s) => {"
    "  const c = s.component();"
    "  return { shape: info(s), componentId: c ? c.id : null, variantProperties: c && c.isVariant() ? c.variantProps : null };"
    "};"
    "const variantOf = (c) => (c.isVariant() && c.variants) ? c.variants : fail('not-a-variant', c.id);"
    "const propertyPos = (v, name) => { const pos = v.properties.indexOf(name); return pos >= 0 ? pos : fail('property-not-found', name + ' (properties: ' + v.properties.join(', ') + ')'); };"
    "const copyRoot = async (id) => {"
    "  const s = await focusShape(id);"
    "  if (!s.isComponentCopyInstance() || !s.isComponentRoot()) fail('not-a-copy', id);"
    "  return s;"
    "};"
    "const replacement = async (parent, index, oldId) => {"
    "  return (await waitFor(() => { const c = parent.children[index]; return c && c.id !== oldId ? c : (penpot.currentPage.getShapeById(oldId) && null); }))"
    "    ?? penpot.currentPage.getShapeById(oldId) ?? fail('shape-not-found', oldId);"
    "};"]))

(defn- body [& lines]
  (str/join "\n" (cons lookup lines)))

(def ^:private component-param
  [:component_id {:description "Component id from list_components"} :uuid])

(def ^:private library-param
  [:library_file_id {:optional true :description "Id of the connected shared library the component belongs to; omit for the file's own components"} :uuid])

(def ^:private property-name
  [:string {:min 1 :max 250}])

(def ^:private property-param
  [:property {:description "Variant property name"} property-name])

(def ^:private value-param
  [:value {:description "Variant property value"} property-name])

(defn- schema [& entries]
  (into [:map {:closed true} common/file-id-param] entries))

(def ^:private create-instance
  (canvas/plugin-tool
   {:name "create_component_instance"
    :description "Place a copy (instance) of a component on a page or inside a board or group. The component comes from the file's own library or, with library_file_id, from a connected shared library. Returns the copy's state and its component id."
    :annotations tool/additive
    :input-schema (apply schema component-param library-param
                         [:x {:description "Canvas X"} common/safe-number]
                         [:y {:description "Canvas Y"} common/safe-number]
                         create/placement-params)
    :body (body create/place
                "const c = findComponent(args.componentId, args.libraryId);"
                "const s = c.instance() ?? fail('create-failed', 'component instance');"
                "try {"
                "  if (args.name !== undefined) s.name = args.name;"
                "  if (parent) parent.appendChild(s);"
                "  s.x = args.x;"
                "  s.y = args.y;"
                "} catch (e) { s.remove(); throw e; }"
                "await settle();"
                "markChanged();"
                "return instanceState(s);")
    :args (fn [{:keys [component_id library_file_id] :as params}]
            (merge (create/shape-args params) (common/compact {:component-id component_id :library-id library_file_id})))
    :result-key nil}))

(def ^:private create-variants
  (canvas/plugin-tool
   {:name "create_variants"
    :description "Combine components of the file's own library into one variant set, as Penpot's \"Combine as variants\" does. Their main instances must be on the same page. Penpot derives the first properties from the component names. Returns the variant set: its id, property names and each variant component with its property values."
    :annotations tool/overwrite
    :input-schema (schema [:component_ids {:description "Ids of two or more components of this file"}
                           [:vector {:min 2} :uuid]])
    :body (body "const mains = [];"
                "for (const id of args.componentIds) {"
                "  const c = findComponent(id);"
                "  if (c.isVariant()) fail('already-variant', id);"
                "  const main = c.mainInstance() ?? fail('component-not-found', id);"
                "  mains.push(await focusShape(main.id));"
                "}"
                "for (const m of mains) if (!penpot.currentPage.getShapeById(m.id)) fail('mixed-pages', m.id);"
                "const container = penpot.createVariantFromComponents(mains) ?? fail('create-failed', 'variant set');"
                "markChanged();"
                "const v = await waitFor(() => container.variants);"
                "return v ? variantState(v) : fail('create-failed', 'variant set');")
    :args #(hash-map :component-ids (:component_ids %))
    :result-key :variants}))

(def ^:private set-variant-property
  (canvas/plugin-tool
   {:name "set_variant_property"
    :description "Set a property value of one variant component. A property the variant set does not have yet is added to the whole set first. Returns the variant set with each variant's property values; a variant whose combination of values clashes with another one shows Penpot's error."
    :annotations tool/overwrite
    :input-schema (schema component-param property-param value-param)
    :body (body "const c = findComponent(args.componentId);"
                "const v = variantOf(c);"
                "let pos = v.properties.indexOf(args.property);"
                "if (pos < 0) {"
                "  const count = v.properties.length;"
                "  v.addProperty();"
                "  markChanged();"
                "  if (!(await waitFor(() => v.properties.length > count))) fail('variant-not-updated', args.property);"
                "  pos = v.properties.length - 1;"
                "  v.renameProperty(pos, args.property);"
                "  if (!(await waitFor(() => v.properties[pos] === args.property))) fail('variant-not-updated', args.property);"
                "}"
                "if (c.variantProps[args.property] !== args.value) {"
                "  c.setVariantProperty(pos, args.value);"
                "  markChanged();"
                "  if (!(await waitFor(() => c.variantProps[args.property] === args.value))) fail('variant-not-updated', args.property);"
                "}"
                "return variantState(v);")
    :args #(hash-map :component-id (:component_id %) :property (:property %) :value (:value %))
    :result-key :variants}))

(def ^:private rename-variant-property
  (canvas/plugin-tool
   {:name "rename_variant_property"
    :description "Rename a property of the variant set a component belongs to; the values stay. Returns the variant set."
    :annotations tool/overwrite
    :input-schema (schema component-param property-param [:new_name {:description "New property name"} property-name])
    :body (body "const v = variantOf(findComponent(args.componentId));"
                "if (args.property !== args.newName) {"
                "  if (v.properties.includes(args.newName)) fail('property-exists', args.newName);"
                "  const pos = propertyPos(v, args.property);"
                "  v.renameProperty(pos, args.newName);"
                "  markChanged();"
                "  if (!(await waitFor(() => v.properties[pos] === args.newName))) fail('variant-not-updated', args.property);"
                "}"
                "return variantState(v);")
    :args #(hash-map :component-id (:component_id %) :property (:property %) :new-name (:new_name %))
    :result-key :variants}))

(def ^:private remove-variant-property
  (canvas/plugin-tool
   {:name "remove_variant_property"
    :description "Remove a property from the variant set a component belongs to, with its values in every variant. Returns the variant set."
    :annotations tool/overwrite
    :input-schema (schema component-param property-param)
    :body (body "const v = variantOf(findComponent(args.componentId));"
                "const pos = propertyPos(v, args.property);"
                "v.removeProperty(pos);"
                "markChanged();"
                "if (!(await waitFor(() => !v.properties.includes(args.property)))) fail('variant-not-updated', args.property);"
                "return variantState(v);")
    :args #(hash-map :component-id (:component_id %) :property (:property %))
    :result-key :variants}))

(def ^:private switch-variant
  (canvas/plugin-tool
   {:name "switch_variant"
    :description "Switch a component copy to the variant of its set that has the given property value, keeping its other property values where possible, as the variant selector in Penpot's design panel does. Penpot replaces the copy with a copy of the other variant, so the returned shape id can differ from the one passed. Returns the copy's state, component id and property values."
    :annotations tool/overwrite
    :input-schema (schema common/shape-id-param property-param value-param)
    :body (body "let s = await copyRoot(args.shapeId);"
                "const c = s.component() ?? fail('not-a-copy', args.shapeId);"
                "const v = variantOf(c);"
                "const pos = propertyPos(v, args.property);"
                "if (!v.currentValues(args.property).includes(args.value)) fail('value-not-found', args.value + ' (values: ' + v.currentValues(args.property).join(', ') + ')');"
                "if (c.variantProps[args.property] !== args.value) {"
                "  const parent = s.parent;"
                "  const index = s.parentIndex;"
                "  s.switchVariant(pos, args.value);"
                "  markChanged();"
                "  await settle();"
                "  s = await replacement(parent, index, args.shapeId);"
                "}"
                "return instanceState(s);")
    :args #(hash-map :shape-id (:shape_id %) :property (:property %) :value (:value %))
    :result-key nil}))

(def ^:private swap-component
  (canvas/plugin-tool
   {:name "swap_component"
    :description "Replace a component copy with a copy of another component, keeping overrides where possible, as Penpot's \"Swap component\" does. The new component comes from the file's own library or, with library_file_id, from a connected shared library. Penpot replaces the copy, so the returned shape id can differ from the one passed. Returns the new copy's state and component id."
    :annotations tool/overwrite
    :input-schema (schema common/shape-id-param component-param library-param)
    :body (body "let s = await copyRoot(args.shapeId);"
                "const target = findComponent(args.componentId, args.libraryId);"
                "const current = s.component();"
                "if (!current || current.id !== target.id) {"
                "  const parent = s.parent;"
                "  const index = s.parentIndex;"
                "  s.swapComponent(target);"
                "  markChanged();"
                "  await settle();"
                "  s = await replacement(parent, index, args.shapeId);"
                "}"
                "return instanceState(s);")
    :args #(common/compact {:shape-id (:shape_id %) :component-id (:component_id %) :library-id (:library_file_id %)})
    :result-key nil}))

(def ^:private detach-instance
  (canvas/plugin-tool
   {:name "detach_instance"
    :description "Detach a component copy from its component, turning it into ordinary shapes that no longer follow the component. Returns the shape's state."
    :annotations tool/overwrite
    :input-schema (schema common/shape-id-param)
    :body (body "const s = await copyRoot(args.shapeId);"
                "s.detach();"
                "markChanged();"
                "if (!(await waitFor(() => !s.isComponentInstance()))) fail('not-detached', args.shapeId);"
                "return instanceState(s);")
    :args #(hash-map :shape-id (:shape_id %))
    :result-key nil}))

(def ^:private reset-overrides
  (canvas/plugin-tool
   {:name "reset_overrides"
    :description "Reset every override of a component copy and its children, restoring the values of the main component. Returns the copy's state."
    :annotations tool/overwrite
    :input-schema (schema common/shape-id-param)
    :body (body "const s = await copyRoot(args.shapeId);"
                "const before = treeFingerprint(s);"
                "s.resetOverrides();"
                "await waitFor(() => treeFingerprint(s) !== before);"
                "if (treeFingerprint(s) !== before) markChanged();"
                "return instanceState(s);")
    :args #(hash-map :shape-id (:shape_id %))
    :result-key nil}))

(def tools
  [create-instance create-variants set-variant-property rename-variant-property remove-variant-property
   switch-variant swap-component detach-instance reset-overrides])
