1.3.2
---
- Require SilverLib 1.2.1
- Only items at exactly max damage are treated as broken before 1.3.0; items pushed over max by a durability change break normally
- Tier-granted Mending no longer applies to broken items
- An already-broken item reaching the break again is kept without a second roll

1.3.1
---
- Items broken before 1.3.0 are also converted when equipped, never re-roll destroy or downgrade, and no longer post QualityItemBrokenEvent when converted
- Fix login conversion destroying items whose tier was removed from the pack
- Integration docs: SilverLib repository and compileOnly setup
