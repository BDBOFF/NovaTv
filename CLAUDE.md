# SamothTV — carnet de passation

Ce fichier sert à reprendre le projet sans rien redemander. **Lis-le en entier avant de toucher au code.**

## ⚠️ Règle permanente

À la fin de **chaque** modification, sans attendre qu'on le demande :

1. `git add -A`, commit, puis **push sur `main`**.
2. **Surveiller la fabrication GitHub Actions jusqu'au bout** (succès ou échec). En cas d'échec, corriger et recommencer jusqu'au vert.
3. **Mettre à jour ce fichier CLAUDE.md** (nouvelles décisions, bugs corrigés, pièges, ce qui reste à faire), puis commit + push.

## Le propriétaire et ses préférences

- Toujours répondre **en français**, avec des **explications simples**. Il n'est pas développeur : pas de jargon, des étapes claires (« clique ici, puis là »).
- Il veut un résultat qui marche, pas un récit du travail. Réponses courtes, une prochaine étape concrète si utile.
- Il teste sur de vrais appareils : box SFR Connect TV (Android 12), TV Sony (salon), une TV dans la chambre, un téléphone Android. Il a un iPhone (sert à scanner les QR codes).
- Il possède une micro-entreprise (ML Concept) et veut vendre l'appli sur son propre site.
- **Rien ne doit être mis en ligne** (site, boutique) tant qu'il ne l'a pas dit explicitement.
- Ne jamais écrire de mot de passe, jeton, clé, code propriétaire ou code cadeau dans un fichier du dépôt ou dans un message public : **le dépôt est public**.

## Ce que fait l'appli

