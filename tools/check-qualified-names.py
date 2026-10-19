#!/usr/bin/env python3
"""Проверка кода Java: тип называется простым именем через импорт, а не полным.

Полное имя (``java.util.ArrayList``, ``uz.salvadore…metamodel.Layer``) посреди кода
читается хуже и прячет зависимость файла от чужого пакета — её не видно в импортах.
Оно оправдано, только когда простое имя в файле уже занято другим типом: мост между
контекстами, где встречаются два ``WorkspaceId``. Отказ вызывает полное имя типа
в коде, если простое имя ничем не занято:

  - не импортировано другим типом;
  - не объявлено в самом файле;
  - не встречается в файле полным именем из другого пакета;
  - не встречается в файле без квалификации — тогда оно уже значит тип своего пакета
    или вложенный тип реализуемого интерфейса, и полное имя их различает.

Проверяется основной код и тесты (``src/**/*.java``). Строки, комментарии, ``import``
и ``package`` не проверяются.

Запуск: tools/check-qualified-names.py   (из любого каталога)
Код выхода 1, если найдено хотя бы одно нарушение.
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

QUALIFIED = re.compile(r"(?<![\w.])((?:[a-z][a-z0-9_]*\.)+)([A-Z]\w*)")
IMPORT = re.compile(r"^import\s+(?:static\s+)?([\w.]+)\s*;", re.M)
DECLARED = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Z]\w*)")
# Корни пакетов, которые считаются полным именем типа; иначе a.b.C — это обращение к полю.
ROOTS = ("java.", "javax.", "jakarta.", "org.", "com.", "uz.", "tools.", "net.", "io.")


def code_only(text: str) -> str:
    """Код без комментариев, строк и символьных литералов; переводы строк сохраняются."""
    out, i, n = [], 0, len(text)
    while i < n:
        if text.startswith('"""', i):
            end = text.find('"""', i + 3)
            end = n if end < 0 else end + 3
            out.append("\n" * text.count("\n", i, end))
            i = end
        elif text[i] in "\"'":
            quote, j = text[i], i + 1
            while j < n and text[j] != quote and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            out.append('""')
            i = j + 1
        elif text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("\n" * text.count("\n", i, j))
            i = j
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def violations(rel: str) -> list[str]:
    text = (ROOT / rel).read_text(encoding="utf-8")
    imported: dict[str, str] = {}
    for full in IMPORT.findall(text):
        imported.setdefault(full.rsplit(".", 1)[-1], full)
    code = code_only(text)
    body = "\n".join("" if re.match(r"\s*(import|package)\s", line) else line for line in code.split("\n"))
    declared = set(DECLARED.findall(body))
    uses = [(m.group(1) + m.group(2), m.group(2), body.count("\n", 0, m.start()) + 1)
            for m in QUALIFIED.finditer(body) if m.group(1).startswith(ROOTS)]
    bare = QUALIFIED.sub("", body)
    packages_of: dict[str, set[str]] = {}
    for full, simple, _ in uses:
        packages_of.setdefault(simple, set()).add(full)
    found = []
    for full, simple, line in uses:
        imported_same = imported.get(simple) == full
        clash = (simple in imported and not imported_same) or simple in declared \
            or len(packages_of[simple]) > 1 \
            or (not imported_same and re.search(r"(?<![\w.])" + simple + r"\b", bare) is not None)
        if not clash:
            found.append(f"{rel}:{line}: полное имя {full} — импорт и простое имя {simple}")
    return found


def main() -> int:
    out = subprocess.run(["git", "ls-files", "-co", "--exclude-standard", "*.java"],
                         cwd=ROOT, capture_output=True, text=True, check=True)
    files = [p for p in out.stdout.splitlines() if "/src/" in p and (ROOT / p).is_file()]
    problems = [v for rel in files for v in violations(rel)]
    if problems:
        print("\n".join(problems))
        print(f"\nНарушений: {len(problems)}. Тип называется простым именем через импорт.")
        return 1
    print(f"Проверено файлов Java: {len(files)}. Полных имён без причины нет.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
