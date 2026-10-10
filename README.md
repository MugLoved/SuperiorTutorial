# SuperiorTutorial

The optional first cave of Superior (Forge 1.20.1 / 47.4.20). An addon to SuperiorStory: it adds its own content and extends Story only through Story's public module registry. Story itself is never edited.

The player starts in a small, old cave that was never cleared. The Decaying Corpse, held to life against his will, sits against the wall. He wants the player to end him, and his hands are too weak to do it himself. He hands over a satchel with just enough for a first weapon and asks to be killed with it. Class choice is not part of the tutorial; it happens afterwards in Aho's skill tree. Nothing is forced: a player can walk out at any time.

## Status

| Step | What | State |
| --- | --- | --- |
| 2 | The Corpse, his bargain, both endings | done (placeholder lines) |
| 2b | The Corpse's real model, his five animations on their triggers, the effects of both endings | done |
| 0.1.6 prep | Larry renamed to the Decaying Corpse; the intro now ends on `reveal` (pack override in `kubejs/data/superiorstory/superiorstory/scenes/intro.json`) | done |
| Sounds | One voice sound per line, death and decay sounds | next |
| Dialogue | Mug's six-step script | |
| Satchel | Corpse's Satchel: 2 oak planks, 1 cobblestone, 2 andesite alloy, the same for everyone; any weapon counts | |
| Endings | Slay and Abandon on the new script; ending keys for Aho's FTB quests | |
| Later | The merchant in the cove; spawning inside the cave | |

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

Each player has their own Corpse. The state is stored as Superior Lib unlock keys (saved with the world, synced to that player's client, readable in dialogue with Story's `unlocked` condition):

| Key | Meaning |
| --- | --- |
| `superior_tutorial_decaying_corpse_bargain` | The player agreed to make a weapon. |
| `superior_tutorial_decaying_corpse_done` | Either ending happened. All of the Corpse's dialogue is hidden after this. |
| `superior_tutorial_decaying_corpse_slain` | The player ended him. He is no longer drawn or targetable for that player (a soul-smoke burst marks the moment). |
| `superior_tutorial_decaying_corpse_left` | The player left him. He is drawn decayed, head hanging, for that player (an ash burst marks the moment). Right-clicking him shows one line on the action bar. |

Everyone else still sees their own Corpse. To reset a player for testing: `/superior_lib lock <player> <key>` for each key.

## Dialogue

Files in `src/main/resources/data/superior_tutorial/superiorstory/scenes/`, all bound with `"npc": "superior_tutorial:decaying_corpse"` and marked `"chat": true`. Story only lets a mob talk when its dialogue offers something (a quest, a hand-over) or is marked as chat, so every Corpse dialogue needs the mark:

- `decaying_corpse_meet.json` (priority 1): the first meeting. Every screen has Leave. The last choice strikes the bargain.
- `decaying_corpse_unarmed.json` (priority 2): after the bargain, with no weapon in hand: he points to the bench.
- `decaying_corpse_armed.json` (priority 3): after the bargain, holding a weapon: "Now... your end of it." Slay or Leave. Each ending's key is set on its last line, so closing the box early (Esc) does not seal it.

All text lives in `src/main/resources/assets/superior_tutorial/lang/en_us.json`. The current lines are placeholders. They can be rewritten there without touching the dialogue files, or overridden from a resource pack without a rebuild.

### `armed` condition

Added to Story's registry by this mod. `"armed": true` is true when the player holds a weapon in the main hand; `"armed": false` when not. A weapon is a bow, crossbow or trident, or anything that adds attack damage in the main hand (swords, axes, modular weapons, and also pickaxes and other tools). Usable in any Story `if` / `unless`.

## Testing

1. Put the jar in the pack's `mods` folder next to `superior_story.jar`, `superior_lib.jar` and GeckoLib.
2. In a creative world, place the Corpse with the spawn egg, then switch to survival.
3. Press the Talk key (R) while looking at him.
4. To repeat a test: `/superior_lib lock @s superior_tutorial_decaying_corpse_bargain`, then the same for `_done`, `_slain` and `_left`. The Corpse goes straight back to waiting.

What to look for:
- Idle: he sits on the floor, back to the wall, breathing barely visibly; his eyes glow faintly at each breath; his head follows you within 8 blocks.
- Every 36 to 60 seconds, at the end of a breath, he twitches and a little dust falls.
- Hit him in survival: nothing moves; only the "Won't take. Tried." line shows.
- Slay: eyes light, go out, he fades with ash and soul smoke, and is gone (no hitbox) for you.
- Leave: his arm reaches, his hand drops with an ash burst, he rots over a few seconds, and stays rotten and still. Right-clicking him shows the remains line.
- A second player who hasn't finished with him still sees him waiting.

## Building

Copy `superior_story.jar`, `superior_lib.jar` and the GeckoLib jar (`geckolib-forge-1.20.1-4.8.4.jar`) from the pack's `mods` folder into `libs/`, then run `gradlew build`. The jar lands in `build/libs/`. The jar names only have to start with `superior_story`, `superior_lib` and `geckolib`.

The pack needs GeckoLib (4.8 or newer for 1.20.1); the tutorial won't load without it.
