1.5.0
---
- Quality stacks: one slot holds one stack's worth of an item across every tier, so quality no longer splits a stack of carrots into a slot per tier. Right-click an item onto the same item of another quality to make one; right-click items onto it to add them; right-click it with an empty cursor to take a tier out; carry it and right-click a slot to take that slot's items in, or an empty slot to put a tier down. Left-click stays vanilla. It turns back into a normal stack when one tier is left.
- Hover a quality stack and scroll to choose which tier comes out next (the best by default); the tooltip shows each tier as an icon with its count and highlights the choice.
- Quality stacks look like the item they hold, with the total count and a bar along the top split by tier colour.
- Items you pick up go into a quality stack you carry first; anything that does not fit is picked up normally.
- A quality stack destroyed by fire, lava or a cactus spills its contents. Only stackable, undamageable items can be pooled, and only when everything but quality matches.
- Tooltips can show quality as stars: client config `qualityTooltipStyle` is BOTH (tier name then stars, the default), SYMBOLS (stars only) or TEXT (name only). Stars go up by half a star per level: crude ½, standard 1, fine 1½ up to masterwork 3, in the tier's colour.
- New server config `qualityStacks` (default on) turns off making quality stacks and auto-pickup; existing quality stacks can still be emptied.

1.4.0
---
- Name sets: the item data map `quality_api:name_set` gives items their own words for each tier (food reads Poor, Common, Fine, Choice, Prime, Gourmet). New `Qualities.displayName(id, nameSet)`, `Qualities.displayName(stack, id)` and `Qualities.nameSet(stack)`; tooltips and `/quality set` use the item's set.
- `Qualities.forLevel(int)` returns a tier id for a level without needing a stack.
- Food effects: `nutrition_multiplier`, `saturation_multiplier`, `eat_effect_duration_multiplier` and `eat_effects` work in tier files and `item_qualities` patches. Tooltips list them, AppleSkin's preview matches, and `QualityFoodEatenEvent` fires when a player eats a quality food. Shipped tiers stay neutral for food.
- New `Qualities.applyFoodEffects(stack, food)`.
- Entity quality: the synced, saved `quality_api:quality` attachment holds a tier id on any entity. New `Qualities.getId`, `get`, `level`, `apply`, `remove`, `nameSet` and `displayName` overloads for entities, and an entity type data map `quality_api:name_set` (`animal` for the `#quality_api:animals` tag, `monster` for the `#quality_api:monsters` tag, `townsfolk` for the `#quality_api:townsfolk` tag, `player` for players; add modded mobs to the tags). No inheritance; callers decide that.
- Entity attributes: entity type data map `quality_api:entity_qualities` gives entities attribute modifiers per tier. They are recomputed on apply, remove, load and reload, never saved, and max health changes keep the health percentage. Tier files do not apply to entities.
- Entity quality survives player death and mob conversions; a converted mob uses its new type's attributes. Config `keepPlayerQualityOnDeath` (default true) clears a player's quality on death when off.
- Loot function `quality_api:copy_entity_quality` (optional `level_offset`, fraction rolled) gives drops the dropping entity's quality, and loot condition `quality_api:entity_min_quality` (`min_level`) gates drops on it.
- `QualityLootEvent` fires whenever `copy_entity_quality` decides a drop's tier, with the killing player, the entity, its level and the proposed tier; `setQuality` changes the result, so a skill mod can decide drop quality. It fires for entities without quality too.
- `/quality entity <targets> set <tier>` and `/quality entity <targets> remove`. Set messages show the tier id next to the display word, e.g. "Set Steve to Legendary (quality_api:masterwork)".
- `Qualities.forLevel(double, random)` rolls the fraction without a stack.

1.3.3
---
- Add silverlib requirement.
