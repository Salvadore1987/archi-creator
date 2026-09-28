#!/usr/bin/env python3
"""Генерация §2 docs/archi-creator.md из spec/product/requirements.yaml.

Нормативные формулировки требований живут в спеке. Но человек, идущий
по ссылке «FR-33», должен увидеть требование, а не редирект в YAML, —
поэтому §2 держит их читаемую таблицу. Две копии одного текста расходятся
всегда, если расхождение ничем не ловится: этот скрипт превращает вторую
копию в производную.

  tools/render-requirements-index.py            перезаписать §2 из спеки
  tools/render-requirements-index.py --check    только проверить (для CI)

--check возвращает 1, если §2 разошёлся с реестром.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REQS = ROOT / "spec" / "product" / "requirements.yaml"
DOC = ROOT / "docs" / "archi-creator.md"

HEADING = "## 2. Требования"
NEXT_SECTION = "## 3. Архитектура"

OWNER_RU = {
    "modeling": "modeling",
    "interchange": "interchange",
    "advisor": "advisor",
    "ui": "ui",
    "cross-cutting": "сквозное",
}


def parse() -> tuple[list[tuple[str, list[dict]]], list[dict]]:
    """-> (функциональные по группам, нефункциональные)."""
    functional: list[tuple[str, list[dict]]] = []
    non_functional: list[dict] = []
    section = None
    group: str | None = None
    entry: dict | None = None
    field: str | None = None

    for raw in REQS.read_text(encoding="utf-8").splitlines():
        if raw.startswith("functional:"):
            section = "f"
            continue
        if raw.startswith("non_functional:"):
            section = "n"
            continue
        if section is None:
            continue

        m = re.match(r"^  # ── (.+?) ─+$", raw)
        if m:
            group = m.group(1).strip()
            continue

        m = re.match(r"^  ((?:N?FR)-\d{2}):\s*$", raw)
        if m:
            entry = {"id": m.group(1), "statement": [], "title": "", "owner": "",
                     "stages": [], "docs_ref": ""}
            field = None
            if section == "f":
                if not functional or functional[-1][0] != (group or ""):
                    functional.append(((group or ""), []))
                functional[-1][1].append(entry)
            else:
                non_functional.append(entry)
            continue

        if entry is None:
            continue

        m = re.match(r"^    (\w+):\s*(.*)$", raw)
        if m:
            key, value = m.group(1), m.group(2).strip()
            field = key if value == "|" else None
            if key == "title":
                entry["title"] = value
            elif key == "owner":
                entry["owner"] = value
            elif key == "stages":
                entry["stages"] = re.findall(r'"([^"]+)"', value)
            elif key == "docs_ref":
                entry["docs_ref"] = value
            continue

        if field == "statement" and raw.startswith("      "):
            entry["statement"].append(raw.strip())

    return functional, non_functional


def cell(entry: dict) -> str:
    text = " ".join(entry["statement"]).replace("|", "\\|")
    return re.sub(r"\s+", " ", text).strip()


def details(entry: dict) -> str:
    ref = entry["docs_ref"]
    if not ref:
        return "—"
    path, _, anchor = ref.partition("#")
    name = Path(path).name
    section = anchor.split("-")[0] if anchor else ""
    num = ".".join(section) if section.isdigit() and len(section) > 1 else section
    label = f"§{num}" if num else name
    return f"[{label}]({name}#{anchor})" if anchor else f"[{name}]({name})"


def render() -> str:
    functional, non_functional = parse()
    out: list[str] = [HEADING, ""]
    out += [
        "**Нормативные формулировки — в "
        "[`spec/product/requirements.yaml`](../spec/product/requirements.yaml).** "
        "Раздел ниже порождается из реестра скриптом "
        "`tools/render-requirements-index.py` и правится не здесь, а в спеке: "
        "расхождение ловится тем же скриптом с флагом `--check`.",
        "",
        "Столбец «Владелец» — bounded context или слой, отвечающий за требование "
        "(`ui` — слой интерфейса, `сквозное` — применяется во всех). "
        "«Этапы» — по [§12](#12-этапы-работ). «Подробности» ведут в раздел "
        "с обоснованием.",
        "",
        "### 2.1 Функциональные требования",
        "",
    ]
    for group, entries in functional:
        if group:
            out += [f"#### {group}", ""]
        out += ["| ID | Требование | Владелец | Этапы | Подробности |",
                "|----|-----------|----------|-------|-------------|"]
        for e in entries:
            out.append(
                f"| {e['id']} | {cell(e)} | {OWNER_RU.get(e['owner'], e['owner'])} "
                f"| {', '.join(e['stages'])} | {details(e)} |")
        out.append("")

    out += ["### 2.2 Нефункциональные требования", "",
            "| ID | Требование | Владелец | Этапы | Подробности |",
            "|----|-----------|----------|-------|-------------|"]
    for e in non_functional:
        out.append(
            f"| {e['id']} | {cell(e)} | {OWNER_RU.get(e['owner'], e['owner'])} "
            f"| {', '.join(e['stages'])} | {details(e)} |")
    out += ["", "---", ""]
    return "\n".join(out)


def main() -> int:
    doc = DOC.read_text(encoding="utf-8")
    start = doc.index(HEADING)
    end = doc.index(NEXT_SECTION)
    generated = render()
    updated = doc[:start] + generated + doc[end:]

    if "--check" in sys.argv:
        if updated == doc:
            print("§2 совпадает с реестром требований.")
            return 0
        print("§2 разошёлся с spec/product/requirements.yaml — "
              "перегенерируйте: tools/render-requirements-index.py")
        return 1

    if updated == doc:
        print("§2 уже актуален.")
        return 0
    DOC.write_text(updated, encoding="utf-8")
    functional, non_functional = parse()
    total = sum(len(e) for _, e in functional) + len(non_functional)
    print(f"§2 перегенерирован: {total} требований "
          f"({sum(len(e) for _, e in functional)} FR + {len(non_functional)} NFR)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
