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

Запуск: tools/check-coverage.py   (из любого каталога, после ./mvnw verify)
Код выхода 1, если порог не пройден или отчёта нет.
"""
from __future__ import annotations

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


def main() -> int:
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
    if percent(covered_all, total_all) < TOTAL:
        problems.append(f"весь код: {percent(covered_all, total_all):.1f} % строк, порог {TOTAL:.0f} %")
    if problems:
        print("\n".join(problems))
        print(f"\nНиже порога: {len(problems)}.")
        return 1
    print(f"Покрытие строк: {percent(covered_all, total_all):.1f} %, модулей {len(report.findall('group'))}. "
          "Пороги пройдены.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
