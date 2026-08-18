# Chomagerie 🧀

Chomagerie ajoute des conforts de jeu pour rendre le serveur plus fluide au quotidien : recharge automatique depuis les shulkers, tags colores devant les pseudos, recettes pratiques et protection des cultures.

## Fonctionnalites ✨

### Recharge automatique depuis les shulkers 📦

Quand tu utilises entierement un stack, Chomagerie peut le remplacer automatiquement par le meme item trouve dans tes shulkers.

- Fonctionne pendant que tu joues, sans ouvrir de menu.
- Garde l'item dans le meme slot.
- Cherche dans ton inventaire puis dans ton ender chest.
- Peut afficher un message et jouer un son quand un refill se fait.
- Peut utiliser seulement les shulkers avec un nom precis, par exemple un shulker de restock.

### Tags de joueur 🎨

Tu peux afficher un petit tag colore avant ton pseudo.

- Le tag apparait sans crochets.
- La couleur de ton pseudo reste normale.
- Tu peux colorer chaque lettre avec les codes Minecraft `&`.
- Tu peux faire des gradients.
- Tu peux changer ou retirer ton tag quand tu veux.

Exemples :

```mcfunction
/chomteam set &2Narko&6tiqu
/chomteam gradient #977272 #E32B2B Narkotiqu
/chomteam clear
/chomteam status
```

Tu peux aussi utiliser `/teamtag` a la place de `/chomteam`.

### Gestion des teams 🛡️

Les operateurs peuvent gerer les teams du serveur avec des commandes.

- Voir les teams existantes.
- Creer ou supprimer une team.
- Changer le nom affiche, le prefixe ou le suffixe.
- L'autocompletion propose les noms affiches des teams.
- Les couleurs `&` et les gradients fonctionnent aussi sur les textes de team.

Exemples :

```mcfunction
/chomteam manage list
/chomteam manage add staff
/chomteam manage remove "Staff"
/chomteam manage display "Staff" &6Staff
/chomteam manage prefix "Staff" <gradient:#977272:#E32B2B>Staff
```

### Protection des cultures 🌾

Le serveur peut controler quand les terres cultivees peuvent etre pietinees.

- Bloquer le pietinement par les joueurs.
- Bloquer le pietinement par les mobs.
- Autoriser le pietinement seulement avec des bottes en cuir, selon la configuration du serveur.

### Recettes pratiques 🔨

Chomagerie ajoute aussi des recettes utiles pour simplifier certains crafts du serveur.

- Recettes de stonecutter.
- Conversions de coraux.
- Recettes utilitaires.
- Recettes autour du cuivre.
- Quelques ajustements de recettes vanilla.

## Configuration en jeu ⚙️

Avec ModMenu, tu peux regler :

- ShulkerRefill
- Team Tag

La gestion avancee des teams se fait uniquement avec les commandes operateur.

## Installation 🚀

Le mod est prevu pour Fabric.

- Les joueurs doivent installer le mod pour utiliser ShulkerRefill et la configuration personnelle.
- Certaines fonctions serveur, comme la protection des cultures, peuvent fonctionner sans installation cote client.
- ModMenu est optionnel, mais recommande pour regler facilement les options.

## License 📜

All Rights Reserved.

- Utilisation en modpack autorisee avec attribution.
- Redistribution, modification ou reutilisation non autorisee hors permission explicite.
