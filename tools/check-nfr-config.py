#!/usr/bin/env python3
"""Сверка конфигурации приложения с нефункциональными требованиями.

Числа из требований — пул соединений, таймауты, пределы помощника и импорта —
записаны в ``application.yaml`` числом, а не переменной окружения (ADR-0026):
они не зависят от стенда, а меняются правкой ``spec/nfr/<bc>.yaml`` и YAML
в одном коммите. Скрипт держит эту связь: каждая строка ``PAIRS`` сопоставляет
настройку приложения с путём в файле требований, и расхождение значений —
отказ. Без проверки спека и конфигурация расходятся молча: ни компилятор,
ни тесты этого не видят.

Длительности сравниваются в миллисекундах: в YAML приложения бывают и числа
в миллисекундах (Hikari), и ``30s``/``5m``; в требованиях — ``3s``/``2m``.

Список пар закрытый. Новое число в ``spec/nfr`` с настройкой в приложении —
новая строка здесь; иначе проверка о нём не знает.

YAML разбирается без зависимостей: оба файла — вложенные словари
``ключ: значение``; списки и многострочные значения пропускаются.

Запуск: tools/check-nfr-config.py   (из любого каталога)
Код выхода 1, если найдено расхождение или не найден ключ.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "archi-bootstrap/src/main/resources/application.yaml"
NFR = ROOT / "spec/nfr"

# (настройка приложения, вид значения, контекст, путь в spec/nfr/<контекст>.yaml)
# Вид: number — число как есть; ms — число в миллисекундах; duration — 15s, 30m.
PAIRS = [
    ("spring.datasource.hikari.maximum-pool-size", "number", "modeling", "storage.connection_pool.max_size"),
    ("spring.datasource.hikari.minimum-idle", "number", "modeling", "storage.connection_pool.min_idle"),
    ("spring.datasource.hikari.connection-timeout", "ms", "modeling", "storage.connection_pool.connection_timeout"),
    ("spring.datasource.hikari.leak-detection-threshold", "ms", "modeling",
     "storage.connection_pool.leak_detection_threshold"),
    ("spring.jpa.properties.jakarta.persistence.query.timeout", "ms", "modeling", "storage.query_timeout"),
    ("spring.jpa.properties.jakarta.persistence.query.timeout", "ms", "interchange", "storage.query_timeout"),
    ("archi.interchange.max-parallel-imports-per-workspace", "number", "interchange",
     "concurrency.max_parallel_imports_per_workspace"),
    ("archi.interchange.png-max-area-px", "number", "interchange", "image_limits.png_max_area_px"),
    ("archi.interchange.max-parallel-rasterizations", "number", "interchange",
     "concurrency.max_parallel_rasterizations"),
    ("archi.interchange.rasterization-wait", "ms", "interchange", "concurrency.rasterization_wait"),
    ("archi.git.timeout", "duration", "interchange", "upstream_calls.git_remote.timeout"),
    ("archi.git.retry-base", "duration", "interchange", "upstream_calls.git_remote.retries.initial_interval"),
    ("archi.git.retry-max", "duration", "interchange", "upstream_calls.git_remote.retries.max_interval"),
    ("archi.ai.timeout", "duration", "advisor", "upstream_calls.anthropic_api.timeout"),
    ("archi.ai.connect-timeout", "duration", "advisor", "upstream_calls.anthropic_api.connect_timeout"),
    ("archi.ai.max-retries", "number", "advisor", "upstream_calls.anthropic_api.retries.count"),
    ("archi.ai.max-context-tokens", "number", "advisor", "rate_limits.context.max_tokens"),
    ("archi.ai.min-coverage", "number", "advisor", "rate_limits.context.min_coverage"),
    ("archi.ai.default-monthly-token-limit", "number", "advisor", "budget.default_monthly_token_limit"),
    ("archi.ai.user-requests-per-minute", "number", "advisor", "rate_limits.per_user.requests_per_minute"),
    ("archi.ai.workspace-requests-per-minute", "number", "advisor",
     "rate_limits.per_workspace.requests_per_minute"),
]

# Пул interchange — пул приложения: отдельных чисел у него нет, только отметка.
SHARED = [("interchange", "storage.connection_pool", "shared")]

DURATION = re.compile(r"^(\d+(?:\.\d+)?)(ms|s|m|h)$")
UNIT_MS = {"ms": 1, "s": 1000, "m": 60_000, "h": 3_600_000}


def flatten(path: Path) -> dict[str, str]:
    """Пары «ключ через точку → значение» для скалярных значений словарей."""
    text = path.read_text(encoding="utf-8")
    if text.startswith("---"):
        text = text.split("\n---", 1)[1].split("\n", 1)[1]
    result: dict[str, str] = {}
    stack: list[tuple[int, str]] = []
    block_indent = None
    for raw in text.splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip(" "))
        if block_indent is not None:
            if indent > block_indent:
                continue
            block_indent = None
        line = raw.strip()
        if line.startswith("- "):
            continue
        match = re.match(r"^([^:\s][^:]*?):(?:\s+(.*))?$", line)
        if not match:
            continue
        key, value = match.group(1), (match.group(2) or "")
        value = re.split(r"\s+#", value, maxsplit=1)[0].strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        full = ".".join([k for _, k in stack] + [key])
        if value in ("", "|", ">"):
            if value in ("|", ">"):
                block_indent = indent
            else:
                stack.append((indent, key))
            continue
        result[full] = value.strip("'\"")
    return result


def normalize(value: str, kind: str) -> float | None:
    if kind == "ms":
        return float(value) if re.fullmatch(r"\d+", value) else None
    if kind == "duration":
        match = DURATION.match(value)
        return float(match.group(1)) * UNIT_MS[match.group(2)] if match else None
    try:
        return float(value)
    except ValueError:
        return None


def spec_ms(value: str, kind: str) -> float | None:
    """Значение из требований в тех же единицах, что у приложения."""
    if kind in ("ms", "duration"):
        match = DURATION.match(value)
        return float(match.group(1)) * UNIT_MS[match.group(2)] if match else None
    return normalize(value, "number")


def main() -> int:
    app = flatten(APP)
    specs: dict[str, dict[str, str]] = {}
    problems: list[str] = []
    for setting, kind, context, path in PAIRS:
        spec = specs.setdefault(context, flatten(NFR / f"{context}.yaml"))
        if setting not in app:
            problems.append(f"application.yaml: нет настройки {setting}")
            continue
        if path not in spec:
            problems.append(f"spec/nfr/{context}.yaml: нет {path}")
            continue
        ours, theirs = normalize(app[setting], kind), spec_ms(spec[path], kind)
        if ours is None or theirs is None:
            problems.append(f"{setting} = {app[setting]!r} и spec/nfr/{context}.yaml#{path} = {spec[path]!r}: "
                            "значение не разбирается")
        elif ours != theirs:
            problems.append(f"{setting} = {app[setting]}, а spec/nfr/{context}.yaml#{path} = {spec[path]}")
    for context, path, expected in SHARED:
        spec = specs.setdefault(context, flatten(NFR / f"{context}.yaml"))
        if spec.get(path) != expected:
            problems.append(f"spec/nfr/{context}.yaml#{path} = {spec.get(path)!r}, а приложение держит один пул "
                            f"на все контексты — ожидается {expected!r}")
    if problems:
        print("\n".join(problems))
        print(f"\nРасхождений: {len(problems)}. Правило — ADR-0026: значение меняется в spec/nfr и YAML вместе.")
        return 1
    print(f"Сверено пар «настройка — требование»: {len(PAIRS) + len(SHARED)}. Расхождений нет.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
