# SamothTV — lecteur IPTV pour Android TV, tablette et téléphone

Lecteur **Xtream Codes** et **M3U** pensé pour la télécommande, qui s'adapte aussi au tactile.
Un seul APK pour Android TV, Google TV, box Android, tablettes et téléphones (Android 5.0 minimum).

> SamothTV est un **lecteur** : aucune chaîne ni aucun contenu n'est fourni. Abonnement IPTV non inclus.

## Fonctions

- Connexion **Xtream Codes** ou **lien M3U/M3U8** (+ guide XMLTV), saisie possible **depuis le téléphone par QR code**
- **Profils « Qui regarde ? »** (un profil par abonnement), avatars, code PIN
- **Accueil, Films, Séries** : Top 20 « Tendances de la semaine » (TMDB), Les mieux notés, Reprendre la lecture, Favoris, sagas, genres, années, catégories
- **Fiches détaillées** TMDB : affiches, fonds, résumé, distribution, bande-annonce, résumés des épisodes
- **TV Direct** : aperçu vidéo, programme en cours, Matchs du jour, **guide TV** et **replay**
- **Lecteur** (Media3/ExoPlayer) : reprendre ou recommencer, langue audio, sous-titres, format d'image, passer l'intro, épisode suivant automatique, zapping, gestes tactiles, reconnexion automatique du direct
- **Synchronisation** de la reprise de lecture et des favoris entre appareils, sans compte
- Recherche instantanée, catégories adultes masquées par défaut
- Mise à jour intégrée

## Télécharger

Dernière version : **[NovaTV.apk](https://github.com/BDBOFF/NovaTv/releases/latest/download/NovaTV.apk)**

Installation sur la TV : app **Downloader** (entrer le lien ci-dessus), clé USB + gestionnaire de fichiers, ou **Send Files to TV**.
Autoriser l'installation d'applications inconnues pour l'app utilisée (Paramètres → Sécurité / Applications).
Une fois installée, l'appli propose elle-même les nouvelles versions.

## Télécommande

| Touche | Dans les menus | Dans le lecteur |
|---|---|---|
| Flèches | Navigation | Gauche/droite : −10 s / +10 s (maintenir pour accélérer). Haut/bas en direct : chaîne précédente/suivante |
| OK | Ouvrir | Pause / lecture, ou afficher les commandes |
| OK maintenu | Ajouter / retirer des favoris | — |
| Retour | Revenir | Quitter le lecteur |
| CH+ / CH− | — | Zapping |

## Fabrication

L'APK est fabriqué par **GitHub Actions** à chaque envoi sur `main` (`.github/workflows/build.yml`) et publié comme release `build-N`.
La clé de signature et la clé TMDB sont fournies par les **secrets du dépôt** (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `TMDB_API_KEY`) : aucune n'est stockée dans le code.

En local (Android Studio Ladybug ou plus récent) : `./gradlew assembleRelease`, avec ces trois valeurs fournies en propriétés Gradle ou variables d'environnement.

## Structure

```
app/src/main/java/com/nova/tv/
  data/        Xtream, M3U, TMDB, stockage, synchro, licence, mise à jour
  ui/          Thème, navigation, composants (tuiles, rangées, focus)
  ui/screens/  Accueil, Films/Séries, Fiches, TV Direct, Guide, Recherche, Réglages, Connexion, Profils, Licence
  player/      Lecteur Media3 et ses commandes
```

Kotlin 2.1 · Jetpack Compose · Media3 1.5 · Coil · OkHttp · minSdk 21.

Notes de travail pour les assistants : voir [CLAUDE.md](CLAUDE.md).
