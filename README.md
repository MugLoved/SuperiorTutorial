# SuperiorTutorial

The optional first cave of Superior (Forge 1.20.1 / 47.4.20). An addon to SuperiorStory: it adds its own content and extends Story only through Story's public module registry. Story itself is never edited.

The player starts in a small, old cave that was never cleared. The Decaying Corpse, held to life against his will, sits against the wall. He wants the player to end him, and his hands are too weak to do it himself. He hands over a satchel with just enough for a first weapon and asks to be killed with it. Class choice is not part of the tutorial; it happens afterwards in Aho's skill tree. Nothing is forced: a player can walk out at any time.

## Status

| Step | What | State |
| --- | --- | --- |
| 2 | The Corpse, his bargain, both endings | done (placeholder lines) |
| 2b | The Corpse's real model, his five animations on their triggers, the effects of both endings | done |
| 0.1.6 prep | Larry renamed to the Decaying Corpse; the intro now ends on `reveal` (pack override in `kubejs/data/superiorstory/superiorstory/scenes/intro.json`) | done |
| Sounds | His own 45 ms voice blips in place of Story's typing blip while you talk to him; death and decay sounds (through Superior Sounds) | done |
| Dialogue | Mug's six-step script, resuming at the step the player reached | done |
| Satchel | Corpse's Satchel: 2 oak planks, 1 cobblestone, 2 andesite alloy, the same for everyone; any weapon counts | done |
| Endings | Slay and Abandon on the new script; ending keys for Aho's FTB quests | done |
| Cove | Mug's cave and cove cut from Mug's world, placed near spawn in new worlds; players start in the cave | done (needs testing in game) |
| Haskel | Haskel Hobb, the fisherman in the cove: his model and animations, dock walking, the hit-and-vanish, his voice, the crate quest and its reward | done (placeholder lines for Mug to rewrite) |
| Later | Haskel's voice from Mug's REAPER render; light-blue / red choice hover (needs a SuperiorStory change: its choices carry no colour) | |

The class menu (categories, class pages, hold-to-choose, per-class kits, the Moldy Notes spell book) was dropped from the tutorial on 2026-10-10. Class choice happens afterwards in Aho's skill tree. The shelved design is kept in Mug's doc "Superior Class Path Archive".

## The Decaying Corpse

- Entity `superior_tutorial:decaying_corpse`, with a spawn egg in the Spawn Eggs creative tab.
- Never moves and cannot be pushed, leashed, burned or killed by a survival player. A survival hit gets one dry line on the action bar instead. A creative-mode hit removes him, for editing the cave. `/kill` and the void still work.
- His body keeps one facing; only his head turns (up to 35 degrees each way) toward the nearest player within 8 blocks who has not finished with him, lifting it to their eye level. He keeps looking at the player while talking (Story switches his AI off and points his head forward during a conversation; the Corpse ignores that and turns his head himself). When first placed, he faces the nearest player, so place him with the egg while standing where he should look. Rotated structures turn him with them.
- Persistent: never despawns.
- Size: 1.3 wide, 1.45 tall (his sitting model). He is drawn two pixels forward of his block's centre so his back rests against a wall behind it; place him in the block in front of the wall.

### Look and animations

Mug's GeckoLib model (`geo/entity/decaying_corpse.geo.json`), his two skins (`textures/entity/decaying_corpse.png`, `decaying_corpse_decayed.png`) and the eye glow (`decaying_corpse_glow.png`, two pixels drawn full-bright). Five animations in `animations/entity/decaying_corpse.animation.json`. Which one plays is decided on each player's own screen, from that player's own keys:

| Animation | Plays when |
| --- | --- |
| `idle` (12 s, loops) | He is waiting. He breathes so faintly you can't tell at a glance; his eyes glow with each breath, up to 40%. His head follows the player on top of it. |
| `twitch` (0.9 s) | While he waits, at the end of every third to fifth breath (random each time): a small twitch runs through him and a little dust falls, then he breathes on. Not while someone is talking to him. |
| `slay` (2.8 s, then held while he fades: 4.4 s in all) | The moment that player's `slain` key is set ("...Thank you."). He looks at his killer until he goes limp, then fades. Then he is gone for them. |
| `leave` (6.0 s) | The moment that player's `left` key is set ("...Monster."). Then he stays rotten for them. |
| `rotten_idle` (10 s, loops) | Afterwards, for a player who left him: decayed skin, no glow, head no longer follows. |

