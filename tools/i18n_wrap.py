#!/usr/bin/env python3
"""
Wraps the app's on-screen English text in tr(...), so it can be translated (see i18n/I18n.kt).

  "Added ${Money.formatInr(x)} to $name"   ->   tr("Added {0} to {1}", Money.formatInr(x), name)

Only text that reads like words for people is wrapped: at least one word of two letters or more, and a space or a
capital followed by small letters. Keys, tags, patterns, SQL and anything in a skipped context are left alone.
Text inside ${...} is looked at too. Run it again and it changes nothing (text already in tr() is skipped).

  python3 tools/i18n_wrap.py FILE...        rewrites the files in place, prints how many texts it wrapped
"""
import re
import sys

# Calls whose string arguments are never shown as text
SKIP_BEFORE = re.compile(
    r'\b(testTag|ofPattern|Regex|toRegex|getString|putString|getBoolean|putBoolean|getInt|putInt|getLong|putLong|'
    r'getStringSet|putStringSet|getFloat|putFloat|Log\.[a-z]|Intent|setType|setAction|Uri\.parse|parse|startsWith|'
    r'endsWith|split|removePrefix|removeSuffix|substringBefore|substringAfter|optString|optInt|optLong|optBoolean|'
    r'optJSONArray|getJSONArray|getJSONObject|has|put|error|require|requireNotNull|check|checkNotNull|Exception|'
    r'Error|TODO|createNotificationChannel|NotificationChannel|tr|trn|Font|fontFamily|getIdentifier|'
    r'getSharedPreferences|navigate|route|semanticsKey|key|withTag|hasTestTag|onNodeWithTag|contentType|'
    r'setClassName|ComponentName|SimpleDateFormat|DecimalFormat|String\.format|format|matches|equals|'
    r'Pattern\.compile|loadUrl|evaluateJavascript|addJavascriptInterface|File|openFileInput|openFileOutput|'
    r'assets\.open|mimeType|MimeTypeMap|Locale|forLanguageTag|ZoneId\.of|AndroidPath|vector|PathParser|svg)'
    r'\s*\(\s*(?:[^()"]*,\s*)?$'
)
SKIP_LINE = re.compile(r'^\s*(@|const val|package |import )|const val ')
WORD = re.compile(r'[A-Za-z]{2,}')
UPPER_LOWER = re.compile(r'(^|[^A-Za-z])[A-Z][a-z]')
# SQL and the like
SQLISH = re.compile(r'\b(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|WHERE|FROM|INTEGER|TEXT PRIMARY)\b')

count = 0


def unescape_len_safe(s):
    return s


def parse_string(src, i):
    """src[i] is the opening quote of a non-raw string. Returns (parts, end) where parts are ('t', text) / ('e', expr)."""
    assert src[i] == '"'
    i += 1
    parts = []
    buf = []
    while True:
        c = src[i]
        if c == '\\':
            buf.append(src[i:i + 2])
            i += 2
            continue
        if c == '"':
            if buf:
                parts.append(('t', ''.join(buf)))
            return parts, i + 1
        if c == '$' and i + 1 < len(src) and src[i + 1] == '{':
            if buf:
                parts.append(('t', ''.join(buf)))
                buf = []
            j = skip_code_until_brace(src, i + 2)
            parts.append(('e', src[i + 2:j], True))
            i = j + 1
            continue
        if c == '$' and i + 1 < len(src) and (src[i + 1].isalpha() or src[i + 1] == '_'):
            if buf:
                parts.append(('t', ''.join(buf)))
                buf = []
            j = i + 1
            while j < len(src) and (src[j].isalnum() or src[j] == '_'):
                j += 1
            parts.append(('e', src[i + 1:j], False))
            i = j
            continue
        if c == '\n':
            raise ValueError('newline in string at %d' % i)
        buf.append(c)
        i += 1


def skip_code_until_brace(src, i):
    """From inside ${, returns the index of the matching }."""
    depth = 0
    while i < len(src):
        c = src[i]
        if c == '"':
            if src.startswith('"""', i):
                i = src.index('"""', i + 3) + 3
                continue
            _, i = parse_string(src, i)
            continue
        if c == "'":
            i = skip_char(src, i)
            continue
        if c == '{':
            depth += 1
        elif c == '}':
            if depth == 0:
                return i
            depth -= 1
        i += 1
    raise ValueError('unclosed ${')


def skip_char(src, i):
    # 'a', '\n', '\u0000'
    if src[i + 1] == '\\':
        j = src.index("'", i + 2)
        return j + 1
    return i + 3


