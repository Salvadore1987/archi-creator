#!/usr/bin/env python3
"""Проверка сообщения коммита: conventional commits и связь со спекой.

Формат: ``тип(область): описание``, описание — по-русски, со строчной буквы,
без точки на конце. Заголовок не длиннее 72 символов, тело отделено пустой
строкой, строки тела не длиннее 100 символов.

Этот файл — **единственный список** допустимых типов и областей: CLAUDE.md
ссылается сюда, а не повторяет перечень. Добавление области — правка здесь.

Что отказывает (код выхода 1):

  - заголовок не разбирается как ``тип(область): описание``;
  - неизвестный тип или неизвестная область;
  - заголовок длиннее 72 символов;
  - описание с заглавной буквы или с точкой на конце;
  - вторая строка непустая (тело не отделено от заголовка);
  - строка тела длиннее 100 символов.

Что только предупреждает (код выхода 0):

  - у типа ``feat`` или ``spec`` в сообщении нет ссылки на требование или
    якорь (``FR-02``, ``INV-IXC-005``, ``UC-MDL-001``, ``UI-012``, ``ADR-0001``).
    Изменение по существу обязано быть трассируемым, но у правки инструментов
    или каркаса якоря может не быть — поэтому предупреждение, а не отказ;
  - помечено ``!`` (breaking change), но тела с объяснением нет.

Сообщения, сформированные git'ом (``Merge …``, ``Revert "…"``, ``fixup!``,
``squash!``), пропускаются: их формат задаёт не автор.

Запуск:

  tools/check-commit-message.py .git/COMMIT_EDITMSG   # хук commit-msg
  tools/check-commit-message.py                       # сообщение HEAD
  tools/check-commit-message.py --range origin/main..HEAD   # гейт в CI

Правило действует с коммита, которым введено: история до него ему не
подчиняется, поэтому в CI проверяется диапазон новых коммитов, а не вся ветка.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# ── Допустимые типы ────────────────────────────────────────────────
TYPES = {
    "feat": "новая функциональность",
    "fix": "исправление дефекта",
    "spec": "правка нормативной спецификации spec/",
    "docs": "документация: docs/, README.md, CLAUDE.md",
    "test": "тесты без изменения поведения кода",
    "refactor": "правка кода без изменения поведения",
    "perf": "производительность",
    "build": "сборка, зависимости, Maven, Docker",
    "ci": "конвейер и гейты",
    "chore": "обслуживание, не попадающее в остальные типы",
    "revert": "откат коммита",
}

# ── Допустимые области ─────────────────────────────────────────────
# Bounded context, слой реализации или часть репозитория. Область
# необязательна: у правки, задевающей всё, её нет.
SCOPES = {
    # bounded contexts и слой интерфейса
    "modeling", "interchange", "advisor", "ui",
    # части реализации
    "metamodel", "codec", "rest", "persistence", "db", "export", "oef",
    "git", "ai", "security", "frontend", "bootstrap",
    # репозиторий и обвязка
    "spec", "docs", "plans", "tools", "pom", "docker", "ci",
}

HEADER_MAX = 72
BODY_MAX = 100

HEADER = re.compile(
    r"^(?P<type>[a-z]+)(?:\((?P<scope>[a-z0-9-]+)\))?(?P<bang>!)?: (?P<subject>.+)$"
)
GENERATED = re.compile(r'^(Merge |Revert "|fixup! |squash! )')
ANCHOR = re.compile(
    r"\b((?:N?FR)-\d{2}|INV-[A-Z]{3}-\d{3}|UC-[A-Z]{3}-\d{3}|UI-\d{3}|ADR-\d{4})\b"
)
SCISSORS = re.compile(r"^#\s*-+\s*>8\s*-+")
ANCHOR_EXPECTED = {"feat", "spec"}


def clean(raw: str) -> list[str]:
    """Убрать комментарии, отрезанный git'ом диф и хвостовые пустые строки."""
    lines: list[str] = []
    for line in raw.splitlines():
        if SCISSORS.match(line):
            break
        if line.startswith("#"):
            continue
        lines.append(line.rstrip())
    while lines and not lines[-1]:
        lines.pop()
    while lines and not lines[0]:
        lines.pop(0)
    return lines


