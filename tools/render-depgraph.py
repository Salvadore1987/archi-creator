#!/usr/bin/env python3
"""Граф зависимостей проекта: PlantUML из Maven, картинка из PlantUML.

Два графа, потому что одним не обойтись:

  reactor.png        — цель ``aggregate``: все модули реактора и их
                       зависимости на одной картинке, версии на узлах;
  archi-bootstrap.png — цель ``graph`` на модуле, куда сходится всё:
                       то же плюс дубли и конфликты версий.

Разделение не прихоть. Флаги ``showDuplicates`` и ``showConflicts``
понимает только цель ``graph``; у ``aggregate`` таких параметров нет
вовсе — агрегированный граф склеивает деревья разных модулей, и «дубль»
в нём означал бы разное для разных пар. Поэтому картинки две: одна
отвечает «как устроен реактор», вторая — «нет ли расхождения версий».

Рендерит PlantUML: локальный ``plantuml``, если он в PATH, иначе образ
``plantuml/plantuml`` в Docker. Без обоих скрипт отказывает, а не молча
оставляет вчерашнюю картинку.

Запуск:

  tools/render-depgraph.py          # оба графа: .puml и .png
  tools/render-depgraph.py --puml   # только .puml, без рендера

Правило запуска — CLAUDE.md, «Граф зависимостей»: по закрытии каждого
этапа, вместе с обновлением README.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT_DIR = ROOT / "docs" / "dependencies"

PLUGIN = "com.github.ferstl:depgraph-maven-plugin"

# Модуль, в который сходятся все двенадцать: его дерево и есть дерево
# приложения. Меняется вместе с ADR-0001, а не сам по себе.
AGGREGATING_MODULE = "archi-bootstrap"

# PlantUML по умолчанию обрезает холст на 4096 px и делает это молча:
# картинка выходит правдоподобной и неполной. Граф в ~160 узлов шире.
PLANTUML_LIMIT_SIZE = "20000"

MVN = ROOT / "mvnw"


def run(command: list[str], *, what: str) -> None:
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode != 0:
        sys.exit(f"{what}: команда вернула {result.returncode}\n  {' '.join(command)}")


def generate_puml() -> list[Path]:
    """Две цели плагина. Формат, версии и каталог — из pom.xml родителя."""
    run(
        [
            str(MVN), "-q", "--no-transfer-progress",
            f"{PLUGIN}:aggregate",
            "-DoutputFileName=reactor",
        ],
        what="граф реактора",
    )
    run(
        [
            str(MVN), "-q", "--no-transfer-progress",
            "-pl", AGGREGATING_MODULE,
            f"{PLUGIN}:graph",
            "-DshowDuplicates=true",
            "-DshowConflicts=true",
            f"-DoutputFileName={AGGREGATING_MODULE}",
        ],
        what="граф модуля " + AGGREGATING_MODULE,
    )

    produced = [OUTPUT_DIR / "reactor.puml", OUTPUT_DIR / f"{AGGREGATING_MODULE}.puml"]
    missing = [p for p in produced if not p.is_file()]
    if missing:
        sys.exit("плагин не создал: " + ", ".join(str(p.relative_to(ROOT)) for p in missing))
    return produced


def render(puml_files: list[Path]) -> None:
    local = shutil.which("plantuml")
    if local:
        for puml in puml_files:
            run(
                [local, "-tpng", "-DPLANTUML_LIMIT_SIZE=" + PLANTUML_LIMIT_SIZE, str(puml)],
                what=f"рендер {puml.name}",
            )
        return

    if not shutil.which("docker"):
        sys.exit(
            "нечем рендерить: нет ни plantuml в PATH, ни docker.\n"
            "  brew install plantuml — или запустить с --puml и отрисовать позже"
        )

    run(
        [
            "docker", "run", "--rm",
            "-e", f"PLANTUML_LIMIT_SIZE={PLANTUML_LIMIT_SIZE}",
            "-v", f"{OUTPUT_DIR}:/data",
            "plantuml/plantuml", "-tpng", "/data",
        ],
        what="рендер через docker",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Граф зависимостей: .puml и .png")
    parser.add_argument("--puml", action="store_true", help="только .puml, без рендера")
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    puml_files = generate_puml()

    if args.puml:
        print("Готово (без рендера): " + ", ".join(p.name for p in puml_files))
        return 0

    render(puml_files)

    images = sorted(OUTPUT_DIR.glob("*.png"))
    if not images:
        sys.exit("рендер прошёл, но .png не появились")
    for image in images:
        print(f"{image.relative_to(ROOT)} — {image.stat().st_size // 1024} КБ")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
