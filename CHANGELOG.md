1.3.0
---
- Require SilverLib 1.2.0; broken-instead-of-destroyed now runs on SilverLib's shared breakage feature, so other mods can keep items broken too
- QualityItemBrokenEvent now fires for any quality item kept broken, including items another mod kept
- Items broken before this version are converted on login (player inventory) or the next time they take damage, without a second destroy or downgrade roll
- Turning brokenInsteadOfDestroyed off no longer releases items that are already broken; they stay broken until repaired
- Loosen the NeoForge requirement to 21.1.228 or newer