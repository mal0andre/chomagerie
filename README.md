# Chomagerie 🧀

Chomagerie adds quality-of-life features to make everyday gameplay on the server smoother: automatic refills from shulker boxes, colored tags before player names, useful recipes, and crop protection.

## Features ✨

### Automatic Shulker Refill 📦

When you completely use up a stack, Chomagerie can automatically replace it with the same item found inside your shulker boxes.

* Works while you play, without opening any menu.
* Keeps the item in the same inventory slot.
* Searches your inventory first, then your Ender Chest.
* Can display a message and play a sound when a refill happens.
* Can be configured to only use shulker boxes with a specific name, for example a dedicated restock shulker.

### Player Tags 🎨

You can display a small colored tag before your username.

* The tag is displayed without brackets.
* Your username keeps its normal color.
* Each letter can be colored individually using Minecraft `&` color codes.
* Gradients are supported.
* You can change or remove your tag whenever you want.

Examples:

```mcfunction
/chomteam set &2Narko&6tiqu
/chomteam gradient #977272 #E32B2B Narkotiqu
/chomteam clear
/chomteam status
```

You can also use `/teamtag` instead of `/chomteam`.

### Team Management 🛡️

Server operators can manage server teams using commands.

* View existing teams.
* Create or remove a team.
* Change the display name, prefix, or suffix.
* Command autocomplete suggests team display names.
* `&` colors and gradients are also supported for team text.

Examples:

```mcfunction
/chomteam manage list
/chomteam manage add staff
/chomteam manage remove "Staff"
/chomteam manage display "Staff" &6Staff
/chomteam manage prefix "Staff" <gradient:#977272:#E32B2B>Staff
```

### Crop Protection 🌾

The server can control when farmland can be trampled.

* Prevent players from trampling farmland.
* Prevent mobs from trampling farmland.
* Allow trampling only when wearing leather boots, depending on the server configuration.

### Useful Recipes 🔨

Chomagerie also adds useful recipes to simplify certain crafting processes on the server.

* Stonecutter recipes.
* Coral conversions.
* Utility recipes.
* Copper-related recipes.
* A few adjustments to vanilla recipes.

## In-Game Configuration ⚙️

Using ModMenu, you can configure:

* ShulkerRefill
* Team Tag

Advanced team management is only available through operator commands.

## Installation 🚀

The mod is designed for Fabric.

* Players must install the mod to use ShulkerRefill and personal configuration options.
* Some server-side features, such as crop protection, can work without the mod being installed on the client.
* ModMenu is optional, but recommended for easily configuring the available options.

## License 📜

All Rights Reserved.

* Use in modpacks is allowed with attribution.
* Redistribution, modification, or reuse is not allowed without explicit permission.
