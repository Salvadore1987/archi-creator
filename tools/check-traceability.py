#!/usr/bin/env python3
"""Проверка трассируемости спецификации.

Спека — источник правды только пока она связна. Проверяется пять вещей:

  1. Объявление. Каждый якорь (INV-*, UC-*, UI-*, ADR-*) объявлен ровно один раз;
     дублей нет.
  2. Разрешимость. Каждый якорь, упомянутый где-либо в spec/, объявлен.
     Ссылка на INV-MDL-042, которого нет, — обрыв.
  3. Покрытие требований. Каждое FR/NFR из product/requirements.yaml имеет хотя бы
     один якорь: требование без якоря ничем не проверяемо.
  4. Обратная связь. Каждый INV, UC и UI привязан хотя бы к одному требованию:
     якорь без требования описывает то, чего никто не просил.
  5. Сиротские инварианты. Каждый INV указан хотя бы в одном use case'е.

Связи берутся ТОЛЬКО из объявленных мест, не из соседства в тексте:

  requirements.yaml   FR-xx.anchors      -> требование ↔ якорь
  usecases/*.md       frontmatter        -> UC ↔ требования, UC ↔ инварианты
  invariants.md       блок ## INV-...    -> INV ↔ требования (строка «Требование:»)
  ui/rules.md         блок ## UI-...     -> UI  ↔ требования

Известные пробелы объявляются полем note у требования: они попадают в отчёт
как объявленные, а не как нарушения. Инварианты без use case'а перечисляются
отдельным списком: до своего этапа это нормально, и список показывает, сколько
работы осталось.

Запуск: tools/check-traceability.py   (из корня репозитория)
Код выхода 1, если найдено нарушение.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "spec"

ANCHOR = re.compile(r"\b(INV-[A-Z]{3}-\d{3}|UC-[A-Z]{3}-\d{3}|UI-\d{3}|ADR-\d{4})\b")
REQ = re.compile(r"\b((?:N?FR)-\d{2})\b")

DECLARED_IN = {
    "INV": ("domain/*/invariants.md", re.compile(r"^##\s+(INV-[A-Z]{3}-\d{3})\b", re.M)),
    "UC": ("application/*/usecases/*.md", re.compile(r"^use_case:\s*(UC-[A-Z]{3}-\d{3})\s*$", re.M)),
    "UI": ("ui/rules.md", re.compile(r"^##\s+(UI-\d{3})\b", re.M)),
    "ADR": ("adr/decisions.yaml", re.compile(r"^\s*-\s+id:\s*(ADR-\d{4})\s*$", re.M)),
}


def spec_files() -> list[Path]:
    return [p for p in sorted(SPEC.rglob("*"))
            if p.is_file() and p.suffix in {".md", ".yaml", ".yml"}]


def collect_declarations() -> tuple[dict[str, Path], list[str]]:
    declared: dict[str, Path] = {}
    problems: list[str] = []
    for pattern, regex in DECLARED_IN.values():
        for path in sorted(SPEC.glob(pattern)):
            for code in regex.findall(path.read_text(encoding="utf-8")):
                if code in declared:
                    problems.append(
                        f"{code}: объявлен дважды — "
                        f"{declared[code].relative_to(ROOT)} и {path.relative_to(ROOT)}")
                else:
                    declared[code] = path
    return declared, problems


def parse_requirements() -> dict[str, dict]:
    text = (SPEC / "product" / "requirements.yaml").read_text(encoding="utf-8")
    reqs: dict[str, dict] = {}
    current: str | None = None
    for line in text.splitlines():
        m = re.match(r"^  ((?:N?FR)-\d{2}):\s*$", line)
        if m:
            current = m.group(1)
            reqs[current] = {"anchors": [], "note": False}
            continue
        if current is None:
            continue
        m = re.match(r"^    anchors:\s*\[(.*)\]\s*$", line)
        if m:
            reqs[current]["anchors"] = ANCHOR.findall(m.group(1))
        elif re.match(r"^    note:", line):
            reqs[current]["note"] = True
    return reqs


def parse_usecases() -> dict[str, dict]:
    """UC -> объявленные им требования и инварианты (из frontmatter)."""
    out: dict[str, dict] = {}
    for path in sorted(SPEC.glob("application/*/usecases/*.md")):
        text = path.read_text(encoding="utf-8")
        head = text.split("---", 2)[1] if text.startswith("---") else ""
        # frontmatter-список может быть перенесён на несколько строк — склеиваем
        head = re.sub(r"\n[ \t]+", " ", head)
        m = re.search(r"^use_case:\s*(UC-[A-Z]{3}-\d{3})", head, re.M)
        if not m:
            continue
        out[m.group(1)] = {
            "path": path,
            "requirements": REQ.findall(
                (re.search(r"^requirements:\s*\[(.*)\]", head, re.M) or _Empty()).group(1)),
            "invariants": ANCHOR.findall(
                (re.search(r"^invariants:\s*\[(.*)\]", head, re.M) or _Empty()).group(1)),
        }
    return out


class _Empty:
    def group(self, _n):
        return ""


def parse_blocks(path: Path, heading: re.Pattern) -> dict[str, str]:
    """Якорь -> текст его раздела, от заголовка до следующего заголовка того же уровня."""
    text = path.read_text(encoding="utf-8")
    blocks: dict[str, str] = {}
    matches = list(heading.finditer(text))
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        blocks[m.group(1)] = text[m.start():end]
    return blocks


def main() -> int:
    declared, problems = collect_declarations()
    reqs = parse_requirements()
    usecases = parse_usecases()

    # 2. Все упомянутые якоря объявлены.
    for path in spec_files():
        rel = path.relative_to(ROOT)
        for code in sorted(set(ANCHOR.findall(path.read_text(encoding="utf-8")))):
            if code not in declared:
                problems.append(f"{rel}: ссылка на необъявленный якорь {code}")

    # Связь якорь -> требования, из объявленных мест.
    req_of: dict[str, set[str]] = {code: set() for code in declared}

    for rid, data in reqs.items():
        for code in data["anchors"]:
            if code in req_of:
                req_of[code].add(rid)

    for uc, data in usecases.items():
        req_of.setdefault(uc, set()).update(data["requirements"])

    for path in sorted(SPEC.glob("domain/*/invariants.md")):
        for code, block in parse_blocks(
                path, re.compile(r"^##\s+(INV-[A-Z]{3}-\d{3})\b", re.M)).items():
            line = re.search(r"^\*\*Требование:\*\*(.*)$", block, re.M)
            if line and code in req_of:
                req_of[code].update(REQ.findall(line.group(1)))

    for code, block in parse_blocks(
            SPEC / "ui" / "rules.md", re.compile(r"^##\s+(UI-\d{3})\b", re.M)).items():
        line = re.search(r"^\*\*Требование:\*\*(.*)$", block, re.M)
        if line and code in req_of:
            req_of[code].update(REQ.findall(line.group(1)))

    # 3. Покрытие требований.
    covered: set[str] = set()
    for code, rqs in req_of.items():
        covered |= rqs
    uncovered_declared = []
    for rid, data in sorted(reqs.items()):
        if rid in covered:
            continue
        if data["note"]:
            uncovered_declared.append(rid)
        else:
            problems.append(f"{rid}: ни одного якоря и нет поля note с объяснением")

    # 4. Обратная связь: якорь без требования.
    for code in sorted(req_of):
        if code.startswith("ADR-"):
            continue  # решение может не иметь требования (ADR-0001)
        if not req_of[code]:
            problems.append(f"{code}: не привязан ни к одному требованию "
                            f"({declared[code].relative_to(ROOT)})")

    # 5. Сиротские инварианты: только из frontmatter use case'ов.
    inv_in_uc: set[str] = set()
    for data in usecases.values():
        inv_in_uc |= {c for c in data["invariants"] if c.startswith("INV-")}
    orphan_inv = sorted(c for c in declared if c.startswith("INV-") and c not in inv_in_uc)

    # Требование, упомянутое в спеке, но отсутствующее в реестре.
    for path in spec_files():
        rel = path.relative_to(ROOT)
        for rid in sorted(set(REQ.findall(path.read_text(encoding="utf-8")))):
            if rid not in reqs:
                problems.append(f"{rel}: требование {rid} не объявлено в реестре")

    # ── отчёт ──────────────────────────────────────────────────────
    kinds: dict[str, list[str]] = {}
    for code in declared:
        kinds.setdefault(code.split("-")[0], []).append(code)
    print("Якорей объявлено: " + ", ".join(f"{k} — {len(v)}" for k, v in sorted(kinds.items())))
    print(f"Требований в реестре: {len(reqs)}, покрыто якорями: {len(covered & set(reqs))}")

    if uncovered_declared:
        print("\nБез якорей, объявлено полем note: " + ", ".join(uncovered_declared))
    if orphan_inv:
        print(f"\nИнвариантов без use case'а: {len(orphan_inv)} "
              f"(нормально до своего этапа)")
        for code in orphan_inv:
            print(f"  {code} — {declared[code].relative_to(ROOT)}")

    if problems:
        print(f"\nНарушений: {len(problems)}\n")
        for p in problems:
            print(f"  {p}")
        return 1
    print("\nНарушений трассируемости нет.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
