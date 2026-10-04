# SuperiorTutorial

The optional first cave of Superior (Forge 1.20.1 / 47.4.20). An addon to SuperiorStory: it adds its own content and extends Story only through Story's public module registry. Story itself is never edited.

The player starts in a small, old cave that was never cleared. Larry, a corpse held to life against his will, sits against the wall. He wants the player to end him, and his hands are too weak to do it himself. The class choice, the starter kit and the reward all come through him. Nothing is forced: a player can walk out at any time and miss only a weak starter kit.

## Status

| Step | What | State |
| --- | --- | --- |
| 2 | Larry, his bargain, both endings | done (placeholder lines) |
| 2b | Larry's real model, his five animations on their triggers, the effects of both endings | done (he uses Story's normal NPC voice for now) |
| 3 | Class menu (categories, classes, Equipment, Difficulty, click-and-hold Choose) | next |
| 4 | Real class pick and starter materials per class | |
| 5 | Workbench loop, Corpse's Satchel, goodie bag and backpack | |
| 6 | The merchant in the cove | |
| 7 | Spawn inside the cave, intro ending changed to Continue | |

## Larry

- Entity `superior_tutorial:larry`, with a spawn egg in the Spawn Eggs creative tab.
- Never moves and cannot be pushed, leashed, burned or killed by a survival player. A survival hit gets one dry line on the action bar instead. A creative-mode hit removes him, for editing the cave. `/kill` and the void still work.
- His body keeps one facing; only his head turns (up to 70 degrees) toward the nearest player within 8 blocks who has not finished with him. When first placed, he faces the nearest player, so place him with the egg while standing where he should look. Rotated structures turn him with them.
- Persistent: never despawns.
- Size: 1.3 wide, 1.45 tall (his sitting model). He is drawn two pixels forward of his block's centre so his back rests against a wall behind it; place him in the block in front of the wall.

### Look and animations

Mug's GeckoLib model (`geo/entity/larry.geo.json`), his two skins (`textures/entity/larry.png`, `larry_decayed.png`) and the eye glow (`larry_glow.png`, two pixels drawn full-bright). Five animations in `animations/entity/larry.animation.json`. Which one plays is decided on each player's own screen, from that player's own keys:

| Animation | Plays when |
| --- | --- |
| `idle` (12 s, loops) | He is waiting. He breathes so faintly you can't tell at a glance; his eyes glow with each breath, up to 40%. His head follows the player on top of it. |
| `shake` (0.9 s) | A survival player hits him (with the "Won't take. Tried." line). Plays over idle; his eyes flare, a little dust falls. Only a player still waiting on him sees it. |
| `slay` (2.8 s) | The moment that player's `slain` key is set ("...Thank you."). Then he is gone for them. |
| `leave` (6.0 s) | The moment that player's `left` key is set ("...Monster."). Then he stays rotten for them. |
| `rotten_idle` (10 s, loops) | Afterwards, for a player who left him: decayed skin, no glow, head no longer follows. |

His head stops following the player over half a second when an ending starts. Joining a world where the ending already happened just shows the result (gone, or rotten), without replaying it.

### Ending effects

Quiet on purpose (no ceremony): nothing flashes or rises into the sky.

- **Slay:** his eyes light up as he is ended, then go out (a few soul flecks leave his face). He fades away while ash comes off him; as he goes limp, soul smoke from his chest and a thin trail of souls for a moment. His shadow fades with him.
- **Leave:** his eyes go out. As his hand drops, a burst of ash and bone dust; the decayed skin creeps over him while the flesh he loses fades away, and ash and dust keep sifting off him until he has rotted. Afterwards a flake of ash falls off him now and then.

`larry_leave_base.png` and `larry_leave_flesh.png` are made from the two skins (the parts they share, and the flesh only the normal skin has), so the leave ending can fade the lost flesh smoothly. If either skin changes, they need making again.

## Per-player state

Each player has their own Larry. The state is stored as Superior Lib unlock keys (saved with the world, synced to that player's client, readable in dialogue with Story's `unlocked` condition):

| Key | Meaning |
| --- | --- |
| `superior_tutorial_larry_bargain` | The player agreed to make a weapon. |
| `superior_tutorial_larry_done` | Either ending happened. All of Larry's dialogue is hidden after this. |
| `superior_tutorial_larry_slain` | The player ended him. He is no longer drawn or targetable for that player (a soul-smoke burst marks the moment). |
| `superior_tutorial_larry_left` | The player left him. He is drawn decayed, head hanging, for that player (an ash burst marks the moment). Right-clicking him shows one line on the action bar. |

Everyone else still sees their own Larry. To reset a player for testing: `/superior_lib lock <player> <key>` for each key.

## Dialogue

Files in `src/main/resources/data/superior_tutorial/superiorstory/scenes/`, all bound with `"npc": "superior_tutorial:larry"` and marked `"chat": true`. Story only lets a mob talk when its dialogue offers something (a quest, a hand-over) or is marked as chat, so every Larry dialogue needs the mark:

- `larry_meet.json` (priority 1): the first meeting. Every screen has Leave. The last choice strikes the bargain.
- `larry_unarmed.json` (priority 2): after the bargain, with no weapon in hand: he points to the bench.
- `larry_armed.json` (priority 3): after the bargain, holding a weapon: "Now... your end of it." Slay or Leave. Each ending's key is set on its last line, so closing the box early (Esc) does not seal it.

All text lives in `src/main/resources/assets/superior_tutorial/lang/en_us.json`. The current lines are placeholders. They can be rewritten there without touching the dialogue files, or overridden from a resource pack without a rebuild.

### `armed` condition

Added to Story's registry by this mod. `"armed": true` is true when the player holds a weapon in the main hand; `"armed": false` when not. A weapon is a bow, crossbow or trident, or anything that adds attack damage in the main hand (swords, axes, modular weapons, and also pickaxes and other tools). Usable in any Story `if` / `unless`.

## Testing

1. Put the jar in the pack's `mods` folder next to `superior_story.jar`, `superior_lib.jar` and GeckoLib.
2. In a creative world, place Larry with the spawn egg, then switch to survival.
3. Press the Talk key (R) while looking at him.
4. To repeat a test: `/superior_lib lock @s superior_tutorial_larry_bargain`, then the same for `_done`, `_slain` and `_left`. Larry goes straight back to waiting.

What to look for:
- Idle: he sits on the floor, back to the wall, breathing barely visibly; his eyes glow faintly at each breath; his head follows you within 8 blocks.
- Hit him in survival: he shakes, his eyes flare, the line shows.
- Slay: eyes light, go out, he fades with ash and soul smoke, and is gone (no hitbox) for you.
- Leave: his arm reaches, his hand drops with an ash burst, he rots over a few seconds, and stays rotten and still. Right-clicking him shows the remains line.
- A second player who hasn't finished with him still sees him waiting.

## Building

Copy `superior_story.jar`, `superior_lib.jar` and the GeckoLib jar (`geckolib-forge-1.20.1-4.8.4.jar`) from the pack's `mods` folder into `libs/`, then run `gradlew build`. The jar lands in `build/libs/`. The jar names only have to start with `superior_story`, `superior_lib` and `geckolib`.

The pack needs GeckoLib (4.8 or newer for 1.20.1); the tutorial won't load without it.