def reads_like_words(static):
    text = re.sub(r'\\[nt"\\$\']|\\u[0-9a-fA-F]{4}', ' ', static)
    if text.strip() == 'OK':
        return True
    if not WORD.search(text):
        return False
    # SVG path data, JSON or markup pieces
    if re.fullmatch(r'[MmLlHhVvCcSsQqTtAaZz0-9 .,\-]+', text) and re.search(r'\d', text):
        return False
    # patterns and qualified names
    if re.search(r'\\\\[sdwbS]|\(\?|\[\^', static) or re.fullmatch(r'\w+(\.\w+)+', text.strip()):
        return False
    if '\\"' in static and (':' in static or '=' in static) or re.search(r'</?[a-z]+[ >/]', text) or text.lstrip().startswith(('{', '}', ',', ';')):
        return False
    if SQLISH.search(text):
        return False
    if not re.search(r'[a-z]', text) and not re.fullmatch(r'[A-Z]{2,}( [A-Z&]{1,})+|OK', text.strip()):
        return False
    # file names, ids, paths and keys
    if re.fullmatch(r'[\w.\-/:%]+', text) and not UPPER_LOWER.search(text):
        return False
    if re.fullmatch(r'[a-z0-9_.\-]+', text.strip()) and ' ' not in text:
        return False
    if re.search(r'^(https?|mailto|market|content|file|package):', text):
        return False
    return ' ' in text or bool(UPPER_LOWER.search(text))


def process(src, in_tr_arg=False):
    global count
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            j = src.find('\n', i)
            j = n if j < 0 else j
            out.append(src[i:j])
            i = j
            continue
        if src.startswith('/*', i):
            j = src.index('*/', i + 2) + 2
            out.append(src[i:j])
            i = j
            continue
        if c == "'":
            j = skip_char(src, i)
            out.append(src[i:j])
            i = j
            continue
        if src.startswith('"""', i):
            j = src.index('"""', i + 3) + 3
            while j < n and src[j] == '"':
                j += 1
            out.append(src[i:j])
            i = j
            continue
        if c == '"':
            parts, j = parse_string(src, i)
            done = ''.join(out)
            line_start = done.rfind('\n') + 1
            line_so_far = done[line_start:]
            full_line = line_so_far + src[i:src.find('\n', i) if src.find('\n', i) >= 0 else n]
            static = ''.join(p[1] for p in parts if p[0] == 't')
            # Inside a wrapped text's argument, single small words count too: "payment" / "payments"
            small_word = in_tr_arg and len(parts) == 1 and re.fullmatch(r'[a-z]{2,}( [a-z]{2,})?', static or '') is not None
            # Text compared with == / != or used as a fallback for saved data stays as it is (it's data)
            data_like = re.search(r'(==|!=|ifBlank \{|ifEmpty \{|\?:)\s*$', line_so_far)
            wrap = ((reads_like_words(static) or small_word) and not data_like and not SKIP_BEFORE.search(line_so_far[-120:])
                    and not SKIP_LINE.search(full_line))
            if wrap:
                key = []
                args = []
                for p in parts:
                    if p[0] == 't':
                        key.append(p[1])
                    else:
                        key.append('{%d}' % len(args))
                        args.append(process(p[1], True).strip())
                count += 1
                out.append('tr("' + ''.join(key) + '"' + ''.join(', ' + a for a in args) + ')')
            else:
                s = ['"']
                for p in parts:
                    if p[0] == 't':
                        s.append(p[1])
                    elif not p[2]:
                        s.append('$' + p[1])
                    else:
                        s.append('${' + process(p[1], in_tr_arg) + '}')
                s.append('"')
                out.append(''.join(s))
            i = j
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def main():
    global count
    total = 0
    for path in sys.argv[1:]:
        count = 0
        src = open(path, encoding='utf-8').read()
        new = process(src)
        if new != src:
            if 'import com.owl.tracker.i18n.tr' not in new and '\npackage com.owl.tracker.i18n' not in new:
                lines = new.split('\n')
                # after the package line, before the first import (or right after package)
                for k, l in enumerate(lines):
                    if l.startswith('import '):
                        lines.insert(k, 'import com.owl.tracker.i18n.tr')
                        break
                else:
                    for k, l in enumerate(lines):
                        if l.startswith('package '):
                            lines.insert(k + 1, '\nimport com.owl.tracker.i18n.tr')
                            break
                new = '\n'.join(lines)
            open(path, 'w', encoding='utf-8').write(new)
        print(f'{count:5d}  {path}')
        total += count
    print(f'{total:5d}  in all')


if __name__ == '__main__':
    main()
