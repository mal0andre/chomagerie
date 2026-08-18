## Release

### [1.5.1] - 2026-08-18

#### Added
- Add Team Tag support through `/chomteam` and `/teamtag`.
- Add configurable Team Tag options in ModMenu.
- Add Minecraft `&` color-code parsing for tags, display names, prefixes, and suffixes.
- Add gradient tag support with `<gradient:#977272:#E32B2B>Text` and `/chomteam gradient #977272 #E32B2B Text`.
- Add rainbow Team Tag mode in the client configuration.
- Add OP-only team management commands: `list`, `add`, `remove`, `display`, `prefix`, and `suffix`.
- Add autocomplete for team management commands using display names instead of raw scoreboard ids.
- Add server-side storage for the last player name and Team Tag team identity.

#### Changed
- Team Tag prefixes no longer use surrounding brackets.
- Team Tag no longer changes the player's name color.
- Team Tag teams are now stable per player UUID, so changing the tag updates the existing team instead of creating a new one.
- Team management is command-only; the ModMenu `Server Teams` category was removed.
- The README was rewritten to document the current feature set and commands.

#### Fixed
- Prevent leftover Chomagerie tag teams from accumulating when players change tags.
- Clean empty Chomagerie tag teams when applying or clearing a tag.
- Update the stored scoreboard player name when a player reconnects with a changed name.
