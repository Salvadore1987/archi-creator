#!/usr/bin/env python3
"""Проверка ссылок между файлами спецификации.

Два прохода:
  1. Markdown-ссылки вида [текст](путь#якорь) — существует ли файл и есть ли
     в нём такой заголовок. Код и вставки в обратных кавычках не считаются:
     путь внутри `...` — цитата формы ссылки, а не ссылка.
  2. Пути в прозе и в комментариях YAML (`docs/backend.md`, `spec/domain/...`)
     — существует ли файл. Спека ссылается так из YAML, где markdown-ссылок нет.
     Каталог docs/plans/ из этого прохода исключён: планы по своей природе
     говорят о путях, которых уже или ещё нет.

Запуск: tools/check-links.py   (из корня репозитория)
Код выхода 1, если найдена хотя бы одна битая ссылка.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCAN_SUFFIXES = {".md", ".yaml", ".yml"}
SKIP_DIRS = {".git", ".idea", "target", "node_modules", ".mvn"}

MD_LINK = re.compile(r"\]\(([^)\s]+)\)")
BARE_PATH = re.compile(
    r"(?<![\w/#-])((?:\.\./)*(?:docs|spec|tools)/[A-Za-z0-9_./-]*"
    r"\.(?:md|yaml|yml|json|archimate|html|xml|js|css))"
)
HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*#*$")
FENCED = re.compile(r"^(```|~~~).*?^\1", re.MULTILINE | re.DOTALL)
INLINE_CODE = re.compile(r"`[^`\n]*`")
NO_BARE_CHECK = ("docs/plans",)


def slug(text: str) -> str:
    """Якорь в стиле GitHub: строчные, пунктуация выброшена, пробелы в дефисы."""
    text = re.sub(r"`|\*|_{1,2}|~~", "", text)
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)  # ссылка -> её текст
    text = text.strip().lower()
    text = re.sub(r"[^\w\s-]", "", text, flags=re.UNICODE)
    return re.sub(r"\s", "-", text)


def anchors_of(path: Path) -> set[str]:
    out = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        m = HEADING.match(line)
        if m:
            out.add(slug(m.group(2)))
    return out


def files_to_scan():
    for p in sorted(ROOT.rglob("*")):
        if p.is_file() and p.suffix in SCAN_SUFFIXES:
            if not any(part in SKIP_DIRS for part in p.relative_to(ROOT).parts):
                yield p


def resolve(src: Path, target: str) -> Path | None:
    """Путь относительно файла либо относительно корня — годится любой."""
    for base in (src.parent, ROOT):
        candidate = (base / target).resolve()
        if candidate.exists():
            return candidate
    return None


def main() -> int:
    anchor_cache: dict[Path, set[str]] = {}
    problems: list[str] = []
    checked = 0

    for src in files_to_scan():
        rel = src.relative_to(ROOT)
        text = src.read_text(encoding="utf-8")

        prose = INLINE_CODE.sub("", FENCED.sub("", text))
        targets = [(m.group(1), True) for m in MD_LINK.finditer(prose)]
        if not str(rel).startswith(NO_BARE_CHECK):
            targets += [(m.group(1), False) for m in BARE_PATH.finditer(text)]

        for target, is_link in targets:
            if target.startswith(("http://", "https://", "mailto:")):
                continue
            path_part, _, anchor = target.partition("#")
            checked += 1

            if not path_part:                      # ссылка внутри файла
                dest = src
            else:
                dest = resolve(src, path_part)
                if dest is None:
                    kind = "ссылка" if is_link else "путь"
                    problems.append(f"{rel}: битый {kind} -> {target}")
                    continue

            if anchor and dest.suffix == ".md":
                if dest not in anchor_cache:
                    anchor_cache[dest] = anchors_of(dest)
                if anchor not in anchor_cache[dest]:
                    problems.append(
                        f"{rel}: нет якоря #{anchor} "
                        f"в {dest.relative_to(ROOT)}"
                    )

    print(f"Проверено ссылок: {checked}")
    if problems:
        print(f"Битых: {len(problems)}\n")
        for p in problems:
            print(f"  {p}")
        return 1
    print("Битых ссылок нет.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
