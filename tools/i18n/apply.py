"""
Writes approved wording from the DistriGo Translation Manager back into the app's resources.

Input: a JSON list exported from the tool's review store, one item per approved text:
    [{"lang": "ar", "key": "acc_activate", "kind": "string", "value": "تفعيل"},
     {"lang": "ar", "key": "common_clients_count", "kind": "plural", "value": {"zero": "...", "one": "...", ...}}]
Values are plain text, as the owner typed them; this script adds Android's escapes.

DRY RUN BY DEFAULT: it prints what would change and touches nothing. Only `--write`, given on the owner's word,
changes files, and only in values-<lang> folders that already exist (a new language is a separate decision).

    python tools/i18n/apply.py approved.json            # show the changes
    python tools/i18n/apply.py approved.json --write    # write them
"""
import json
import os
import re
import sys
from html import escape

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(REPO, "app", "src", "main", "res")
QUANTITIES = ["zero", "one", "two", "few", "many", "other"]


def android(text):
    """Plain text -> Android resource text: XML-escaped, with \\ ' " and new lines escaped."""
    t = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if t.startswith("@") or t.startswith("?"):
        t = "\\" + t
    return escape(t, quote=False)


def folder(lang):
    return "values-" + lang.replace("-", "-r")


def find(files, tag, key):
    pattern = re.compile(r'(<%s name="%s"(?: [^>]*)?>)(.*?)(</%s>)' % (tag, re.escape(key), tag), re.S)
    for path in files:
        s = open(path, encoding="utf-8").read()
        m = pattern.search(s)
        if m:
            return path, s, m
    return None, None, None


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    write = "--write" in sys.argv
    items = json.load(open(sys.argv[1], encoding="utf-8"))
    changed, problems, edits = 0, [], {}
    for item in items:
        lang, key, kind, value = item["lang"], item["key"], item["kind"], item["value"]
        d = os.path.join(RES, folder(lang))
        if not os.path.isdir(d):
            problems.append(f"{lang}: no {folder(lang)} folder in the app; adding a language is a separate decision")
            continue
        files = [os.path.join(d, f) for f in sorted(os.listdir(d)) if f.startswith("strings") and f.endswith(".xml")]
        files = [edits.get(f, (f,))[0] for f in files]
        tag = "plurals" if kind == "plural" else "string-array" if kind == "array" else "string"
        path, s, m = find(files, tag, key)
        if path in edits:
            s = edits[path][1]
            m = re.compile(r'(<%s name="%s"(?: [^>]*)?>)(.*?)(</%s>)' % (tag, re.escape(key), tag), re.S).search(s)
        if not m:
            problems.append(f"{lang}: {key} not found in {folder(lang)}")
            continue
        if kind == "plural":
            indent = re.search(r"\n(\s*)<item", m.group(2))
            pad = indent.group(1) if indent else "        "
            close = re.search(r"\n(\s*)$", m.group(2))
            body = "".join(f'\n{pad}<item quantity="{q}">{android(value[q])}</item>' for q in QUANTITIES if q in value)
            new = body + ("\n" + close.group(1) if close else "\n    ")
        elif kind == "array":
            indent = re.search(r"\n(\s*)<item", m.group(2))
            pad = indent.group(1) if indent else "        "
            new = "".join(f"\n{pad}<item>{android(v)}</item>" for v in value) + "\n    "
        else:
            new = android(value)
        if new == m.group(2):
            continue
        print(f"--- {lang} {key} ({os.path.basename(path)})\n  was: {m.group(2).strip()}\n  now: {new.strip()}")
        s = s[:m.start(2)] + new + s[m.end(2):]
        edits[path] = (path, s)
        changed += 1
    for p in problems:
        print("!!", p)
    if write:
        for path, s in edits.values():
            open(path, "w", encoding="utf-8").write(s)
        print(f"{changed} text(s) written to {len(edits)} file(s).")
    else:
        print(f"Dry run: {changed} text(s) would change in {len(edits)} file(s). Nothing was written.")


if __name__ == "__main__":
    main()
