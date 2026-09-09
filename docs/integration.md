# Integrating a custom crafting station or minigame with Quality API

This guide is for mod developers who have their own crafting block, minigame, or progression system and want the items it produces to carry Quality API tiers. It covers the dependency setup, the decision flow at the moment an item is made, the data you should ship, and the mistakes that are easy to make.

Quality API targets NeoForge 1.21.1. All public API lives in `net.silvertide.quality_api.api`.

## 1. Dependency

`build.gradle`:

```groovy
dependencies {
    compileOnly "net.silvertide.quality_api:quality_api:${quality_api_version}"
    localRuntime "net.silvertide.quality_api:quality_api:${quality_api_version}"
}
```

Use `implementation` instead if your mod cannot function without it. In `neoforge.mods.toml`:

```toml
[[dependencies.yourmod]]
modId = "quality_api"
type = "required"      # or "optional" if you bridge (see section 8)
versionRange = "[1.0.0,)"
ordering = "NONE"
side = "BOTH"
```

## 2. The model in one minute

- An item's quality is a single data component, `quality_api:quality`, holding a tier id such as `quality_api:masterwork`. Nothing else is stored on the stack.
- Tiers are a datapack registry. Each has an integer `level` (0 is the silent baseline, negative is worse), a colour, a roll `weight`, an optional `items` filter, and default `effects`.
- Effects (durability multiplier, mining speed, attribute modifiers, bonus enchantment levels, break behaviour) are computed live from the tier every time they are needed. You never bake anything onto the item.
- Per-item and per-tag patches to those effects live in a NeoForge data map. You can ship entries for your own items.
- A quality item at zero durability becomes broken instead of vanishing, and may drop a tier. You get this for free.

Work in **levels**, not tier ids. Packs rename, remove, and add tiers. Your code should compute an integer or fractional level and let the API pick the tier.

## 3. Deciding the tier in your station

Your station knows three things the API does not: what the player put in, how well they performed, and what your recipe demands. Combine them into a level, then ask the API for the tier. Do this once, at the moment the item is actually produced, never for a preview.

```java
ItemStack result = recipe.assemble(inputs, level.registryAccess());

OptionalDouble ingredientMean = Qualities.meanLevel(inputStacks);
double base = ingredientMean.orElse(0.0);
double offset = scoreToLevelOffset(minigameScore);
double target = clampToRecipe(base + offset, recipe);

Optional<ResourceLocation> tier = Qualities.forLevel(result, target, level.getRandom());
```

- `meanLevel` averages the levels of the stacks that carry quality and skips plain ones. It is empty when no input has quality, so `orElse(0.0)` treats plain materials as baseline.
- `forLevel(stack, double, random)` floors the level and rolls the fraction. A target of 2.25 gives level 2 three quarters of the time and level 3 one quarter. Then it picks the nearest tier the item can take. Use the `forLevel(stack, int)` overload if you already have a whole number.
- The mapping from your score to an offset is yours. A common shape is `(score - 0.5) * 2.0`, so a perfect run adds one level and a failed run removes one. Threshold-based minigames can instead compute an integer level directly and skip the fraction.
- Clamp with plain arithmetic: `Math.max(target, recipeMinimumLevel)`, `Math.min(target, highestIngredientLevel)`. Get an ingredient's level with `Qualities.level(stack)`, which is 0 for plain items.

If your station has no ingredients with quality and no minigame, use `Qualities.roll(result, random)` for a weighted random tier, or apply a fixed level with `forLevel`.

## 4. Let other mods have a say

Post `QualityCraftEvent` before applying the tier. This is the same event the vanilla crafting table posts, so a skills mod or compat mod that adjusts crafting outcomes works on your station without knowing it exists.

```java
CraftingInput input = CraftingInput.of(width, height, inputStacks);
QualityCraftEvent event = new QualityCraftEvent(serverPlayer, recipeHolder, input, result, ingredientMean, tier.orElse(null));
NeoForge.EVENT_BUS.post(event);
ResourceLocation chosen = event.getQuality();
```

- `serverPlayer` must not be null. If your station can run without a player, skip the event and use your own tier.
- `recipeHolder` may be null. Passing your own recipe type lets listeners recognise your station.
- `CraftingInput.of` trims empty rows and columns, so listeners read `input.items()` rather than assuming grid positions.
- `chosen` is null when a listener decided the item should have no quality. Respect it.

## 5. Apply the tier and record the crafter

