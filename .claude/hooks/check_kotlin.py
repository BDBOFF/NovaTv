#!/usr/bin/env python3
"""
Vérificateur Kotlin (hook PostToolUse de Claude Code), lancé après chaque modification d'un .kt.

On ne peut pas compiler en local : ce contrôle attrape les erreurs qui ont déjà fait
échouer la fabrication GitHub, avant d'envoyer le code.
- API Java trop récentes pour minSdk 21 (pas de desugaring) ;
- `by mutableStateOf` / `by remember` sans les imports getValue / setValue.
Ajouter `// minsdk-ok` en fin de ligne pour ignorer un faux positif.
Code de sortie 2 = le message est renvoyé à l'assistant pour qu'il corrige.
"""
import json
import re
import sys

API24 = [
    (r"\.merge\(", "Map.merge (API 24) : utiliser map[k] = (map[k] ?: 0) + 1"),
    (r"\.putIfAbsent\(", "putIfAbsent (API 24) : utiliser if (k !in map) map[k] = v"),
    (r"\.computeIfAbsent\(|\.computeIfPresent\(|\.compute\(", "Map.compute* (API 24) : utiliser getOrPut"),
    (r"newKeySet\(", "ConcurrentHashMap.newKeySet (API 24) : utiliser Collections.synchronizedSet(HashSet())"),
    (r"\.removeIf\s*[({]", "removeIf (API 24) : utiliser removeAll { } de Kotlin"),
    (r"\.stream\(\)", "Stream Java (API 24) : utiliser les fonctions de collection Kotlin"),
    (r"java\.util\.Optional|\bOptional<", "Optional (API 24)"),
    (r"java\.time\.", "java.time (API 26) : utiliser System.currentTimeMillis / Calendar / SimpleDateFormat"),
    (r"java\.util\.function\.", "java.util.function (API 24) : utiliser les lambdas Kotlin"),
    (r"\.replaceAll\s*\{", "List.replaceAll (API 24)"),
]


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        sys.exit(0)
    ti = data.get("tool_input", {}) or {}
    path = ti.get("file_path", "")
    if not path.endswith(".kt"):
        sys.exit(0)
    try:
        with open(path, encoding="utf-8") as fh:
            lines = fh.read().splitlines()
    except OSError:
        sys.exit(0)

    issues = []
    for n, line in enumerate(lines, 1):
        code = line.split("//")[0] if "minsdk-ok" not in line else ""
        for rx, msg in API24:
            if re.search(rx, code):
                issues.append(f"  ligne {n} : {msg}")

    src = "\n".join(lines)
    uses_delegate = re.search(r"\bby\s+(mutable\w*StateOf|remember|derivedStateOf|produceState|collectAsState)", src)
    if uses_delegate:
        for imp in ("getValue", "setValue"):
            if not re.search(rf"import androidx\.compose\.runtime\.({imp}|\*)\b", src):
                if imp == "setValue" and not re.search(r"\bvar\s+\w+\s+by\b", src):
                    continue
                issues.append(f"  import manquant : androidx.compose.runtime.{imp} (requis par `by ...`)")

    if issues:
        print(
            f"Vérificateur Kotlin — {path.split('/app/src/main/java/')[-1]} : à corriger avant le push "
            "(minSdk 21, sinon la fabrication GitHub échoue) :\n" + "\n".join(issues),
            file=sys.stderr,
        )
        sys.exit(2)
    sys.exit(0)


if __name__ == "__main__":
    main()
