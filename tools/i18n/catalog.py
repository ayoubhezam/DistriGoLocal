"""
Builds the snapshot the DistriGo Translation Manager reads: every text of the app, in the base language (French)
and in every translation found under app/src/main/res/values-<lang>/strings*.xml, grouped by screen (the
strings_<screen>.xml file it comes from).

Read-only for the app: it never writes a resource file. Approved wording goes back through apply.py, and only on the
owner's word.

    python tools/i18n/catalog.py [out.json]
"""
import glob
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(REPO, "app", "src", "main", "res")
GLOSSARY = os.path.join(REPO, "docs", "i18n_glossary.md")
BASE = "fr"

# Android's resource qualifiers for the app's languages: values = the base, values-ar = Arabic. A new language is a
# new values-<code> folder; nothing here changes.
FOLDER = re.compile(r"^values(?:-([a-z]{2,3}(?:-r[A-Z]{2})?|b\+[A-Za-z+]+))?$")


def text_of(element):
    """The resource's text as Android stores it, escapes kept (\\' \\n \\")."""
    return "".join(element.itertext())


def read_folder(folder):
    strings = {}
    for path in sorted(glob.glob(os.path.join(RES, folder, "strings*.xml"))):
        screen = os.path.basename(path)[len("strings"):-len(".xml")].lstrip("_") or "general"
        root = ET.parse(path).getroot()
        for e in root:
            if e.get("translatable") == "false":
                continue
            name = e.get("name")
            if e.tag == "string":
                strings[name] = {"screen": screen, "kind": "string", "text": text_of(e)}
            elif e.tag == "plurals":
                strings[name] = {"screen": screen, "kind": "plural",
                                 "text": {i.get("quantity"): text_of(i) for i in e if i.tag == "item"}}
            elif e.tag == "string-array":
                strings[name] = {"screen": screen, "kind": "array", "text": [text_of(i) for i in e]}
    return strings


def glossary():
    """The approved glossary (docs/i18n_glossary.md): « | # | French | Arabic | Note | » rows under « ## » sections."""
    if not os.path.exists(GLOSSARY):
        return []
    rows, section = [], ""
    for line in open(GLOSSARY, encoding="utf-8"):
        if line.startswith("## "):
            section = re.sub(r"^\d+\.\s*", "", line[3:].strip())
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) >= 3 and cells[0].isdigit():
            rows.append({"n": int(cells[0]), "section": section, "fr": cells[1], "ar": cells[2],
                         "note": cells[3] if len(cells) > 3 else ""})
    return rows


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "tools", "i18n", "catalog.json")
    folders = sorted(d for d in os.listdir(RES) if FOLDER.match(d) and glob.glob(os.path.join(RES, d, "strings*.xml")))
    base = read_folder("values")
    languages, translations = [], {}
    for folder in folders:
        if folder == "values":
            continue
        code = FOLDER.match(folder).group(1).replace("-r", "-")
        languages.append({"code": code, "folder": folder})
        translations[code] = read_folder(folder)
    entries = []
    for key, b in sorted(base.items(), key=lambda kv: (kv[1]["screen"], kv[0])):
        entries.append({
            "key": key, "screen": b["screen"], "kind": b["kind"], "base": b["text"],
            "tr": {code: t[key]["text"] for code, t in translations.items() if key in t},
        })
    orphans = {code: sorted(k for k in t if k not in base) for code, t in translations.items()}
    try:
        commit = subprocess.check_output(["git", "-C", REPO, "rev-parse", "--short", "HEAD"], text=True).strip()
        dirty = bool(subprocess.check_output(["git", "-C", REPO, "status", "--porcelain", "--", "app/src/main/res"], text=True).strip())
    except Exception:
        commit, dirty = "", False
    catalog = {
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "commit": commit + ("+local" if dirty else ""),
        "base": BASE,
        "languages": languages,
        "entries": entries,
        "orphans": orphans,
        "glossary": glossary(),
    }
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(entries)} texts, languages {[l['code'] for l in languages]}, glossary {len(catalog['glossary'])} terms -> {out}")


if __name__ == "__main__":
    main()