His head lets go of the player as part of each ending (slay: as he goes limp; leave: as he reaches out). In the dialogue portrait he is drawn in his current pose, looking straight out. Joining a world where the ending already happened just shows the result (gone, or rotten), without replaying it.

### Ending effects

Quiet on purpose (no ceremony): nothing flashes or rises into the sky.

- **Slay:** his eyes light up as he is ended and a low ring of pale light settles on the floor around him. The light goes out of his eyes (soul flecks leave his face). As he goes limp, a last breath of ash and smoke from his mouth and soul smoke from his chest. Then he fades while ash and bone dust fall away and a thin trail of souls rises; a small fall of dust where he sat. His shadow fades with him.
- **Leave:** his eyes go out. As his hand drops, a cold cloud of dust rolls out low over the floor with a burst of ash, bone dust and dark flakes; the decayed skin creeps over him while the flesh he loses fades away, ash, dust and flakes keep sifting off him, and a dark stain soaks into the floor under him (`decaying_corpse_stain.png`), which stays. Afterwards a flake of ash or a dark fleck falls off him now and then.
- Sounds for both endings are planned through Superior Sounds (not in this mod).

`decaying_corpse_leave_base.png` and `decaying_corpse_leave_flesh.png` are made from the two skins (the parts they share, and the flesh only the normal skin has), so the leave ending can fade the lost flesh smoothly. If either skin changes, they need making again.

## Per-player state

