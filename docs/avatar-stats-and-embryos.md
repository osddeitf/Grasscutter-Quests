# Avatar stats and ability embryos

How an avatar's fight properties and its ability embryo list are computed and
sent to the client. Written as a basis for a redesign, so it describes current
behavior, including the parts that are known to be wrong.

## 1. Pieces

| Class | Owned by | Holds | Persisted |
|---|---|---|---|
| `Avatar` | `AvatarStorage` | level, promote level, skill depot id, skill levels, constellations (`talentIdList`), `fightProperties`, `fightPropOverrides` | yes (the listed fields; `fightProperties` is transient) |
| `AvatarStatsManager` (`avatar.stats`) | `Avatar` | partial stat maps (base, weapon, reliquary, constellation) and the *sources* that grant abilities (weapon affixes, reliquary set affixes, proud skills, constellations) | no |
| `AvatarAbilityManager` (`avatar.abilities`) | `Avatar` | the avatar's embryo list, split by source type | no |
| `TeamAbilityManager` | `EntityTeam` | the team entity's embryo list | no |
| `AbilityEmbryoList` | each ability manager | ordered `hash → AbilityEmbryo`, plus the embryo id counter | no |

Both managers on `Avatar` are created in `Avatar.setAvatarData()`, so they
exist once the avatar has its `AvatarData`. The ability manager is recreated
when the avatar joins the team (`Avatar.resetAbilities()`, called from
`TeamManager.updateTeamEntities()`), so embryo numbering starts over on every
join. Entities rebuilt on scene entry (including same-scene teleports) are
recognized as existing team members, so those don't reset it.

The skill depot is not cached. `getSkillDepot()` looks it up from
`skillDepotId` on every call.

## 2. Lifecycle

**New avatar** (`new Avatar(AvatarData)`):
1. Fight properties 1–2999 are set to `0`.
2. `setSkillDepotData(data.getSkillDepotId())` sets the depot id and fills
   missing skill levels with `1`.
3. `setAvatarData(data)` creates the stats manager, which immediately computes
   its partial maps (no equipment yet), and the ability manager, which adds the
   `DEFAULT` embryos.
4. `recalcStats()` runs the full pipeline (below).
5. `currentHp` = max HP, `currentEnergy` = 0.

**Loaded from the database** (`AvatarStorage.loadFromDatabase`):
1. Morphia builds the object; all transient fields are empty.
2. `setOwner(player)`, then `setAvatarData(data)` creates both managers.
   Equipment isn't attached yet, so the first stats computation sees none.
3. `recalcConstellations()` fills skill bonus levels and extra charges.
4. Later, `postLoad()` gives a starting weapon if missing, then `recalcStats()`.

`TowerAvatar` and `TrialAvatar` go through the `Avatar(AvatarData)`
constructor and recalculate after their equipment is set.

## 3. Stats pipeline (`AvatarStatsManager`)

Four `calculate*` steps each rebuild one partial result; `refreshStats()`
combines them into `avatar.fightProperties`.

| Step | Produces (stats) | Produces (ability sources) |
|---|---|---|
| `calculateLevelBaseStats` | base HP/ATK/DEF by level, base crit, crit DMG, 100% energy recharge, promotion props, props of unlocked inherent proud skills | `proudSkills`: inherent passives whose `needAvatarPromoteLevel` ≤ promote level (`groupId * 100 + 1`) |
| `calculateWeaponStats` | weapon curve stats, weapon promotion props, refinement affix props | `weaponAffixes` |
| `calculateReliquaryStats` | main stat, sub stats, set bonus props for the 5 artifact slots | `reliquaryAffixes`: one per reached set threshold |
| `calculateTalentStats` | constellation `addProps` | `talents`: unlocked constellations of the current depot |

`refreshStats()`:
1. Remembers the HP percentage and the current energy.
2. Sums the base, weapon, reliquary and constellation maps. The only
   constellation `addProps` in the resources is `FIGHT_PROP_CHARGE_EFFICIENCY`
   (Anemo Traveler C2), so a plain sum is enough for now.
3. Computes compound properties (`flat + base * (1 + percent)`).
4. Replaces `fightProperties` with the result.
5. Calls `setCurrentEnergy(...)`, applies `fightPropOverrides`, then restores
   HP by percentage.

**Constellation skill bonuses and extra charges** are kept on `Avatar`
(`proudSkillBonusMap`, `skillExtraChargeMap`). `recalcConstellations()`
rebuilds them from `stats.getTalents()` on every `recalcStats()`, reading the
open configs' `extraTalentIndex` (1 = Normal Attack, 2 = Elemental Skill,
9 = Burst, 0 = none, anything else is logged) and `skillPointModifiers`.
`unlockConstellation()` additionally calls `calcConstellation(entry, true)` to
send the level/charge notifies for the newly unlocked constellation.