def check(raw: str) -> tuple[list[str], list[str]]:
    """Вернуть (отказы, предупреждения) для одного сообщения."""
    problems: list[str] = []
    warnings: list[str] = []
    lines = clean(raw)

    if not lines:
        return ["сообщение пустое"], []

    header = lines[0]
    if GENERATED.match(header):
        return [], []

    m = HEADER.match(header)
    if not m:
        problems.append(
            "заголовок не в формате «тип(область): описание» — получено: " + repr(header)
        )
        return problems, warnings

    ctype = m.group("type")
    scope = m.group("scope")
    subject = m.group("subject")

    if ctype not in TYPES:
        problems.append(
            f"неизвестный тип «{ctype}»; допустимы: " + ", ".join(sorted(TYPES))
        )
    if scope is not None and scope not in SCOPES:
        problems.append(
            f"неизвестная область «{scope}»; допустимы: " + ", ".join(sorted(SCOPES))
            + " (новая область добавляется в tools/check-commit-message.py)"
        )
    if len(header) > HEADER_MAX:
        problems.append(f"заголовок {len(header)} символов, предел {HEADER_MAX}")
    if subject.endswith("."):
        problems.append("описание заканчивается точкой")
    if subject[:1].isupper():
        problems.append("описание начинается с заглавной буквы")
    if len(subject) < 5:
        problems.append("описание короче пяти символов: оно ничего не сообщает")

    if len(lines) > 1 and lines[1]:
        problems.append("тело не отделено от заголовка пустой строкой")

    for i, line in enumerate(lines[1:], start=2):
        # Длинные ссылки и идентификаторы переносу не подлежат.
        if len(line) > BODY_MAX and " " in line.strip():
            problems.append(f"строка {i}: {len(line)} символов, предел {BODY_MAX}")

    body = "\n".join(lines[1:])
    if ctype in ANCHOR_EXPECTED and not ANCHOR.search(raw):
        warnings.append(
            f"тип «{ctype}» без ссылки на требование или якорь "
            "(FR-xx, INV-*, UC-*, UI-*, ADR-*): изменение по существу "
            "обязано быть трассируемым"
        )
    if m.group("bang") and not body.strip():
        warnings.append("помечено «!», но тела с объяснением слома нет")

    return problems, warnings


class BadRange(Exception):
    """Диапазон не разбирается git'ом."""


def messages_in_range(rng: str) -> list[tuple[str, str]]:
    """Сообщения коммитов диапазона, без merge-коммитов."""
    log = subprocess.run(
        ["git", "log", "--no-merges", "--format=%H", rng],
        cwd=ROOT, capture_output=True, text=True,
    )
    if log.returncode != 0:
        err = log.stderr.strip().splitlines()
        raise BadRange(err[0] if err else rng)
    shas = log.stdout.split()
    out = []
    for sha in shas:
        body = subprocess.run(
            ["git", "log", "-1", "--format=%B", sha],
            cwd=ROOT, capture_output=True, text=True, check=True,
        ).stdout
        out.append((sha[:8], body))
    return out


def main() -> int:
    args = sys.argv[1:]
    if args and args[0] == "--range":
        if len(args) < 2:
            print("нужен диапазон: --range origin/main..HEAD")
            return 2
        try:
            subjects = messages_in_range(args[1])
        except BadRange as e:
            print(f"git не разобрал диапазон «{args[1]}»: {e}")
            return 2
        if not subjects:
            print(f"В диапазоне {args[1]} коммитов нет.")
            return 0
    elif args:
        path = Path(args[0])
        if not path.is_file():
            print(f"нет файла {path}")
            return 2
        subjects = [(path.name, path.read_text(encoding="utf-8"))]
    else:
        subjects = messages_in_range("HEAD~1..HEAD") or [
            ("HEAD", subprocess.run(
                ["git", "log", "-1", "--format=%B"],
                cwd=ROOT, capture_output=True, text=True, check=True,
            ).stdout)
        ]

    failed = 0
    for label, raw in subjects:
        problems, warnings = check(raw)
        head = clean(raw)[0] if clean(raw) else ""
        for w in warnings:
            print(f"Предупреждение [{label}] {head}\n  {w}")
        if problems:
            failed += 1
            print(f"\nСообщение не по правилу [{label}]: {head}")
            for p in problems:
                print(f"  {p}")

    if failed:
        print(
            f"\nНе прошло сообщений: {failed}. Формат:\n"
            "  тип(область): описание со строчной буквы без точки\n\n"
            "  <пустая строка>\n"
            "  Тело: зачем, а не что. Ссылка на требование или якорь.\n\n"
            "Типы: " + ", ".join(sorted(TYPES)) + "\n"
            "Правило и примеры — CLAUDE.md, раздел «Коммиты»."
        )
        return 1

    print(f"Проверено сообщений: {len(subjects)}. Нарушений нет.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