Each player has their own Corpse. The state is stored as Superior Lib unlock keys (saved with the world, synced to that player's client, readable in dialogue with Story's `unlocked` condition, and readable by other mods such as Aho's FTB quests):

| Key | Meaning |
| --- | --- |
| `superior_tutorial_decaying_corpse_step2` | The player heard him out (step 1). Coming back, they start at step 2. |
| `superior_tutorial_decaying_corpse_bargain` | The player agreed (step 2) and got the Corpse's Satchel, exactly once. |
| `superior_tutorial_decaying_corpse_refused` | The player denied his request once (step 5). Coming back armed, they start at step 6. |
| `superior_tutorial_decaying_corpse_done` | Either ending happened. All of the Corpse's dialogue is hidden after this. **This is the key for "the tutorial is finished".** |
| `superior_tutorial_decaying_corpse_slain` | The player ended him. He is no longer drawn or targetable for that player (a soul-smoke burst marks the moment). |
| `superior_tutorial_decaying_corpse_left` | The player left him to his fate. He is drawn decayed, head hanging, for that player. Right-clicking him shows "..." on the action bar. |

Everyone else still sees their own Corpse. To reset a player for testing: `/superior_lib lock <player> <key>` for each key.

## Dialogue

Mug's six-step script. Files in `src/main/resources/data/superior_tutorial/superiorstory/scenes/`, all bound with `"npc": "superior_tutorial:decaying_corpse"` and marked `"chat": true` (Story only lets a mob talk when its dialogue offers something or is marked as chat). The highest-priority file whose conditions hold is the one that plays, which is how a returning player resumes at their step:

| File | Priority | When | Starts at |
| --- | --- | --- | --- |
| `decaying_corpse_meet.json` | 1 | always (until done) | step 1 |
| `decaying_corpse_step2.json` | 2 | `step2` | step 2 |
| `decaying_corpse_unarmed.json` | 3 | `bargain`, no weapon in hand | step 4 |
| `decaying_corpse_armed.json` | 4 | `bargain`, any weapon in hand | step 5 |
| `decaying_corpse_refused.json` | 5 | `bargain` and `refused`, any weapon in hand | step 6 |

- Step 1, "What about it?" sets `step2`. Step 2, "I'll do it." hands over the satchel (`"corpse_satchel": true`) and sets `bargain` in the same click, so the satchel can only be given once.
- Step 5, "Deny the request." sets `refused`.
- Each ending's keys are set on its last line, so closing the box early (Esc) does not seal it.
- Every choice that moves the quest on is marked `"outcome": "good"`, every one that leaves is `"outcome": "bad"`. Story does not colour choices yet; the marks are there for when it does.

All text lives in `src/main/resources/assets/superior_tutorial/lang/en_us.json` (keys `superior_tutorial.decaying_corpse.*` and `superior_tutorial.choice.*`). It can be rewritten there without touching the dialogue files, or overridden from a resource pack without a rebuild.

### The Corpse's Satchel

`"corpse_satchel": true` is an action this mod adds to Story. It gives a Sophisticated Backpack named "Corpse's Satchel" that fills itself, the first time it is opened, from the loot table `superior_tutorial:satchel/corpse` (`data/superior_tutorial/loot_tables/satchel/corpse.json`): 2 oak planks, 1 cobblestone, 2 andesite alloy. Change the loot table to change the contents. Without Sophisticated Backpacks the player gets the items loose.

### Sounds

All of them play through Superior Sounds (catalog `assets/superior_tutorial/superior_sounds/audio/corpse.json`); this mod plays nothing itself. While he waits (not talking, no ending chosen) he also groans now and then, every 14 to 32 seconds: event `superior_tutorial:corpse_ambient`, sound `corpse.ambient` = the husk's idle sounds at 0.55 pitch (slow and low), volume 0.22.

- **Voice:** while the local player has the dialogue box open with a Corpse, the signal `superior_tutorial:corpse_speaking` is true, and a rule with priority 100 answers Story's typing event (`superiorstory:type`) with `superior_tutorial:corpse.voice` instead of Story's blip. The six voice blips (`sounds/corpse/voice_1-6.ogg`) are cut from Mug's recordings to 45 ms, the length of the intro's typing blips, so they never pile up.
- **Endings:** cue events `superior_tutorial:corpse_slay_*` and `corpse_decay_*` fire at set moments of each ending, at his body. They play vanilla sounds lowered in pitch (`assets/superior_tutorial/sounds.json`): slay is a last breath, bones, cloth, a slump, a low ring as he fades, dust; leave is wet rot, bone and grit as his hand drops, roots, moss, and settling mud.

### `armed` condition

Added to Story's registry by this mod. `"armed": true` is true when the player holds a weapon in the main hand; `"armed": false` when not. A weapon is a bow, crossbow or trident, or anything that adds attack damage in the main hand (swords, axes, modular weapons, and also pickaxes and other tools). Usable in any Story `if` / `unless`.

## The cave and cove

Mug's build, cut from Mug's world (box 42 -12 126 to 150 50 180) into `data/superior_tutorial/structures/cove.nbt`, 109 x 63 x 55. The cutter is `tools/cut.py` in the project's fisherman folder.

- The file keeps only the open space inside (air, water, water plants), every block within 3 blocks of it, the surface within 5 blocks of the sinkhole's opening (with any gaps in that edge filled), and everything built above the surface. The rest is left out of the file, so the world's own rock and soil stay around it.
- Mug's markers are gone (air). The Corpse (facing west, toward the cave) and Haskel (at his home on the dock end, the yellow marker on the trapdoor at 126 1 150, facing the pond; his other yellow dock spots and the lime spot in his house are walking spots) are in the file as entities. A Truly Modular workbench (`miapi:modular_work_bench`) stands where Mug put the anvil (71 2 148 in Mug's world), broad side to the room (facing north; its model is turned a quarter from an anvil's, so Mug's west-facing anvil is a north-facing bench). Two green wool 'moss' blocks by the pond that went missing in game (121 -2 164, 122 -2 163) are moss blocks in the file.
- **Chests:** the cave's three chests are Quark spruce chests (their facing kept) with the loot table `superior_tutorial:chests/cove_secret` (junk, and about one in two has some iron or gold). The spruce chest under the dock (129 -6 154) has `superior_tutorial:chests/cove_sunken`: Haskel's Crate, always, and some wet junk. With Lootr installed, Lootr turns these into its own chests (its look, not spruce), so every player gets their own loot and their own crate. Haskel's own chests in his house stay empty.
- **New worlds:** the cove's site is chosen when a new world is created, before its first chunk is made (`LevelEvent.CreateSpawnPosition`, searching from where vanilla looks for the world spawn), and the cove is placed there once the server has started; it is placed once in the Overworld, unturned (some modded blocks, such as Quark posts, don't turn with a structure), on the nearest even, dry ground to world spawn (searching up to 512 blocks out; ground over the cavern no more than 6 blocks uneven, no ocean, river or beach, at least 3 above sea level, the cave covered by at least 4 blocks of rock). From the moment the site is chosen, world generation is guarded (`world/CoveGuard`, two mixins: `WorldGenRegion.setBlock` and `ConfiguredFeature.place`; Overworld only): no tree starts within 8 blocks of the opening (vanilla tree, huge fungus and huge mushroom features, or any feature with "tree" in its name); no tree block (by tag, leaves type or a name ending in _log, _wood, _leaves, _stem or _hyphae) is set within 3 blocks of it; no plant is set in the opening or in the cove's box below the surface; and once the cove is placed, nothing at all is set in its box below the surface. So the trees and plants are never made, and distant-terrain (LOD) mods that draw the world from its generation show it the same way. Then the opening is cleared up to the sky. (`place_cove` in an older world first takes down any tree in or within 3 blocks of the opening whole.) Blocks keep exactly the shape they were saved with (posts, fences and walls are not reconnected). Placement takes a few seconds while the world loads. Older worlds never get it by themselves.
- **First join:** each player is put once in the cave, on the first rock out of the water at its dark end, looking toward the Corpse (key `superior_tutorial_cave_started`). Their respawn point is not changed.
- **Config** (`config/superior_tutorial-common.toml`): `placeCoveInNewWorlds` and `startPlayersInCave`, both on.
- **Commands** (operators): `/superior_tutorial place_cove` places the cove so the sinkhole opens where you stand (unturned) and makes it this world's cove; `/superior_tutorial cave` takes you to the start point.