## 4. `recalcStats()` is the single entry point

Every change calls the whole thing; there is no partial update (the code has a
TODO about this).

```
recalcStats(force)                 // `force` is ignored
├─ fetters, name card from AvatarData
├─ stats.calculateLevelBaseStats()
├─ stats.calculateWeaponStats()
├─ stats.calculateReliquaryStats()
├─ stats.calculateTalentStats()
├─ stats.refreshStats()
├─ recalcConstellations()          // skill bonus levels, extra charges
├─ updateAvatarAbilities()
│  ├─ abilities.attachOpenConfigs("weapon",      stats.weaponAffixes)
│  ├─ abilities.attachOpenConfigs("reliquary",   stats.reliquaryAffixes)
│  ├─ abilities.attachOpenConfigs("proud_skill", stats.proudSkills)
│  ├─ abilities.attachOpenConfigs("talent",      stats.talents)
│  └─ abilities.updateSkillDepotAbilities()
├─ abilities.notifyChanges()       // AbilityChangeNotify if embryos changed
└─ AvatarFightPropUpdateNotify     // if login packets were sent
```

Callers: equip or unequip (including the avatar that lost the item),
level-up, promotion, weapon and artifact upgrades, constellation unlock or
reset, skill-depot/element change, adding an avatar or trial avatar, tower
equipment, login `postLoad`, and the `give`, `setconst` and `setstats`
commands.

`changeSkillDepot()` runs `calculateLevelBaseStats()` and `refreshStats()`
before sending `AvatarSkillDepotChangeNotify`, so the notify lists the new
depot's passives and still goes out before the `AbilityChangeNotify` from
`recalcStats()`.

## 5. Embryo model (`BaseAbilityManager`, `AbilityEmbryoList`)

- Each manager keeps `Map<AbilityType, IntSet>`: the ability hashes each source
  type currently contributes.
- `updateAbilityEmbryos(type, newSet)` diffs against the previous set for that
  type. It removes hashes that disappeared and adds new ones to
  `AbilityEmbryoList`. If anything changed, it sets `abilityChanged`.
- `AbilityEmbryoList` is an insertion-ordered map `hash → AbilityEmbryo`.
  - A new hash gets `++counter` as its embryo id. Ids are never reused within a
    manager, and a hash that is removed and re-added gets a new id at the end
    of the list.
  - Hashes are shared across types: adding an existing hash is a no-op, but
    removing a hash from one type removes it from the list even if another
    type still has it (no reference counting).
- `toProto()` returns the list in insertion order (`AbilityControlBlock`).
- `notifyChanges()` clears `abilityChanged`. For an avatar, it sends
  `AbilityChangeNotify` only if the avatar currently has an entity in the
  active team; otherwise the change is just dropped from notification. The
  full list goes out with the next `SceneTeamUpdateNotify`.

Embryos are not the server's instanced abilities. The client instantiates
abilities from embryos and reports them through ability invokes
(`ADD_NEW_ABILITY`), which `AbilityManager` stores per entity by the client's
instanced id.

## 6. Avatar embryo sources

Team-scoped sources are applied by `TeamManager.updateTeamAvatarAbilities()` to
every avatar in the active team, trial avatars included. It runs on every team
update (`updateTeamEntities()`), on login, and on scene init
(`onSceneInit()`, without change notify, since the scene init packets carry the
full lists).

| Type | Contents | Updated by | Scope |
|---|---|---|---|
| `DEFAULT` | `GameConstants.DEFAULT_ABILITY_HASHES` + the avatar's `ConfigAvatar` embryo list (`AbilityEmbryoInfo`) | constructor of the manager | per avatar |
| `SKILL_DEPOT` | abilities of the depot's `skillDepotAbilityGroup` | every `recalcStats()`; not cleared if the new depot has no group | per avatar |
| `OPEN_CONFIG` | union of `AddAbility` names from the open configs of 5 named sources: `weapon`, `reliquary`, `proud_skill`, `talent` (from `recalcStats`) and `resonance` (team-scoped). A missing open config entry falls back to hashing the config name itself | `attachOpenConfigs(source, set)`: diffs the source's set, then rebuilds the union | per avatar / team (`resonance`) |
| `WORLD_AREA_LEVEL_1` / `_2` | avatar ability groups of the current world area, remembered in `TeamManager` | `TeamManager.enterWorldArea()` (from `HandlerEnterWorldAreaReq`) + team refresh | team |
| `LEVEL_ENTITY` | `avatarAbilities` of the scene's level entity config; cleared if the scene has none | team refresh (incl. scene init) | team |
| `DYNAMIC` | avatar abilities of all groups registered in `AbilityGroupManager` (widgets, Lua regions, ...) | `AbilityGroupManager.commit()` (notifies only once the scene is loaded) + team refresh | team |
| `RESONANCE_MOON_SIGN`, `GALLERY`, `SCRIPT_LUA` | always empty (placeholders, never called) | — | — |

