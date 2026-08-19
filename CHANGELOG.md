## Release

### [1.5.2] - 2026-08-19

#### Added
- Add a configurable Minecraft keybind to open the Chomagerie configuration menu.
- Add MaLiLib-based configuration categories for All, ShulkerRefill, and Team Tag.
- Add support for multiple shulker box names in the ShulkerRefill name filter.
- Add support for multi-stop Team Tag gradients.
- Add a MaLiLib color-list editor and color picker for Team Tag gradient colors.
- Add English fallback language entries for the new configuration menu.

#### Changed
- Replace the previous configuration UI with a MaLiLib configuration screen.
- Team Tag configuration is now handled through the menu instead of `/chomteam` and `/teamtag` commands.
- Gradient colors are now edited as a color list instead of separate start/end text fields.
- ShulkerRefill name filtering now accepts a list of exact names instead of a single exact name.

#### Fixed
- Fix the Minecraft controls category label for the Chomagerie keybind.
- Fix a startup crash caused by registering key mappings before Minecraft options were available.
- Fix missing language fallback behavior by defaulting unavailable language entries to English.
- Fix the MaLiLib tab layout so the All tab stays inside the screen.
- Fix raw translation keys showing in the gradient color list menu.