SamothTV (ancien nom de code : Nova TV, d'où le paquet `com.nova.tv`) est un **lecteur IPTV** pour **Android TV, tablette et téléphone** (un seul APK qui s'adapte à l'écran). Elle ne fournit aucun contenu : l'utilisateur apporte son abonnement.

- Connexion **Xtream Codes** ou **lien M3U/M3U8** (+ guide XMLTV facultatif).
- **Saisie depuis le téléphone par QR code** : la TV lance un petit serveur web local, le téléphone (même iPhone) remplit un formulaire. Sert pour la connexion et pour la clé de licence (les claviers TV sont pénibles, et sur la Sony du salon le clavier ne s'affichait pas).
- **Profils « Qui regarde ? »** : 1 profil = 1 abonnement IPTV. Appui long sur un profil = menu (couleur + emoji d'avatar, modifier les identifiants, code PIN, recharger, supprimer).
- Thème **rouge sang**, logo **S-TV**, nom affiché sur une seule ligne.
- Accueil, Films, Séries : rangées dans cet ordre — **Top 20 « Tendances de la semaine »** (TMDB, avec gros numéros de classement, style « Néon rouge » par défaut, styles Classique/Contour en option), **Les mieux notés**, **Reprendre la lecture**, **Favoris**, puis catégories. Les tendances incluent des titres absents du catalogue (badge BIENTÔT, ajout aux favoris, passe à DISPONIBLE quand le titre arrive).
- **TV Direct** : page d'accueil en rangées, aperçu vidéo en direct, « Matchs du jour », guide TV, replay.
- Sagas, genres, années ; recherche instantanée ; fiches avec métadonnées **TMDB** (affiches, résumé, casting, bande-annonce, résumés des épisodes).
- Lecteur : reprendre ou recommencer (fenêtre de choix + bouton ↻), OK = pause/lecture, carte « épisode suivant », passer le générique, gestes tactiles.
- **Appui long OK** sur une tuile = ajouter/retirer des favoris.
- **Synchronisation cloud** (Firebase Realtime Database, sans compte) de la reprise de lecture et des favoris entre appareils, identifiée par une empreinte des identifiants IPTV.
- **Un seul flux par abonnement** : fenêtre d'avertissement si un autre profil du même abonnement regarde déjà.
- **Licence** : essai gratuit 3 jours, puis clé achetée (Lemon Squeezy), code propriétaire et 10 codes cadeau « à vie » (seules leurs empreintes SHA-256 sont dans le code).
- **Mise à jour intégrée** : l'appli vérifie la dernière release GitHub et propose d'installer le nouvel APK.

## Organisation du code

Kotlin 2.1, Jetpack Compose (BOM 2024.12.01), Media3 1.5.1 (ExoPlayer + OkHttp), Coil 2.7, kotlinx.serialization, OkHttp, zxing core (QR codes), profileinstaller. AGP 8.7.3, Gradle 8.11.1, **minSdk 21**, compileSdk 35, R8 activé.

Tout est sous `app/src/main/java/com/nova/tv/` :

| Fichier | Rôle |
|---|---|
| `AppViewModel.kt` | Cœur de l'appli : session, catalogue, index, rangées, navigation (`backStack`), lecture, favoris, profils, licence, mise à jour. `connect()` gère le démarrage. |
| `MainActivity.kt`, `NovaApp.kt` | Point d'entrée, réglages Coil (rgb565). |
| `data/Source.kt` | Interface commune Xtream / M3U. |
| `data/XtreamClient.kt` | API Xtream, cache disque JSON (`CATALOG_TTL` = 6 h). |
| `data/M3uClient.kt` | Lecture M3U + XMLTV. |
| `data/CatalogSnapshot.kt` | Copie binaire du catalogue pour un démarrage instantané (Xtream uniquement). |
| `data/Models.kt` | Modèles (Account, Movie, Series, Channel, Fav, History, PlayItem, `Kind`…). |
| `data/Storage.kt` | Préférences, comptes, favoris, historique ; notifie la synchro. |
| `data/Sync.kt` | Synchro Firebase (PATCH multi-chemins, fusion local/distant). |
| `data/License.kt` | Essai, clés Lemon Squeezy, code propriétaire, codes cadeau, offres affichées. |
| `data/PhoneForm.kt` | Serveur HTTP local (ports 8642-8644, jeton) pour la saisie par téléphone. |
| `data/Tmdb.kt`, `data/Explore.kt` | TMDB (tendances, détails, sagas, saisons) et pages d'exploration. |
| `data/Updater.kt` | Mise à jour depuis les releases GitHub (constante `REPO`). |
| `data/Util.kt` | Client HTTP, helpers JSON, `Titles` (nettoyage, normalisation des titres). |
| `ui/AppRoot.kt` | Routes, verrou de licence, fenêtres globales (flux occupé, reprise). |
| `ui/Device.kt` | Détection TV / tablette / téléphone (`LocalDevice`, marges `LocalGutter`). |
| `ui/Theme.kt`, `ui/Tiles.kt` | Couleurs, typographie, tuiles. |
| `ui/components/Components.kt` | `FocusBox` (focus, mémoire de focus, appui long), `LongPressGuard`, `trapFocus()`, boutons. |
| `ui/screens/*` | Écrans (Main, Home, Catalog, Details, Live, LiveHome, Guide, Search, Settings, Login, License, Profiles, Explore, Mobile). |
| `player/PlayerScreen.kt`, `player/LivePreview.kt` | Lecteur plein écran et aperçu du direct. |

Le dossier `site/` contient le **brouillon** du site de vente (Samoth TV + Samoth HOME). Il n'est **pas** publié.

## Fabriquer et publier l'APK

- **On ne compile pas en local** (l'environnement de l'assistant n'accède pas au dépôt Maven de Google). Tout passe par **GitHub Actions** (`.github/workflows/build.yml`).
- Chaque push sur `main` lance la fabrication. Le numéro de version = numéro d'exécution GitHub (`-PBUILD_NUMBER`), `versionName` = `1.<numéro>`.
- Si ça réussit, une release `build-N` est créée avec `NovaTV.apk`. Lien fixe toujours valable : `https://github.com/BDBOFF/novatv/releases/latest/download/NovaTV.apk`. L'appli installée se met à jour toute seule via ce mécanisme.
- Suivre une fabrication :
  ```bash
  gh api repos/BDBOFF/NovaTv/actions/runs --jq '.workflow_runs[0] | "\(.head_sha[0:7]) \(.status) \(.conclusion) \(.id)"'
  ```
  Les journaux complets ne sont pas téléchargeables depuis l'environnement de l'assistant : en cas d'échec, il faut relire le code modifié et déduire l'erreur (souvent un import manquant ou un smart cast impossible).
- Une vérification syntaxique locale est possible avec `kotlinc`, mais sans les bibliothèques Android elle affiche beaucoup de fausses erreurs (« unresolved reference » sur Compose/coroutines) : ne regarder que les erreurs dans les lignes modifiées.
- Commits signés `BDBOFF`.
- **Secrets GitHub Actions** (Settings > Secrets and variables > Actions) : `KEYSTORE_BASE64` (fichier de signature encodé en base64), `KEYSTORE_PASSWORD` (mot de passe du fichier et de la clé, alias `nova`), `TMDB_API_KEY`. Le workflow recrée `app/nova-release.jks` à partir du secret et s'arrête avec un message clair s'il en manque un. Ni le `.jks` ni les mots de passe ne doivent revenir dans le dépôt (`*.jks` est dans `.gitignore`). L'assistant ne peut pas lire ni écrire ces secrets (accès bloqué) : c'est le propriétaire qui les saisit.
- **Ne jamais changer de fichier de signature** sans prévenir : sinon l'appli installée refuse la mise à jour et il faut la désinstaller sur chaque appareil.

## Décisions prises (et pourquoi)

- **Un seul APK** TV + téléphone + tablette, qui s'adapte à l'écran : plus simple à distribuer et à maintenir.
- **Distribution par lien GitHub public** (dépôt public + release) plutôt que Google Drive ou X-plore : lien fixe, mise à jour intégrée.
- **Clé TMDB intégrée au code**, invisible dans les réglages : demande explicite du propriétaire (« elle doit apparaître nulle part »). Elle est injectée au build via `BuildConfig`.
- **Vente sur son propre site via Lemon Squeezy** (pas le Play Store pour l'instant). Prix : 2,99 € / 1 mois, 6,99 € / 3 mois, 11,99 € / 6 mois, 19,99 € / 1 an (mis en avant), 34,99 € à vie ; essai 3 jours ; une licence = 2 appareils. Les clés sont vérifiées directement auprès de Lemon Squeezy, sans serveur à nous ; tolérance hors ligne de 14 jours.
- **Synchro sans compte** : l'identifiant cloud est une empreinte SHA-256 des identifiants IPTV, donc tous les appareils du même abonnement partagent automatiquement reprise et favoris.
- **1 profil = 1 abonnement** et garde « un seul flux » : la plupart des abonnements n'autorisent qu'une connexion simultanée.
- **Démarrage rapide** : au lancement, on relit une copie binaire du catalogue et on affiche l'accueil tout de suite ; la vérification du compte et le rafraîchissement (si plus de 6 h) se font en arrière-plan, et l'écran n'est mis à jour que si le catalogue a vraiment changé.
- **Android Auto (vidéo)** : refus de contourner les restrictions d'Android Auto (bidouille « appli de navigation »). On ajoutera la vidéo à l'arrêt **officielle** quand Google la proposera (Android 17, voitures compatibles). Le propriétaire n'était pas content de ce refus après une première réponse plus ouverte : rester cohérent et clair sur ce point.
- **Organisation GitHub** : conseil de créer une organisation « SamothTV » (plutôt qu'un second compte) et d'y transférer les dépôts. C'est au propriétaire de faire le transfert.

## Bugs déjà corrigés — pièges à éviter

- **minSdk 21** : ne pas utiliser d'API Java 8 qui demandent l'API 24 (`Map.merge`, `putIfAbsent`, `ConcurrentHashMap.newKeySet`, `java.time`…). Utiliser des équivalents simples.
- `by mutableLongStateOf` / `mutableStateOf` hors composable : penser aux imports `getValue` / `setValue`, sinon la fabrication échoue.
- `buildMap` : préciser les types (`buildMap<String, String>`), l'inférence échoue.
- **Smart cast** sur une variable nullable vérifiée indirectement : le compilateur refuse, il faut tester la variable elle-même (cas de `snap` dans `connect()`).
- **Appui long sur TV** : la répétition de touche validait ensuite le premier élément du menu → `LongPressGuard` consomme les touches à la racine jusqu'au relâchement.
- **Fenêtres de dialogue** : les flèches déplaçaient le focus derrière la fenêtre → modificateur `trapFocus()` sur toutes les fenêtres modales.
- **OK ne relançait pas la lecture après une pause** (la barre de progression avait le focus) → la `SeekBar` gère OK/Entrée comme pause/lecture.
- **L'ordre des profils changeait** à la connexion → `upsertAccount` remplace le compte à sa place.
- **Nom coupé sur l'écran de démarrage du téléphone** → texte sur une seule ligne, plus petit en mode compact.
- **Recherche lente (~15 s) et navigation saccadée** → index de recherche précalculé, rangées calculées hors du fil principal et mises en cache, changement d'onglet temporisé (320 ms), images en rgb565.
- **Démarrage d'environ 1 minute** → copie binaire du catalogue + rafraîchissement en arrière-plan + `Titles.normalize` sans regex. Le premier lancement après une installation reste lent une fois (création de la copie). Les playlists M3U ne profitent pas encore de l'accélération.
- **Erreur HTTP 512 sur le téléphone au travail** : blocage du fournisseur ou du réseau, pas un bug de l'appli.

## Ce qui reste à faire

- **Boutique Lemon Squeezy** : quand le propriétaire l'aura créée, renseigner `STORE_ID` et `BUY_URL` dans `data/License.kt` et les liens d'achat du site.
- **Organisation GitHub SamothTV** : après le transfert des dépôts, mettre à jour `REPO` dans `data/Updater.kt`, le lien fixe de l'APK, le workflow et l'adresse du site (`samothtv.github.io`). Ancien chemin `BDBOFF/novatv` : GitHub redirige, mais à remplacer.
- **Site** : prix de Samoth HOME à confirmer (marqué « Gratuit » pour l'instant), page de confidentialité qui mentionne Firebase. **Ne rien publier sans accord.**
- **Sécurité du dépôt public** : le passage aux secrets GitHub est fusionné dans `main` (fichier de signature, mot de passe et clé TMDB retirés du dépôt). **En cours (17/11/2026)** : les fabrications 36 et 37 échouent car GitHub ne trouve aucun des 3 secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `TMDB_API_KEY`), alors que le propriétaire pense les avoir créés : probablement au mauvais endroit (onglet Variables, Codespaces, Dependabot, secrets d'environnement, ou réglages du compte au lieu du dépôt). Ils doivent être dans **dépôt BDBOFF/NovaTv > Settings > Secrets and variables > Actions > onglet Secrets > Repository secrets**. Une fois corrigés, relancer la fabrication (nouveau push ou « Re-run jobs »). La dernière APK publiée reste la build 34. Les anciennes versions restent lisibles dans l'historique Git : pour une vraie protection, il faudrait plus tard une nouvelle clé TMDB (site TMDB) et éventuellement un nouveau fichier de signature (réinstallation sur tous les appareils), à proposer avant le début des ventes.
- **Démarrage rapide pour M3U** (copie binaire non utilisée car `M3uClient` garde ses URL en interne).
- **Android Auto** : vidéo à l'arrêt officielle quand l'API Google sera disponible.
- Mettre à jour les actions GitHub (`setup-java@v5`, Node 24) avant qu'elles ne cassent.
