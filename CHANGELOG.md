1.7.0
---
- Traits: named flags that other mods and datapacks can put on items and entities. QualityAPI stores, syncs and shows them; the mod or pack that adds a trait decides what it does.
  - Define a trait with a datapack file at `data/<namespace>/quality_api/trait/<name>.json`, e.g. `{ "color": "#FFAA00" }`, and name it with the lang key `trait.<namespace>.<name>`.
  - Items keep them in the `quality_api:traits` component, e.g. `/give @s minecraft:carrot[quality_api:traits=["mypack:golden"]]`. Entities keep them through death and conversion.
  - Tooltips show them on one line, "Traits: Golden, Fiery", four per line. Quality stacks list the traits of what they hold.
  - Items with the same traits stack and pool into quality stacks across tiers; items with different traits stay apart.
  - Loot function `quality_api:add_trait` (`trait`), pair it with `random_chance` for rare drops.
  - Mods use `Traits` (`add`, `remove`, `has`, `getIds`, `displayName`, `tooltipLines`, for stacks and entities). Crafting, breeding and loot carry no traits over on their own; the owning mod decides that.
- Picking up an item that differs only in quality from a stack in your inventory now makes a quality stack of the two, so mixed-quality crops collect into one slot on their own. Server config `createQualityStacksOnPickup` (default on). It never touches the item in your hand or offhand, and a stack of the same quality with room still takes the pickup normally.
