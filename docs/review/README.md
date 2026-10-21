# Ревью кода по стандарту Java / Spring Boot — находки

План, этапы и протокол — [`docs/plans/code-review.md`](../plans/code-review.md).
Стандарт — `java-spring-code-guidelines-v2.md`, версия 3.0, коммит `97bce16`.

Здесь лежит по файлу на этап: `NN-<имя>.md`. Код в ходе ревью не правится;
исправления — отдельным планом после этапа 18.

## Формат находки

| № | Уровень | Правило | Где | Суть | Предложение | Ловит сборка? |
|---|---|---|---|---|---|---|

- **№** — `<этап>-<порядковый>`, на него ссылается план исправлений.
- **Уровень** — ЗАПРЕЩЕНО, ОБЯЗАТЕЛЬНО, РЕКОМЕНДУЕТСЯ (маркеры стандарта, §0.2)
  или ПРОЕКТ — нарушение правила самого проекта (`CLAUDE.md`, `spec/`).
- Массовое нарушение одного правила — одна находка со счётчиком и списком файлов
  в приложении к файлу этапа.
- Расхождение, принятое в реестре отклонений (план, раздел «Реестр отклонений»),
  находкой не является.
- **Ловит сборка?** — есть ли проверка (ArchUnit, enforcer, скрипт `tools/`, тест);
  если нет, в плане исправлений сначала заводится проверка.

## Этапы

| Этап | Файл | Находок |
|---|---|---|
| 0. Подготовка | [`00-podgotovka.md`](00-podgotovka.md) | — (реестр отклонений) |
| 1. Механические проверки | [`01-mekhanicheskie-proverki.md`](01-mekhanicheskie-proverki.md) | 15 |
| 2. Домен modeling | [`02-domen-modeling.md`](02-domen-modeling.md) | 21 |
| 3. Домен interchange | [`03-domen-interchange.md`](03-domen-interchange.md) | 18 |
| 4. Домен advisor | [`04-domen-advisor.md`](04-domen-advisor.md) | 11 |
| 5. Application modeling | [`05-application-modeling.md`](05-application-modeling.md) | 12 |
| 6. Application interchange: импорт, экспорт, раскладка | [`06-application-interchange.md`](06-application-interchange.md) | 14 |
| 7. Application interchange: SVG, PNG, OEF | [`07-svg-png-oef.md`](07-svg-png-oef.md) | 10 |
| 8. Application interchange: Git | [`08-git.md`](08-git.md) | 26 |
| 9. Application advisor | [`09-application-advisor.md`](09-application-advisor.md) | 10 |
| 10. Адаптеры хранения и миграции | [`10-persistence.md`](10-persistence.md) | 9 |