Open config details:
- Only `AddAbility` entries are used by the ability manager. `ModifyAbility`
  (resonance and constellation parameter changes) is TODO; extra levels and
  skill points are handled by `Avatar.recalcConstellations()` instead.
- `OpenConfigEntry` reads `$type` into an enum. `ModifySkillCD` and
  `UnlockControllerConditions` are recognized but not handled; entries with a
  still-unknown type are dropped (the rest of the entry and file still load).

## 7. Team embryo sources (`TeamAbilityManager`)

| Type | Contents | Updated by |
|---|---|---|
| `DEFAULT` | `ConfigGlobalCombat` default team abilities | constructor |
| `WORLD_AREA_LEVEL_1` / `_2` | team ability groups of the world area | `TeamManager.enterWorldArea()` |
| `LEVEL_ENTITY` | `teamAbilities` of the level entity config; cleared if the scene has none | `TeamManager.onSceneInit()` |
| `DYNAMIC` | team abilities of registered ability groups | `AbilityGroupManager.commit()` |

The team's list goes out in `PlayerEnterSceneInfoNotify`; changes go out as
`AbilityChangeNotify(team)`.

## 8. Team resonance

`TeamManager.updateTeamResonances()` runs on every team entity update and on
login:
1. Every `TeamResonanceExcelConfigData` entry is checked with
   `isResonanceActive(teamAvatars)`, where `teamAvatars` are the avatars of the
   active team in team order, trial avatars included. Only the first 4 count,
   and at least 4 are required. `ALL_DIFFERENT` requires no duplicate elements.
2. If the active set changed, `TeamResonanceChangeNotify` is sent so the client
   can set up its override maps.
3. The set is attached to every team avatar as the `resonance` open config
   source by the team refresh (§6).

## 9. Known problems

| Problem | Where |
|---|---|
| No reference counting: a hash granted by two sources disappears when one of them goes away (sources are expected not to overlap) | `AbilityEmbryoList`, `openConfigAbilities` |
| Element switching: the depot change notify still carries the previous depot's constellation data, and `skillLevelMap` is shared between depots | `Avatar.changeSkillDepot` |
| Remembered world areas aren't reset on scene change; they're replaced when the client reports a new area | `TeamManager` |
| Scene entry rebuilds the team entities from `getAvatarById`, which drops trial and tower avatars and skips `updateTeamEntities()`; when they're added back later, they count as joining and their numbering resets | `Scene.setupPlayerAvatars` |
| Scenes with specified avatars swap the team from outside `TeamManager` (`activeTeam` is cleared and refilled) | `Player.setAvatarsAbilityForScene` |
| Co-op resonance only counts the player's own avatars | `TeamManager.updateTeamResonances` |
| A missing open config entry is added as an ability by hashing its name, for every source type | `AvatarAbilityManager.getAddAbilityNames` |
| `SKILL_DEPOT` embryos aren't cleared if the new depot has no ability group | `AvatarAbilityManager.updateSkillDepotAbilities` |
| `recalcStats(forceSendAbilityChange)` ignores its parameter | `Avatar` |

## 10. Questions for the redesign

- **Update granularity.** Should each trigger (equip, level, constellation,
  depot) recalculate only its part, with one explicit "flush" that sends
  packets? The partial maps already allow it.
- **A central source for team-scoped abilities.** A per-player
  `AbilityContext` holding the scene (level entity), world areas, ability
  groups and resonance, which team members pull from (e.g. with a revision
  counter). World area, level entity and dynamic groups are per player, so this
  also works when co-op players are in different scenes; resonance would be set
  from the world/co-op team.
- **Embryo ownership.** Embryos only matter while the avatar is in the team.
  Owning them per team membership (a member object in `TeamManager`) instead of
  on `Avatar` would make the join/leave reset structural, while staying
  independent of how scene changes and teleports recreate entities.
- **Reference counting.** Count `hash → number of sources` in
  `AbilityEmbryoList`, if overlapping sources turn out to exist.
- **Open config consumer.** One place that turns an open config into all its
  effects: added abilities, `ModifyAbility` parameters, skill bonus levels,
  extra charges.
- **Embryo ids and order.** Does the client care whether embryo ids restart per
  entity, and about list order? Today, ids continue across teleports and a
  re-added hash moves to the end with a new id.
- **Missing resources.** Keep the "hash the config name" fallback, or log and
  skip?
