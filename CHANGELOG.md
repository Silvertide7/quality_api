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
- `/quality entity <targets> set <tier>` and `/quality entity <targets> remove`. Set messages show the tier id next to the display word, e.g. "Set Steve to Legendary (quality_api:masterwork)".
- `Qualities.forLevel(double, random)` rolls the fraction without a stack.

1.3.3
---
- Add silverlib requirement.
