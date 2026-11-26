---
name: regles-tv
description: Conventions d'interface de SamothTV (télécommande, focus, fenêtres, adaptation TV/tablette/téléphone, thème). À appliquer dès qu'on crée ou modifie un écran, une tuile, un bouton ou une fenêtre de dialogue dans app/src/main/java/com/nova/tv/ui ou player.
user-invocable: false
---

# Règles d'interface SamothTV

L'appli se pilote d'abord à la **télécommande** (box SFR, TV Sony), et aussi au doigt sur téléphone et tablette. Ces règles évitent de réintroduire des bugs déjà corrigés.

## Tout ce qui se sélectionne passe par `FocusBox`
- Fichier : `ui/components/Components.kt`.
- Gère l'agrandissement au focus, la bordure, la mémoire de focus (`memoryKey`) pour revenir au bon endroit après un retour, l'appui long (télécommande et tactile).
- Ne jamais utiliser `clickable` seul sur un élément navigable à la télécommande.

## Appui long = favoris
- Sur une tuile : `onLongClick = { longPressFav(vm, context, tile) }`.
- `LongPressGuard` (posé à la racine dans `AppRoot`) consomme les répétitions de touche jusqu'au relâchement. Ne pas le retirer : sinon le menu qui s'ouvre se valide tout seul.

## Fenêtres de dialogue
- Toujours un fond plein écran semi-transparent avec `.trapFocus()` : sans lui, les flèches déplacent la sélection derrière la fenêtre.
- Donner le focus au bouton principal après ~100 ms (`LaunchedEffect` + `FocusRequester.requestFocus()` dans `runCatching`).
- `BackHandler` pour fermer.
- Modèles existants : `ResumeDialog`, `BusyDialog` dans `ui/screens/ProfilesScreen.kt`.

## Adapter à l'appareil
- `LocalDevice.current` : `isTv`, `compact` (téléphone), `touch`.
- Marges latérales : `LocalGutter` (48 dp TV, 32 tablette, 16 téléphone), pas de valeur en dur.
- Champs texte sur TV : le clavier est pénible ou absent (Sony du salon) → proposer `PhoneConnectCard` (saisie par QR code depuis le téléphone).
- Textes importants sur une seule ligne en mode compact (`maxLines = 1`, taille réduite).

## Thème
- Couleurs uniquement depuis `Nova` (`ui/Theme.kt`) : `Nova.Accent` (rouge sang), `Nova.Bg`, `Nova.Surface`, `Nova.Line`, `Nova.Muted`, `Nova.Dim`…
- Typographie depuis `Type` (`Display`, `H1`, `H2`, `H3`, `Body`, `Small`, `Label`).
- Boutons : `NovaButton` (`primary = true` pour l'action principale).

## Fluidité (box peu puissantes)
- Calculs lourds (listes, tri, recherche) hors du fil principal : `withContext(Dispatchers.Default)`, et réutiliser les caches de `AppViewModel` (`vis()`, `railsCache`).
- Images via `NetImage` (Coil en rgb565).
- Listes longues en `LazyRow` / `LazyColumn` avec des `key` stables.

## Lecteur
- OK = pause / lecture, y compris quand la barre de progression a le focus.
- Le lecteur sauvegarde la progression : ne pas casser `saveProgress` (toutes les 15 s, à la sortie, à la fin).

## Textes
- Tout en **français**, ton simple et direct, tutoiement évité dans l'appli (vouvoiement « Vous vous êtes arrêté à… »).
