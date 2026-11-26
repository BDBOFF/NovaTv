#!/usr/bin/env python3
"""
Garde-fou anti-secrets (hook PreToolUse de Claude Code).

Le dépôt SamothTV est PUBLIC : on bloque toute écriture ou tout commit qui
contiendrait un mot de passe, une clé, un code de licence ou un fichier de signature.
Code de sortie 2 = action bloquée, le message est renvoyé à l'assistant.
"""
import hashlib
import json
import os
import re
import subprocess
import sys

# Empreintes SHA-256 de secrets connus (les valeurs elles-mêmes ne sont jamais écrites ici)
KNOWN_SECRET_HASHES = {
    "f87cb467617d002e4f4c5b2f2b9475c47aa2ba9b3a6d4d5b426700e6882c7157",  # mot de passe du keystore
    "1807d49789777a833ab07b1ce86752347ebb53b1b20e4f4be7e0b13ffe6f03a6",  # ancienne clé TMDB
    "75219cf0f705ce2bce7f7ac1394f6650f131b80153e88fafdc4c49b0fb63e802",  # adresse e-mail personnelle du propriétaire
}

PATTERNS = [
    (r"SAMOTH-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}", "code propriétaire / code cadeau SAMOTH"),
    (r"(?i)(tmdb|api[_-]?key)[^\n]{0,40}?\b[0-9a-f]{32}\b", "clé API TMDB"),
    (r"(?i)(store|key)Password\s*=\s*\"[^\"$]+\"", "mot de passe de signature écrit en clair"),
    (r"-----BEGIN [A-Z ]*PRIVATE KEY-----", "clé privée"),
    (r"\bgh[pousr]_[A-Za-z0-9]{30,}\b|\bgithub_pat_[A-Za-z0-9_]{30,}", "jeton GitHub"),
    (r"\bsk_(live|test)_[A-Za-z0-9]{16,}", "clé secrète de paiement"),
    (r"(?i)lemon[^\n]{0,30}eyJ[A-Za-z0-9_-]{20,}", "clé API Lemon Squeezy"),
]

SECRET_FILE = re.compile(r"(?i)\.(jks|keystore|p12|pfx|pem)$|(^|/)(local\.properties|\.env)$|KEYSTORE_BASE64")

# Fichiers qui ont le droit de décrire les motifs (ce script lui-même)
ALLOWED_FILES = (".claude/hooks/guard_secrets.py",)


def find_problems(text: str) -> list:
    problems = []
    for rx, label in PATTERNS:
        if re.search(rx, text):
            problems.append(label)
    for tok in set(re.findall(r"[A-Za-z0-9_\-]{6,64}", text)) | set(re.findall(r"[\w.+-]+@[\w-]+\.[\w.]+", text)):
        if hashlib.sha256(tok.encode()).hexdigest() in KNOWN_SECRET_HASHES:
            problems.append("secret connu du projet (mot de passe ou clé)")
            break
    return problems


def git(cwd, *args) -> str:
    try:
        return subprocess.run(["git", "-C", cwd, *args], capture_output=True, text=True, timeout=20).stdout
    except Exception:
        return ""


def pending_changes(cwd: str) -> tuple:
    """Ce qu'un `git add -A && git commit` enverrait : modifications + fichiers nouveaux."""
    text = git(cwd, "diff", "HEAD")
    bad_files = []
    for f in git(cwd, "ls-files", "--others", "--exclude-standard").splitlines():
        if f.endswith(ALLOWED_FILES):
            continue
        if SECRET_FILE.search(f):
            bad_files.append(f)
            continue
        p = os.path.join(cwd, f)
        try:
            if os.path.getsize(p) < 1_000_000:
                with open(p, encoding="utf-8", errors="ignore") as fh:
                    text += "\n" + fh.read()
        except OSError:
            pass
    # le script de garde contient la description des motifs : on retire son diff
    text = re.sub(r"diff --git a/\.claude/hooks/guard_secrets\.py.*?(?=\ndiff --git |\Z)", "", text, flags=re.S)
    return text, bad_files


def block(reasons):
    print(
        "BLOQUÉ par le garde-fou anti-secrets (dépôt public) : " + ", ".join(sorted(set(reasons))) + ".\n"
        "Retire la valeur sensible : elle doit passer par les secrets GitHub Actions "
        "(KEYSTORE_BASE64, KEYSTORE_PASSWORD, TMDB_API_KEY) ou rester hors du dépôt.",
        file=sys.stderr,
    )
    sys.exit(2)


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        sys.exit(0)
    tool = data.get("tool_name", "")
    ti = data.get("tool_input", {}) or {}
    cwd = data.get("cwd") or os.getcwd()

    if tool in ("Write", "Edit", "MultiEdit"):
        path = ti.get("file_path", "")
        if path.endswith(ALLOWED_FILES):
            sys.exit(0)
        if SECRET_FILE.search(path) and git(os.path.dirname(path) or cwd, "rev-parse", "--is-inside-work-tree").strip() == "true":
            if not git(os.path.dirname(path) or cwd, "check-ignore", path).strip():
                block(["fichier sensible dans le dépôt (" + os.path.basename(path) + ")"])
        text = ti.get("content", "") + ti.get("new_string", "")
        for e in ti.get("edits", []) or []:
            text += "\n" + e.get("new_string", "")
        problems = find_problems(text)
        if problems:
            block(problems)

    elif tool == "Bash":
        cmd = ti.get("command", "")
        if re.search(r"git\s+add\b[^\n;&|]*\s-f\b", cmd) or re.search(r"git\s+add\b[^\n;&|]*\.(jks|keystore)\b", cmd):
            block(["ajout forcé d'un fichier ignoré ou d'un fichier de signature"])
        if re.search(r"\bgit\b[^\n]*\b(commit|push)\b", cmd):
            text, bad_files = pending_changes(cwd)
            if "push" in cmd:
                text += git(cwd, "log", "-p", "@{u}..HEAD")
            reasons = find_problems(text) + [f"fichier sensible : {f}" for f in bad_files]
            if reasons:
                block(reasons)
    sys.exit(0)


if __name__ == "__main__":
    main()
