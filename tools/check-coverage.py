#!/usr/bin/env python3
"""Пороги покрытия кода тестами по общему отчёту JaCoCo.

Отчёт собирает ``archi-bootstrap`` на фазе ``verify`` (цель ``report-aggregate``):
классы всех модулей реактора и данные юнит- и интеграционных тестов каждого.
Порог проверяется здесь, а не целью ``jacoco:check``: та видит только классы
своего модуля, а адаптеры хранения и REST покрывают интеграционные тесты
``archi-bootstrap`` — в модуле адаптера их нет.

Мера — строки (счётчик ``LINE``). Пороги — минимум стандарта, ниже него сборка
красная: модуль слоя ``domain`` — 90 %, ``application`` — 80 %, адаптер — 50 %,
весь код вместе — 60 %. ``archi-bootstrap`` отдельного порога не имеет и входит
в общий.

Отчёт должен содержать данные обоих видов тестов: шаг юнит-тестов и шаг
интеграционных идут без ``clean`` между ними, иначе адаптеры окажутся
непокрытыми.

Процент строк набирается и позитивными путями, поэтому сверх порога
поимённая проверка:

- каждая строка ``throw`` модуля ``domain`` исполнена тестом: отказ домена —
  правило, и непроверенный отказ не виден в проценте. Ветка, до которой код
  не доходит по построению, помечается комментарием ``// недостижимо — причина``
  строкой над ``throw``.

Запуск: tools/check-coverage.py   (из любого каталога, после ./mvnw verify)
        tools/check-coverage.py --module <каталог модуля>
            отказы домена по отчёту юнит-тестов модуля
            (после ./mvnw -pl <модуль> test jacoco:report)
Код выхода 1, если порог не пройден, есть непроверенный отказ или отчёта нет.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORT = ROOT / "archi-bootstrap/target/site/jacoco-aggregate/jacoco.xml"

# (суффикс имени модуля, порог в процентах)
LAYERS = [
    ("-domain", 90.0),
    ("-application", 80.0),
    ("-adapter-rest", 50.0),
    ("-adapter-persistence", 50.0),
]
TOTAL = 60.0


def lines(element: ET.Element) -> tuple[int, int]:
    """Покрытые и все строки по счётчику LINE элемента отчёта."""
    for counter in element.findall("counter"):
        if counter.get("type") == "LINE":
            covered, missed = int(counter.get("covered")), int(counter.get("missed"))
            return covered, covered + missed
    return 0, 0


def percent(covered: int, total: int) -> float:
    return 100.0 * covered / total if total else 100.0


THROW = re.compile(r"\bthrow\b")
UNREACHABLE = "// недостижимо"


def sources(module: Path) -> Path:
    return module / "src/main/java"


def module_dir(name: str) -> Path | None:
    """Каталог модуля реактора по имени группы отчёта."""
    found = [p for p in ROOT.glob(f"**/{name}/pom.xml") if "target" not in p.parts and "node_modules" not in p.parts]
    return found[0].parent if found else None


def unreachable(text: list[str], number: int) -> bool:
    """Помечена ли строка ``throw`` как недостижимая — на ней же или строкой выше."""
    return UNREACHABLE in text[number - 1] or number > 1 and text[number - 2].strip().startswith(UNREACHABLE)


def uncovered_throws(element: ET.Element, module: Path) -> list[str]:
    """Строки ``throw``, которые не исполнил ни один тест: путь к файлу и номер строки."""
    missed = []
    for package in element.iter("package"):
        for source in package.findall("sourcefile"):
            path = sources(module) / package.get("name") / source.get("name")
            text = path.read_text(encoding="utf-8").splitlines()
            covered = {int(line.get("nr")): int(line.get("ci")) for line in source.findall("line")}
            for number, line in enumerate(text, 1):
                if THROW.search(line) and covered.get(number) == 0 and not unreachable(text, number):
                    missed.append(f"{path.relative_to(ROOT)}:{number}")
    return missed



def check_module(module: Path) -> int:
    report = module / "target/site/jacoco/jacoco.xml"
    if not report.exists():
        print(f"Нет отчёта {report.relative_to(ROOT)}: сначала ./mvnw -pl {module.relative_to(ROOT)} test jacoco:report.")
        return 1
    missed = uncovered_throws(ET.parse(report).getroot(), module)
    print("\n".join(missed) if missed else "Все отказы модуля исполнены тестами.")
    return 1 if missed else 0


def main() -> int:
    if len(sys.argv) == 3 and sys.argv[1] == "--module":
        return check_module((Path.cwd() / sys.argv[2]).resolve())
    if not REPORT.exists():
        print(f"Нет отчёта {REPORT.relative_to(ROOT)}: сначала ./mvnw verify.")
        return 1
    report = ET.parse(REPORT).getroot()
    problems = []
    covered_all = total_all = 0
    for group in report.findall("group"):
        name = group.get("name")
        covered, total = lines(group)
        covered_all += covered
        total_all += total
        for suffix, threshold in LAYERS:
            if name.endswith(suffix) and percent(covered, total) < threshold:
                problems.append(f"{name}: {percent(covered, total):.1f} % строк, порог {threshold:.0f} %")
        module = module_dir(name)
        if module and name.endswith("-domain"):
            problems += [f"отказ без теста: {where}" for where in uncovered_throws(group, module)]
    if percent(covered_all, total_all) < TOTAL:
        problems.append(f"весь код: {percent(covered_all, total_all):.1f} % строк, порог {TOTAL:.0f} %")
    if problems:
        print("\n".join(problems))
        print(f"\nНарушений: {len(problems)}.")
        return 1
    print(f"Покрытие строк: {percent(covered_all, total_all):.1f} %, модулей {len(report.findall('group'))}. "
          "Пороги пройдены.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
