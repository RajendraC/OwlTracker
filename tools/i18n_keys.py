#!/usr/bin/env python3
"""
Lists every text the app shows (the first argument of each tr("…") call) and checks the language files.

  python3 tools/i18n_keys.py            writes i18n/template.json (every text, empty translation) and, for each
                                        app/src/main/assets/i18n/<code>.json, prints what's missing or no longer used
  python3 tools/i18n_keys.py --fill hi  also adds the missing texts to hi.json with empty translations
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app/src/main/kotlin')
LANG_DIR = os.path.join(ROOT, 'app/src/main/assets/i18n')
CALL = re.compile(r'\btr\(\s*"((?:[^"\\]|\\.)*)"')
PLURAL = re.compile(r'\btrn\([^"]*?"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"')


def unescape(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == '\\' and i + 1 < len(s):
            n = s[i + 1]
            if n == 'u':
                out.append(chr(int(s[i + 2:i + 6], 16))); i += 6; continue
            out.append({'n': '\n', 't': '\t', 'r': '\r', '"': '"', "'": "'", '\\': '\\', '$': '$', 'b': '\b'}.get(n, n)); i += 2; continue
        out.append(c); i += 1
    return ''.join(out)


CATEGORY = re.compile(r'\b(?:e|SeedCategory)\(Cat\.\w+,\s*"((?:[^"\\]|\\.)*)"')


def keys():
    found = {}
    # built-in category names (shown through categoryLabel)
    db = open(os.path.join(SRC, 'com/owl/tracker/data/Db.kt'), encoding='utf-8').read()
    for m in CATEGORY.finditer(db):
        found.setdefault(unescape(m.group(1)), 'com/owl/tracker/data/Db.kt')
    for path in glob.glob(os.path.join(SRC, '**/*.kt'), recursive=True):
        text = open(path, encoding='utf-8').read()
        for m in CALL.finditer(text):
            k = unescape(m.group(1))
            found.setdefault(k, os.path.relpath(path, SRC))
        for m in PLURAL.finditer(text):
            for g in (1, 2):
                found.setdefault(unescape(m.group(g)), os.path.relpath(path, SRC))
    return found


def main():
    ks = keys()
    os.makedirs(os.path.join(ROOT, 'i18n'), exist_ok=True)
    with open(os.path.join(ROOT, 'i18n/template.json'), 'w', encoding='utf-8') as f:
        json.dump({k: '' for k in sorted(ks)}, f, ensure_ascii=False, indent=1)
    print(f'{len(ks)} texts → i18n/template.json')
    fill = sys.argv[sys.argv.index('--fill') + 1] if '--fill' in sys.argv else None
    for path in sorted(glob.glob(os.path.join(LANG_DIR, '*.json'))):
        code = os.path.basename(path)[:-5]
        table = json.load(open(path, encoding='utf-8'))
        missing = [k for k in ks if not table.get(k)]
        stale = [k for k in table if k not in ks and not k.startswith('//')]
        print(f'{code}: {len(ks) - len(missing)} of {len(ks)} translated, {len(missing)} missing, {len(stale)} no longer used')
        if fill == code:
            for k in missing:
                table.setdefault(k, '')
            with open(path, 'w', encoding='utf-8') as f:
                json.dump(table, f, ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
