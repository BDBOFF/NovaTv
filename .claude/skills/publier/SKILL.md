---
name: publier
description: Publie une modification de SamothTV de bout en bout - relecture, commit, push sur main, surveillance de la fabrication GitHub jusqu'au vert, mise à jour de CLAUDE.md. À utiliser à la fin de chaque modification du code, sans attendre qu'on le demande.
---

# /publier — envoyer une modification jusqu'à l'APK

C'est la règle permanente de CLAUDE.md, toujours faite de la même façon.

## 1. Relire avant d'envoyer

- Lance le subagent **relecteur** sur les changements (`git diff HEAD` + fichiers nouveaux). Corrige tout ce qu'il signale comme bloquant.
- Le hook `check_kotlin.py` doit être muet sur les fichiers `.kt` modifiés.

## 2. Commit et push sur main

```bash
cd /chemin/du/depot && git pull -q --rebase
git add -A
git -c user.name="BDBOFF" -c user.email="samothc4t@users.noreply.github.com" commit -q -m "<résumé en français>

<lignes d'attribution demandées par la session>"
git push -q origin main
```

Le hook `guard_secrets.py` bloque le commit s'il voit un secret : ne jamais le contourner, retirer la valeur.

## 3. Surveiller la fabrication jusqu'au bout

```bash
export GH_TOKEN=
sleep 8
id=$(gh api "repos/BDBOFF/NovaTv/actions/runs?branch=main" --jq '.workflow_runs[0].id')
for i in $(seq 1 55); do
  s=$(gh api repos/BDBOFF/NovaTv/actions/runs/$id --jq '"\(.head_sha[0:7]) \(.status) \(.conclusion) \(.run_number)"')
  case "$s" in *completed*) echo "$s"; break;; esac; sleep 10
done
```

Vérifie que `head_sha` correspond bien au commit envoyé.

## 4. Si la fabrication échoue

Les journaux complets ne sont pas téléchargeables. Récupère l'étape en échec et les annotations :

```bash
gh api repos/BDBOFF/NovaTv/actions/runs/$id/jobs --jq '.jobs[0].steps[] | "\(.name): \(.conclusion)"'
j=$(gh api repos/BDBOFF/NovaTv/actions/runs/$id/jobs --jq '.jobs[0].id')
gh api repos/BDBOFF/NovaTv/check-runs/$j/annotations --jq '.[].message' | grep -iv "node\|deprecat\|ubuntu"
```

- Étape « Préparer la clé de signature » en échec : un secret GitHub manque, c'est au propriétaire de le créer (dépôt BDBOFF/NovaTv > Settings > Secrets and variables > Actions > Repository secrets). Lui expliquer simplement.
- Étape « Compiler l'APK » en échec : erreur Kotlin. Relire le diff ligne par ligne (imports, smart cast sur variable nullable, types de `buildMap`, API trop récente pour minSdk 21), corriger, revenir à l'étape 2. Recommencer jusqu'au vert.

## 5. Vérifier la publication

```bash
gh api repos/BDBOFF/NovaTv/releases/latest --jq '"\(.tag_name) \(.assets[0].name) \(.assets[0].size)"'
```

La release doit être `build-<numéro de la fabrication>` avec `NovaTV.apk`.

## 6. Mettre à jour CLAUDE.md

Ajoute ce qui a changé : fonctionnalité, décision et sa raison, bug corrigé, nouveau piège, tâche faite ou restante. Puis commit + push (une nouvelle fabrication se lance, la surveiller aussi).

## 7. Prévenir le propriétaire

En français simple, court : ce qui change pour lui, le numéro de version (`build-N`), et que l'appli lui proposera la mise à jour au prochain lancement. Pas de récit technique.
