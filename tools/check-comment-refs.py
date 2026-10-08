#!/usr/bin/env python3
"""Проверка комментариев в коде: нет ссылок на спецификацию.

Правило CLAUDE.md, раздел «Комментарии в коде»: комментарий описывает
поведение кода, а не отсылает к документу. Отказ вызывают в комментариях:

  - номер раздела: ``§10.3``;
  - якорь: ``FR-02``, ``NFR-05``, ``INV-IXC-005``, ``UC-MDL-001``, ``UI-004``,
    ``ADR-0017``;
  - путь в каталог спецификации или обоснований: ``spec/…``, ``docs/…``.

Проверяются только комментарии — строки, аннотации и константы нет:
коды ошибок (``"INV-MDL-006"``) и ``@DisplayName`` тестов — данные.

Что считается комментарием, зависит от вида файла:

  - Java, TypeScript, JavaScript: ``//`` и ``/* */``; строки, символы
    и текстовые блоки ``\"\"\"`` пропускаются;
  - CSS: ``/* */``;
  - SQL: ``--`` и ``/* */``;
  - XML и HTML: ``<!-- -->``, в ``pom.xml`` ещё и ``<description>``;
  - YAML, properties, Dockerfile, ignore-файлы, CSV, скрипты с ``#!``:
    ``#`` в начале строки или после пробела, вне кавычек.

Не проверяются ``spec/``, ``docs/`` и ``tools/`` — они говорят о спеке
по своей природе, — markdown, фикстуры ``.archimate``, сгенерированная
обёртка Maven и применённые миграции Flyway (``FROZEN``): Flyway считает
контрольную сумму с комментариями, и правка их роняет ``validate``.

Исключения поштучно — в ``EXEMPT``, с причиной. Исключение уместно, когда
упоминание описывает поведение: код читает этот файл или это буквальное
значение, а не ссылка на обоснование.

Запуск: tools/check-comment-refs.py   (из любого каталога)
Код выхода 1, если найдена хотя бы одна ссылка.
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

REF = re.compile(
    r"§\s*\d"
    r"|\b(?:FR|NFR)-\d+"
    r"|\b(?:INV|UC)-[A-Z]{2,}-\d+"
    r"|\bUI-\d+"
    r"|\bADR-\d+"
    r"|(?<![\w./-])(?:spec|docs)/"
)

SKIP_PREFIXES = ("spec/", "docs/", "tools/", ".idea/", ".mvn/")
SKIP_NAMES = {"mvnw", "mvnw.cmd", "package-lock.json"}
SKIP_SUFFIXES = {".md", ".archimate", ".json", ".png", ".svg", ".ico"}

# Применённые миграции: комментарий входит в контрольную сумму Flyway.
FROZEN = {
    "archi-bootstrap/src/main/resources/db/migration/V1__baseline.sql",
    "archi-bootstrap/src/main/resources/db/migration/V2__model.sql",
}

# (путь, фрагмент строки комментария) -> почему это не ссылка на спеку.
EXEMPT = {
    ("archi-bootstrap/src/test/java/uz/salvadore/hamkorbank/archi/bootstrap/roundtrip/"
     "RoundTripGoldenFileTest.java", "Эталон читается из docs/"):
        "тест действительно читает эталон из docs/",
    ("archi-bootstrap/src/main/java/uz/salvadore/hamkorbank/archi/bootstrap/wiring/"
     "MicrometerUseCaseMetrics.java", "{@code INV-MDL-006}"):
        "буквальное значение метки error_code, по которому фильтрует алерт",
}

C_LIKE = {".java", ".ts", ".tsx", ".js", ".mjs", ".cjs"}
HASH = {".yaml", ".yml", ".properties", ".csv", ".sh", ".example",
        ".gitignore", ".dockerignore"}


def c_like_comments(text: str, line_comments: bool = True):
    """Комментарии ``//`` и ``/* */`` как пары (номер строки, текст)."""
    i, n, line = 0, len(text), 1
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
        elif text.startswith('"""', i):                 # текстовый блок Java
            end = text.find('"""', i + 3)
            end = n if end < 0 else end + 3
            line += text.count("\n", i, end)
            i = end
        elif c in "\"'`":
            j = i + 1
            while j < n and text[j] != c:
                if text[j] == "\\":
                    j += 1
                elif text[j] == "\n" and c != "`":       # незакрытая строка
                    break
                j += 1
            line += text.count("\n", i, j)
            i = j + 1
        elif line_comments and text.startswith("//", i):
            end = text.find("\n", i)
            end = n if end < 0 else end
            yield line, text[i:end]
            i = end
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            end = n if end < 0 else end + 2
            for k, part in enumerate(text[i:end].split("\n")):
                yield line + k, part
            line += text.count("\n", i, end)
            i = end
        else:
            i += 1


def sql_comments(text: str):
    for no, raw in enumerate(text.splitlines(), 1):
        pos = raw.find("--")
        if pos >= 0:
            yield no, raw[pos:]
    for no, part in c_like_comments(text, line_comments=False):
        yield no, part


def block_comments(text: str, opening: str, closing: str):
    for m in re.finditer(re.escape(opening) + r".*?" + re.escape(closing),
                         text, re.DOTALL):
        first = text.count("\n", 0, m.start()) + 1
        for k, part in enumerate(m.group(0).split("\n")):
            yield first + k, part


def hash_comments(text: str):
    for no, raw in enumerate(text.splitlines(), 1):
        quote = None
        for i, c in enumerate(raw):
            if quote:
                if c == quote:
                    quote = None
            elif c in "\"'":
                quote = c
            elif c == "#" and (i == 0 or raw[i - 1].isspace()):
                yield no, raw[i:]
                break


def comments_of(rel: str, text: str):
    path = Path(rel)
    suffix = path.suffix if path.suffix else path.name
    if suffix in C_LIKE:
        return c_like_comments(text)
    if suffix == ".css":
        return c_like_comments(text, line_comments=False)
    if suffix == ".sql":
        return sql_comments(text)
    if suffix in {".xml", ".html"}:
        found = list(block_comments(text, "<!--", "-->"))
        if path.name == "pom.xml":
            found += block_comments(text, "<description>", "</description>")
        return found
    if suffix in HASH or path.name == "Dockerfile" or text.startswith("#!"):
        return hash_comments(text)
    return ()


def files_to_scan():
    out = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout.splitlines()
    for rel in sorted(set(out)):
        path = ROOT / rel
        if (rel.startswith(SKIP_PREFIXES) or rel in FROZEN
                or path.name in SKIP_NAMES or path.suffix in SKIP_SUFFIXES
                or "/fixtures/" in rel or not path.is_file()):
            continue
        yield rel, path


def main() -> int:
    problems, scanned = [], 0
    for rel, path in files_to_scan():
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        scanned += 1
        seen = set()
        for no, comment in comments_of(rel, text):
            m = REF.search(comment)
            if not m or (rel, no) in seen:
                continue
            if any(rel == p and frag in comment for p, frag in EXEMPT):
                continue
            seen.add((rel, no))
            problems.append(f"{rel}:{no}: {m.group(0)!r} — {comment.strip()}")

    print(f"Проверено файлов: {scanned}")
    if problems:
        print(f"Ссылок на спецификацию в комментариях: {len(problems)}\n")
        for p in problems:
            print(f"  {p}")
        print("\nПерескажите суть правила словами — CLAUDE.md, «Комментарии в коде».")
        return 1
    print("Ссылок на спецификацию в комментариях нет.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
