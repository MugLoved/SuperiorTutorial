# SuperiorTutorial Agent Guidelines

- This is an addon to SuperiorStory. Never edit Story; extend it only through its public `api` package (`StoryHooks` modules, events). If a feature truly needs something inside Story, write down exactly what and ask Mug to request it from Aho.
- Story's binding rules apply to content written here: dialogue in datapack JSON, modules usable from JSON, all player-facing text in `assets/superior_tutorial/lang/en_us.json`, validate at load.
- Per-player tutorial state lives in Superior Lib unlock keys (`PlayerUnlockApi`), never in a second store. Key names start with `superior_tutorial_`.
- Story owns sounds through Superior Sounds; this mod plays none directly.
- Lore rules for the tutorial: nothing explained, nobody chosen, no waking up, no memory wipe, no first fight, no ceremony. The Decaying Corpse is not a guide; he wants to die and speaks in tired fragments.
- When the JSON surface or a key changes, update `README.md` in the same commit.
