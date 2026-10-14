---
version: 1.0
last_modified: 2026-10-14
adr: ADR-0024
status: accepted
date: 2026-10-14
---

# ADR-0024 — Транзакция — порт `UnitOfWork`, application-слой без Spring

## Статус

Принято 2026-10-14 по итогам этапа 0 ревью кода (отклонение D-08). Записывает решение,
действующее со второго этапа (до сих пор — только в `CLAUDE.md`), и уточняет
[`ADR-0001`](0001-maven-multi-module.md). Отступает от внутреннего стандарта
`java-spring-code-guidelines-v2.md` в §4.8.1.

## Контекст

Стандарт ставит `@Transactional` на application-сервис или обработчик команды,
`readOnly = true` — на чтение. Это требует Spring в application-слое.

В проекте `domain` и `application` всех трёх контекстов — без Spring, и это держит
сборка: правило `maven-enforcer-plugin` из `pluginManagement` родителя запрещает
`org.springframework*`, `jakarta.persistence`, `jakarta.transaction`, `org.hibernate*`
в шести модулях, включая транзитивные зависимости. Application-слой поэтому
тестируется фейками без контекста Spring.

## Решение

- **Граница транзакции — порт `UnitOfWork`** application-слоя: `write(work)` и `read(work)`.
  Порт объявлен в `modeling.application.port` и `advisor.application.port`; interchange
  пользуется портом modeling, потому что импорт и запись модели — одна транзакция.
- **Реализация — `TransactionalUnitOfWork` в `archi-bootstrap`** поверх
  `TransactionTemplate`; чтение — с `readOnly`.
- **Одна запись use case'а — один вызов `write`.** Внутри — только база: сетевые вызовы
  (Git, Anthropic API) выполняются вне его, как требует стандарт (§4.8.4).
- **События публикуются после коммита** (`AfterCommitEventPublisher`), `ModelVersionCommitted`
  пишется в outbox той же транзакцией — по `ADR-0003`.
- `@Transactional` в коде проекта не используется: ни в application, где его запрещает
  сборка, ни в адаптерах и bootstrap, где он бы размыл, кто держит границу.

## Последствия

- Находки ревью «нет `@Transactional`» не заводятся. Заводятся: несколько `write` в одном
  use case'е, где нужна атомарность; сетевой вызов внутри `write`; запись внутри `read`.
- Требование стандарта о границах транзакции (§4.8.4) действует полностью — меняется
  только механизм.
