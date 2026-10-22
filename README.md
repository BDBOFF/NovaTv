# SamothTV — lecteur IPTV pour Android TV

Lecteur Xtream Codes pour Android TV, Google TV, Fire TV et box Android.
Interface pensée pour la télécommande : accueil immersif, fiches détaillées avec TMDB,
TV en direct avec programme, guide TV, replay, reprise de lecture.

> Nova est un **lecteur** : il ne fournit aucune chaîne ni aucun contenu.

## Fonctions

- Connexion Xtream Codes (serveur + identifiant + mot de passe), ou collage direct d'une URL M3U `get.php?username=…&password=…`
- Plusieurs playlists, bascule depuis les Réglages
- **Accueil** : fond immersif qui suit l'élément sélectionné, logo du titre, note, durée, genres, résumé
  - Reprendre la lecture, Tendances de la semaine (Top 10 TMDB croisé avec votre catalogue), Films récents, Ma liste, Séries mises à jour, Vos chaînes, rangées par catégorie
- **Films / Séries** : catégories, tri (récents, A→Z, note), grille d'affiches
- **Fiches** : fond d'écran, logo, résumé en français, réalisateur, distribution avec photos, bande-annonce YouTube, Ma liste, reprise
- **Séries** : saisons, épisodes avec vignettes, durée et progression, épisode suivant automatique
- **TV Direct** : cartes chaîne avec programme en cours, barre de progression, programme suivant, favoris
- **Guide TV** : grille horaire 4 h, navigation dans le temps, **replay** sur les chaînes compatibles
- **Lecteur** (Media3/ExoPlayer) : MPEG-TS et HLS, choix de la langue audio et des sous-titres, format d'image, zapping ▲▼ et CH+/CH−, liste des chaînes, liste des épisodes, reconnexion automatique du direct
- Recherche dans les films, séries et chaînes
- Catégories adultes masquées par défaut

## Obtenir l'APK sans rien installer (GitHub)

1. Créez un compte gratuit sur github.com, puis un **nouveau dépôt** (privé possible).
2. Envoyez tout le contenu de ce dossier dans le dépôt (bouton *Add file → Upload files*, glissez-déposez les fichiers et dossiers).
   Vérifiez que le dossier caché `.github/workflows/build.yml` est bien présent. Sinon, créez-le via *Add file → Create new file* en collant son contenu.
3. Ouvrez l'onglet **Actions**. La compilation démarre toute seule et dure environ 5 minutes.
4. Ouvrez la compilation terminée (coche verte), section **Artifacts** → téléchargez **NovaTV-apk**. Le zip contient `NovaTV.apk`.

Astuce : créez une *Release* sur GitHub (onglet Releases → *Draft a new release*). L'APK y sera joint automatiquement, avec un lien direct pratique pour l'app **Downloader** sur la TV.

## Installer sur la TV

- **Downloader** (Fire TV / Google TV) : installez l'app *Downloader*, entrez le lien de l'APK, installez.
- **Clé USB** : copiez l'APK, ouvrez-le avec un gestionnaire de fichiers sur la TV.
- **Send Files to TV** : envoyez l'APK depuis votre téléphone.

Autorisez l'installation d'applications inconnues pour l'app utilisée (Paramètres → Sécurité / Applications).

## Compiler avec Android Studio

Ouvrez le dossier dans Android Studio (Ladybug ou plus récent), laissez Gradle synchroniser, puis *Build → Build APK(s)*.
En ligne de commande : `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

## Affiches, fonds et résumés (TMDB)

Sans clé, Nova utilise les affiches et infos fournies par votre serveur.
Avec une clé **TMDB** gratuite, vous obtenez fonds d'écran HD, logos de titres, résumés en français, distribution et vraies tendances :

1. Compte gratuit sur themoviedb.org → Paramètres → API → demandez une clé (usage personnel).
2. Dans Nova : **Réglages → Affiches & fiches**, collez la clé et enregistrez.

Pour l'intégrer directement à l'APK : ajoutez un secret `TMDB_API_KEY` dans le dépôt GitHub (Settings → Secrets and variables → Actions).

## Télécommande

| Touche | Dans les menus | Dans le lecteur |
|---|---|---|
| Flèches | Navigation | Gauche/droite : −10 s / +10 s (maintenir pour accélérer). Haut/bas en direct : chaîne précédente/suivante |
| OK | Ouvrir | Pause / reprise, ou afficher les commandes |
| Retour | Revenir / menu | Quitter le lecteur |
| CH+ / CH− | — | Zapping |

## Signature

L'APK est signé avec la clé `app/nova-release.jks` (mot de passe `novatv2026`), toujours la même : les mises à jour s'installent par-dessus sans perdre vos réglages.
Pour une diffusion publique, remplacez-la par votre propre clé.

## Structure

```
app/src/main/java/com/nova/tv/
  data/        Client Xtream, TMDB, stockage local, nettoyage des titres
  ui/          Thème, navigation, composants (cartes, rangées, focus)
  ui/screens/  Accueil, Films/Séries, Fiches, TV Direct, Guide, Recherche, Réglages, Connexion
  player/      Lecteur Media3 et ses commandes
```

Kotlin 2.1 · Jetpack Compose · Media3 1.5 · Coil · OkHttp · minSdk 21 (Android 5.0).