```java
if (chosen != null && Qualities.apply(result, chosen)) {
    result.set(QualityAPI.CRAFTER.get(), serverPlayer.getGameProfile().getName());
}
```

- `apply` returns false when the item is blacklisted or the tier's `items` filter excludes it. Treat that as "no quality", not as an error.
- `apply` rescales any existing damage to the new maximum, so applying to a used item is safe.
- The crafter component is optional provenance shown as "Crafted by" in the tooltip. Set it only when a real player made the item. Respect `Config.RECORD_CRAFTER` if you want to honour the user's setting.
- Do not set `quality_api:quality` directly with `stack.set`. It works, but it bypasses the blacklist, the item filter, and the damage rescale.

## 6. Reading quality elsewhere in your mod

- `Qualities.level(stack)` is the integer level, 0 when the item has none. Most gameplay code needs nothing else.
- `Qualities.getId(stack)` and `Qualities.get(stack)` give the tier id or the full tier definition when you need colour or weight.
- `Qualities.isBroken(stack)` tells you the item is at zero durability and unusable.
- `Qualities.shift(stack, id, steps)` moves up or down to the next tier the item can take. `Qualities.isHighestLevel(stack, id)` tells you there is nothing above for that item.
- `Qualities.idsByLevel()` lists all loaded tiers in ascending level order if you need to iterate.

All of these return empty or 0 before the first world load. Never call them from a static initialiser, and never call `QualityAPI.QUALITY.get()` before registries exist.

## 7. Ship your data

**Tiers.** Either rely on the six shipped tiers (crude -1, standard 0, fine 1, superior 2, exquisite 3, masterwork 4) or ship your own in `data/<yourmod>/quality_api/quality/<name>.json`. If your mod is built around its own ladder, ship the full set and tell pack makers to disable the shipped ones.

**Effects for your items.** Add a data map file at `data/quality_api/data_maps/item/item_qualities.json` in your jar. That path is fixed and every mod's file merges with the others. Entries are patches: name only the fields you want to change for that tier.

```json
{
  "values": {
    "#yourmod:forged_weapons": {
      "quality_api:masterwork": {
        "attributes": [ { "type": "minecraft:generic.attack_damage", "operation": "add_value", "amount": 3.0 } ]
      }
    },
    "yourmod:tool_head": {
      "quality_api:crude": { "break_destroy_chance": 1.0 }
    }
  }
}
```

Item entries beat tag entries. Remove an inherited effect by setting its neutral value: an empty list, an empty map, or a multiplier of 1.0.

**Non-damageable carriers.** Ingots, gems, and tool heads can hold quality without gaining durability. Nothing special is required. Quality on stackables only makes them stack separately from plain ones.

**Recipes.** A recipe result with a `quality_api:quality` component in its `components` is kept as-is by the vanilla table. The `quality_api:min_quality` ingredient (`items`, `min_level`) accepts an item of at least a given level. NeoForge's `neoforge:components` ingredient matches an exact tier.

## 8. Soft dependency

If Quality API is optional for your mod, keep every reference to its classes inside a nested class that is only touched after checking the mod is loaded:

```java
public final class QualityCompat {
    public static int level(ItemStack stack) {
        return ModList.get().isLoaded("quality_api") ? Bridge.level(stack) : 0;
    }

    private static final class Bridge {
        static int level(ItemStack stack) {
            return Qualities.level(stack);
        }
    }
}
```

The API has no class-initialisation side effects, so this pattern is safe.

## 9. Listening instead of owning

If you do not have a station and want to influence the vanilla crafting table, listen to `QualityCraftEvent` and call `setQuality`. Two rules:

- The event fires every time the result preview is recomputed, with the proposed tier. It decides the outcome, it is not a "player crafted something" signal. Award experience or statistics from NeoForge's `ItemCraftedEvent` instead, which fires once per real craft and sees the finished stack.
- Never modify the result stack inside `ItemCraftedEvent`. On shift-click it fires after the item has already moved into the inventory.

## 10. Checklist

- Compute a level from ingredients and performance. Do not hardcode tier ids.
- Decide the tier once, when the item is produced.
- Post `QualityCraftEvent` with a real `ServerPlayer`, then honour `getQuality()`.
- Use `Qualities.apply`, check its return value, and set the crafter only for real players.
- Ship a data map for your items and, if needed, your own tier files.
- Read quality with `Qualities.level` and friends, never by parsing the component yourself.
- Keep everything behind a bridge class if the dependency is optional.
