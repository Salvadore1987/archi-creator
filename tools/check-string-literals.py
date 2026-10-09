#!/usr/bin/env python3
"""Проверка строковых литералов в коде сервера: текст — в ресурсах, а не в строках.

Правило CLAUDE.md, раздел «Тексты и локализация»: в коде живут ключ сообщения
и аргументы, текст для человека — в ``i18n/messages*.properties`` по языку
запроса. Отказ вызывают в строковых литералах основного кода Java
(``src/main/java``, включая текстовые блоки):

  - русский текст — его место в файле локали;
  - якорь спецификации: ``FR-02``, ``NFR-05``, ``INV-MDL-006``, ``UC-MDL-001``,
    ``UI-004``, ``ADR-0017``, номер раздела ``§10``. Коды ошибок — контракт API
    и остаются, но только константами в классах ``*Codes``: в тексте сообщения
    ссылка на документ разработки уходила бы пользователю;
  - исключение, построенное из строки-литерала: ``new …Exception("…")``,
    ``ModelingException.invalid("…")``. Причина отказа — ключ сообщения
    (``Message.of(…)``), иначе её не перевести.

В файлах локали (``src/main/resources/i18n/*.properties``) отказ вызывают
якорь спецификации в тексте и апостроф: при ``always-use-message-format``
он служебный символ ``MessageFormat`` и молча съедает подстановку.

Комментарии не проверяются — у них своя проверка, ``check-comment-refs.py``.
Тесты не проверяются: русские ``@DisplayName`` и ожидаемые тексты — данные теста.
Фронтенд проверяет тот же принцип своим тестом ``checks/static-rules.test.ts``.

Исключения поштучно — в ``EXEMPT``, с причиной.

Запуск: tools/check-string-literals.py   (из любого каталога)
Код выхода 1, если найдено хотя бы одно нарушение.
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

CYRILLIC = re.compile(r"[А-Яа-яЁё]")
ANCHOR = re.compile(
    r"§\s*\d"
    r"|\b(?:FR|NFR)-\d+"
    r"|\b(?:INV|UC)-[A-Z]{2,}-\d+"
    r"|\bUI-\d+"
    r"|\bADR-\d+"
)
# Литерал в коде после разбора заменён на "" — по нему видно место, где стоял текст.
EXCEPTION_FROM_LITERAL = re.compile(
    r"new\s+[\w.]*(?:Exception|Error)\s*\(\s*\"\""
    r"|\.(?:invalid|notFound)\s*\(\s*\"\""
)

# Путь относительно корня → причина. Пусто: исключений пока нет.
EXEMPT: dict[str, str] = {}


def java_literals(text: str):
    """Строковые литералы и текстовые блоки Java с номером строки и код без них.

    Возвращает список (строка, литерал) и код, где каждый литерал заменён на ""
    — по нему ищутся конструкции вроде исключения из литерала. Комментарии
    из кода вырезаются, символьные литералы остаются как есть.
    """
    literals: list[tuple[int, str]] = []
    code: list[str] = []
    i, line, n = 0, 1, len(text)
    while i < n:
        c = text[i]
        if text.startswith('"""', i):
            end = text.find('"""', i + 3)
            end = n if end < 0 else end
            body = text[i + 3:end]
            literals.append((line, body))
            code.append('""')
            line += body.count("\n")
            i = end + 3
        elif c == '"':
            j = i + 1
            while j < n and text[j] != '"' and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            literals.append((line, text[i + 1:j]))
            code.append('""')
            i = j + 1
        elif c == "'":
            j = i + 1
            while j < n and text[j] != "'" and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            code.append(text[i:j + 1])
            i = j + 1
        elif text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            line += text[i:j].count("\n")
            code.append(" ")
            i = j
        else:
            if c == "\n":
                line += 1
            code.append(c)
            i += 1
    return literals, "".join(code)


def tracked(pattern: str) -> list[str]:
    out = subprocess.run(["git", "ls-files", "-co", "--exclude-standard", pattern],
                         cwd=ROOT, capture_output=True, text=True, check=True)
    return [p for p in out.stdout.splitlines() if (ROOT / p).is_file()]


def check_java(rel: str) -> list[str]:
    text = (ROOT / rel).read_text(encoding="utf-8")
    literals, code = java_literals(text)
    found = []
    codes_class = rel.endswith("Codes.java")
    for line, literal in literals:
        if CYRILLIC.search(literal):
            found.append(f"{rel}:{line}: русский текст в строке — ключ сообщения и текст в i18n: \"{literal.strip()[:70]}\"")
        if not codes_class and ANCHOR.search(literal):
            found.append(f"{rel}:{line}: якорь спецификации в строке — код ошибки только константой в *Codes: \"{literal.strip()[:70]}\"")
    for match in EXCEPTION_FROM_LITERAL.finditer(code):
        line = code.count("\n", 0, match.start()) + 1
        found.append(f"{rel}:{line}: исключение из строки-литерала — причина ключом: Message.of(…)")
    return found


def check_bundle(rel: str) -> list[str]:
    found = []
    for number, raw in enumerate((ROOT / rel).read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith(("#", "!")) or "=" not in raw:
            continue
        value = raw.split("=", 1)[1]
        if ANCHOR.search(value):
            found.append(f"{rel}:{number}: якорь спецификации в тексте сообщения")
        if "'" in value:
            found.append(f"{rel}:{number}: апостроф в тексте — для MessageFormat это служебный символ")
    return found


def main() -> int:
    violations: list[str] = []
    java = [p for p in tracked("*.java") if "/src/main/java/" in p]
    bundles = [p for p in tracked("*.properties") if "/src/main/resources/i18n/" in p]
    for rel in java:
        if rel not in EXEMPT:
            violations += check_java(rel)
    for rel in bundles:
        violations += check_bundle(rel)
    if violations:
        print("\n".join(violations))
        print(f"\nНарушений: {len(violations)}. Правило — CLAUDE.md, раздел «Тексты и локализация».")
        return 1
    print(f"Проверено файлов Java: {len(java)}, файлов локали: {len(bundles)}. Нарушений нет.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