## Haskel Hobb, the fisherman

Working name. Entity `superior_tutorial:fisherman` (about a villager's size), spawn egg in the Spawn Eggs tab. Model, texture and animations are Mug's Legendary Pack trader, colour-swapped, with a rod on his pack (`geo/entity/fisherman.geo.json`, `textures/entity/fisherman.png`, `animations/entity/fisherman.animation.json`).

- **Dock:** his home is the end of his dock, so a player coming out of the cave sees him first. He only walks between his home, his dock spots and one spot in his house, never into water. He stays 1 to 3 minutes at home and 20 to 40 seconds anywhere else (and lets a fidget finish), then walks (about 1 block a second) to another spot he can reach; every other walk goes home. He opens the house doors, walks through and closes them behind him. Pushed or knocked more than 3 blocks off his spots (and not on his way somewhere), he walks back. Spots come from the structure (`HaskelSpotOffsets`, x y z triples from where he stands, and `HaskelHouseOffset`, one triple; both turned with the structure) or are his own block when placed with the egg.
- **People:** a player within 3 blocks makes him stop and face them; within 6 his head follows them (up to 70 degrees). His body turns only after his head has been turned far for a while, and slowly. He says nothing on his own; the only line outside dialogue is the one after a hit.
- **Hits:** he can't be hurt. A player's hit makes him vanish in a big cloud of smoke and step out again on the dock spot (home included) farthest from that player, standing on whatever is there (slab, trapdoor, cold campfire); never in the house. Only with no dock spots, dry ground 8 to 12 blocks away, with a line for the attacker. A sneaking creative hit removes him, for editing. `/kill` and the void still work.
- **Animations:** idle always; one of three fidgets (weight shift, pack settle, toe tap) 6 to 14 seconds after he stops, then every 15 to 40 seconds while he stands and nobody talks to him (the server picks them and tells the clients); the walk while he moves, sped up to match his pace.
- **Sounds** (through Superior Sounds, catalog `superior_sounds/audio/fisherman.json`): his voice blips (`sounds/fisherman/voice_1-6.ogg`) replace Story's typing blip while the local player talks to him (signal `superior_tutorial:fisherman_speaking`); cue events `superior_tutorial:fisherman_vanish` and `fisherman_appear`. Ambient: every 10 to 25 seconds while he isn't talking or hidden, a low grumble (event `superior_tutorial:fisherman_ambient`, sound `fisherman.ambient` = the villager's idle sounds at 0.5 pitch, the lowest the game plays, which also slows them, volume 0.5).
- Persistent: never despawns. If a conversation ever ends without Story letting go of him (his AI left off), he frees himself after 3 seconds.

### The crate quest

A crate went off his dock into the pond while he was unloading. The player fishes it out and brings it back.

- **Haskel's Crate** is in the spruce chest under the dock (loot table `superior_tutorial:chests/cove_sunken`, see the cove above).
- **Sunken Crate** (`superior_tutorial:sunken_crate`): no longer in the cove, kept for hand-placed use. It can't be broken in survival; using or hitting it hands the player Haskel's Crate (one at a time; nothing after the quest is done), and a player who has taken the deal and casts within 6 blocks of it (sideways; up to 14 below the bobber) hooks the crate instead of a catch half the time.
- **Reward** (`"haskel_reward": true`, an action this mod adds to Story): takes the crate and gives the **Haskelsack** with **Haskel's Map** inside in place of the fish stew (an explorer map with a red X on the nearest village: any structure in `#minecraft:village` or with "village" in its name, so modded villages count), a Supplementaries sack filled from `superior_tutorial:sack/haskel`: Ryan (a crab in a bucket), cooked crab legs, cod rolls, salmon slices, fish stew, John Fish (a pufferfish), string, lily pads and Haskel's Spare Rod (Lure II). Without Supplementaries the items come loose. If no village can be found, the sack keeps the fish stew and he says "Here, take the sack. Don't die!".
- `"has_haskel_crate": true` (a condition this mod adds): the player carries Haskel's Crate.

| Key | Meaning |
| --- | --- |
| `superior_tutorial_fisherman_met` | The player met him. |
| `superior_tutorial_fisherman_deal` | The player agreed to fish the crate out. |
| `superior_tutorial_fisherman_done` | The crate was handed back and the reward given. |

| Scene file | Priority | When |
| --- | --- | --- |
| `fisherman_meet.json` | 1 | not met, not done |
| `fisherman_meet_left.json` | 2 | as above, and the player left the Corpse |
| `fisherman_meet_slain.json` | 3 | as above, and the player ended the Corpse |
| `fisherman_deal.json` | 4 | met, no deal yet |
| `fisherman_waiting.json` | 5 | deal, not done |
| `fisherman_handin_early.json` | 6 | carrying the crate (no deal yet) |
| `fisherman_handin.json` | 7 | deal, carrying the crate |
| `fisherman_after.json` | 8 | done |

All his lines are placeholders for Mug to rewrite, in `en_us.json` under `superior_tutorial.fisherman.*` and `superior_tutorial.choice.fisherman.*`. The first meeting keeps the reward vague on purpose.

## Testing

1. Put the jar in the pack's `mods` folder next to `superior_story.jar`, `superior_lib.jar`, `superior_sounds.jar`, Sophisticated Backpacks and GeckoLib.
2. In a creative world, place the Corpse with the spawn egg, then switch to survival.
3. Press the Talk key (R) while looking at him.
4. To repeat a test: `/superior_lib lock @s superior_tutorial_decaying_corpse_step2`, then the same for `_bargain`, `_refused`, `_done`, `_slain` and `_left`. The Corpse goes straight back to waiting.
5. The cove: create a new world (a normal one, not flat or void) and you start in the cave. In an existing world, stand on open ground and use `/superior_tutorial place_cove`, then `/superior_tutorial cave`.
6. Haskel: talk to him (R), take the deal, then dive to the chest under the dock, take the crate, and bring it back. To repeat: lock `superior_tutorial_fisherman_met`, `_deal` and `_done`.

What to look for:
- Idle: he sits on the floor, back to the wall, breathing barely visibly; his eyes glow faintly at each breath; his head follows you within 8 blocks.
- Every 36 to 60 seconds, at the end of a breath, he twitches and a little dust falls.
- Hit him in survival: nothing moves; only the "Won't take. Tried." line shows.
- Slay: eyes light, go out, he fades with ash and soul smoke, and is gone (no hitbox) for you.
- Leave: his arm reaches, his hand drops with an ash burst, he rots over a few seconds, and stays rotten and still. Right-clicking him shows "...".
- A second player who hasn't finished with him still sees him waiting.

## Building

Copy `superior_story.jar`, `superior_lib.jar` and the GeckoLib jar (`geckolib-forge-1.20.1-4.8.4.jar`) from the pack's `mods` folder into `libs/`, then run `gradlew build`. The jar lands in `build/libs/`. The jar names only have to start with `superior_story`, `superior_lib` and `geckolib`.

The pack needs GeckoLib (4.8 or newer for 1.20.1); the tutorial won't load without it.
