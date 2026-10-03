# SuperiorTutorial

The optional first cave of Superior (Forge 1.20.1 / 47.4.20). An addon to SuperiorStory: it adds its own content and extends Story only through Story's public module registry. Story itself is never edited.

The player starts in a small, old cave that was never cleared. Larry, a skeleton held to life against his will, sits against the wall. He wants the player to end him, and his hands are too weak to do it himself. The class choice, the starter kit and the reward all come through him. Nothing is forced: a player can walk out at any time and miss only a weak starter kit.

## Status

| Step | What | State |
| --- | --- | --- |
| 2 | Larry, his bargain, both endings | done (stand-in model, placeholder lines) |
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
- Stand-in look: the vanilla skeleton, sitting, arms limp. Mug's own GeckoLib model replaces it later.

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

1. Put the jar in the pack's `mods` folder next to `superior_story.jar` and `superior_lib.jar`.
2. In a creative world, place Larry with the spawn egg, then switch to survival.
3. Press the Talk key (R) while looking at him.
4. To repeat a test: `/superior_lib lock @s superior_tutorial_larry_bargain`, then the same for `_done`, `_slain` and `_left`.

## Building

Copy `superior_story.jar` and `superior_lib.jar` from the pack's `mods` folder into `libs/`, then run `gradlew build`. The jar lands in `build/libs/`.
