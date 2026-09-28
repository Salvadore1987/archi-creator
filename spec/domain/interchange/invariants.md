---
version: 0.1
last_modified: 2026-09-22
bounded_context: interchange
---

# Бизнес-инварианты Interchange

Каждый инвариант имеет уникальный код вида `INV-IXC-NNN`, где `IXC` —
префикс bounded context'а `interchange`.

Код используется в тестах (`@DisplayName("INV-IXC-005: ...")`), в поле
`code` ответа `application/problem+json`, в логах и метриках
(label `error_code`) и в референсах из ADR.

Коды не переиспользуются: удалённый инвариант помечается `[DEPRECATED]`,
новый получает следующий свободный номер.

Главный здесь — **INV-IXC-005**. Остальные девять существуют в основном
затем, чтобы он не сломался.

---

## INV-IXC-001 — Узел документа полон: идентификатор, тип, порядок, а неизвестное — дословно

**Тип:** структурный

**Формулировка:** каждый `DocumentNode`, построенный читателем, несёт
непустой `archiId`, строку типа и позицию среди соседей. Узел или атрибут,
которого метамодель текущей фазы не знает, сохраняется целиком как
`RawXmlFragment` с адресом родителя и позицией — не отбрасывается и не
нормализуется.

**Нарушение:** потерянный при чтении атрибут невозможно вернуть при записи,
и round-trip ломается ещё до того, как писатель начал работу. Ошибка
проявится на файле заказчика, а не на тестовом.

**Тест:** `ArchiReaderTest#unknownNodeIsPreservedAsRawFragment`

---

## INV-IXC-002 — Сессия импорта идёт по разрешённым переходам, применить можно только проверенное

**Тип:** state-machine

**Формулировка:** `RECEIVED → PARSED → VALIDATED → APPLIED`; отказ возможен
из любого нетерминального состояния. `ApplyImport` допустим только в
состоянии `VALIDATED`. `APPLIED` и `REJECTED` терминальны: повторное
применение той же сессии отклоняется.

**Нарушение:** применение неразобранного или непроверенного документа
означает запись в модель мимо всех проверок. Повторное применение
терминальной сессии дублирует модель.

**Тест:** `ImportSessionStateMachineTest#applyIsAllowedOnlyFromValidated`

---

## INV-IXC-003 — Повторная подача того же файла не создаёт вторую модель

**Тип:** идемпотентность

**Формулировка:** импорт с тем же `Idempotency-Key` возвращает существующую
сессию и её результат; новая модель не создаётся, новая версия не пишется.
Тот же ключ с другим `sourceHash` — конфликт, а не новый импорт.

**Нарушение:** ретрай прокси на трёхсекундном импорте (NFR-02) создаёт
модель-двойник. Архитектор обнаруживает это не сразу, и часть правок
уходит в копию.

**Тест:** `ImportIdempotencyTest#sameKeyReturnsExistingSession`

---

## INV-IXC-004 — Писатель детерминирован

**Тип:** структурный (воспроизводимость)

**Формулировка:** запись одного и того же `ModelDocument` дважды даёт
побайтово идентичный результат. Никаких меток времени, случайных
идентификаторов, зависимости от порядка обхода хеш-таблиц или локали в
выводе. Это относится и к `.archimate`, и к разложенному YAML для Git.

**Нарушение:** недетерминированный писатель делает diff в Git шумом:
каждая выгрузка показывает изменения там, где ничего не менялось, и
ревью предложений (этап 7c) становится бессмысленным.

**Тест:** `ArchiWriterTest#writingTwiceProducesIdenticalBytes`,
`GitYamlWriterTest#exportIsByteStable`

---

## INV-IXC-005 — Lossless round-trip `.archimate`

**Тип:** доменное правило (критический контур, NFR-05)

**Формулировка:** цикл «прочитать файл → построить `ModelDocument` →
записать файл» даёт XML, семантически идентичный исходному. Сохраняются:
идентификаторы (буквально), дерево папок вместе с вложенностью, порядок
узлов, свойства и документация, геометрия представлений в системе
координат родителя, все неподдержанные фрагменты. `targetConnections`
восстанавливается из множества `sourceConnection`, но порядок
идентификаторов в атрибуте воспроизводится как есть. Отчёт о потерях
для этого формата обязан быть пустым.

**Нарушение:** любое расхождение означает, что файл, побывавший в Archi
Creator, отличается от исходного. Именно это обещание снимает
vendor lock-in; без него продукт не имеет смысла внедрять рядом с Archi.
Регрессия блокирует сборку.

**Тест:** `RoundTripGoldenFileTest#hamkorbankAsIsSurvivesRoundTrip`
(эталон — `docs/Hamkorbank_AS_IS_strict.archimate`,
[`docs/spec/backend.md` §9.1](../../../docs/spec/backend.md#91-golden-file-тесты-round-trip--критический-контур))

---

## INV-IXC-006 — Выгрузка в формат с потерями обязана вернуть отчёт

**Тип:** доменное правило

**Формулировка:** `ExportJob` в состоянии `RENDERED` или `DELIVERED` имеет
непустой `lossReport` как объект. Для форматов `OPEN_EXCHANGE`,
`CSV_CATALOG`, `SVG`, `PNG` отчёт перечисляет всё, что не перенеслось, с
причиной. Для `ARCHIMATE` и `GIT_YAML` список записей обязан быть пуст.
Пустой список — валидный отчёт; отсутствие отчёта — нет.

**Нарушение:** выгрузка без отчёта превращается в тихую потерю данных.
Получатель — аудит, регулятор, подрядчик — не узнает, что часть модели до
него не доехала, а обнаружит это через квартал в чужом инструменте
([`docs/spec/backend.md` §3.5](../../../docs/spec/backend.md#35-экспорт-в-open-exchange-format)).

**Тест:** `ExportJobTest#renderedJobAlwaysHasLossReport`,
`OefWriterTest#rawFragmentsAreListedAsLosses`

---

## INV-IXC-007 — Строгий импорт меняет порог отказа, но не отношение к повреждённым данным

**Тип:** доменное правило

**Формулировка:** при `strictMode = true` находка уровня `ERROR` переводит
сессию в `REJECTED`. При `strictMode = false` находки уровня `ERROR` и
`WARNING` попадают в отчёт, а импорт применяется. **Повреждённые данные
отклоняются всегда, независимо от режима**: неразбираемый XML, отсутствие
обязательных атрибутов, ссылка на несуществующий идентификатор внутри
файла.

**Нарушение:** приём повреждённого файла в мягком режиме создаёт модель,
которую нельзя выгрузить обратно, — round-trip ломается на данных, которые
вообще не следовало принимать (FR-49, FR-50,
[`docs/spec/backend.md` §8.3](../../../docs/spec/backend.md#83-строгость-импорта)).

**Тест:** `StrictImportTest#errorRejectsOnlyInStrictMode`,
`StrictImportTest#corruptedFileIsRejectedInBothModes`

---

## INV-IXC-008 — Выгружается зафиксированная версия, а не текущее состояние

**Тип:** структурный

**Формулировка:** `ExportJob` строится по конкретной версии модели
(`sourceVersionNo`), а не по состоянию на момент рендера. Выгрузка,
начатая до сохранения и завершённая после, обязана отдать содержимое
версии, которая была актуальна при запуске.

**Нарушение:** артефакт, собранный из двух состояний, — внутренне
несогласованная модель: элемент есть, а связи на него уже нет.
Для пакетной выгрузки всех представлений это ещё заметнее: картинки
расходятся между собой.

**Тест:** `ExportJobTest#exportPinsSourceVersion`

---

## INV-IXC-009 — Коммит в Git не переписывает историю

**Тип:** жизненный цикл

**Формулировка:** `PushModelToGit` добавляет коммит в ветку привязки.
Force push, rebase и удаление веток запрещены. Физическое удаление модели
в системе (`PurgeModel`) коммиты не переписывает: репозиторий остаётся
журналом.

**Нарушение:** переписанная история уничтожает то единственное, ради чего
Git здесь и появился, — воспроизводимый журнал изменений. От него же
зависит правило очистки снимков версий (FR-47): снимок удаляют, полагаясь
на то, что содержимое восстановимо из репозитория.

**Тест:** `GitPublisherTest#pushIsAlwaysFastForward`

---

## INV-IXC-010 — Слияние идёт по сущностям, а не по тексту

**Тип:** доменное правило

**Формулировка:** слияние предложения из ветки выполняется сопоставлением
объектов по стабильным идентификаторам (`archiId`) и сравнением полей.
Текстовое слияние YAML запрещено на уровне процесса: результат построчного
merge не является валидной моделью и может пройти проверку формата, будучи
семантически сломанным.

**Нарушение:** построчное слияние двух правок одного элемента даёт
объект, которого никто не создавал, — с полем из одной ветки и половиной
списка из другой ([`docs/spec/backend.md` §11.4](../../../docs/spec/backend.md#114-слияние-по-сущностям-а-не-по-тексту)).

**Тест:** `EntityMergeTest#disjointEditsMergeWithoutConflict`,
`EntityMergeTest#sameFieldEditsProduceFieldLevelConflict`

---

<!-- Добавляй новые инварианты в том же формате. Не переиспользуй номера. -->

## Маппинг на тесты

| Инвариант | Unit-тест (`archi-interchange-domain`) | Integration / golden-file (`archi-bootstrap`) |
|---|---|---|
| INV-IXC-001 | `ArchiReaderTest#unknownNodeIsPreservedAsRawFragment` | `RoundTripGoldenFileTest#opaqueNodesSurvive` |
| INV-IXC-002 | `ImportSessionStateMachineTest#applyIsAllowedOnlyFromValidated` | `ImportIT#applyTwiceIsRejected` |
| INV-IXC-003 | `ImportIdempotencyTest#sameKeyReturnsExistingSession` | `ImportIT#retryDoesNotCreateSecondModel` |
| INV-IXC-004 | `ArchiWriterTest#writingTwiceProducesIdenticalBytes` | `GitExportIT#repeatedExportProducesNoDiff` |
| INV-IXC-005 | `ArchiCodecTest#documentSurvivesWriteReadCycle` | `RoundTripGoldenFileTest#hamkorbankAsIsSurvivesRoundTrip` |
| INV-IXC-006 | `ExportJobTest#renderedJobAlwaysHasLossReport` | `OefExportIT#lossReportListsRawFragments` |
| INV-IXC-007 | `StrictImportTest#errorRejectsOnlyInStrictMode` | `ImportIT#corruptedFileIsRejectedInBothModes` |
| INV-IXC-008 | `ExportJobTest#exportPinsSourceVersion` | `BatchViewExportIT#allImagesComeFromOneVersion` |
| INV-IXC-009 | `GitPublisherTest#pushIsAlwaysFastForward` | `GitExportIT#historyIsAppendOnly` |
| INV-IXC-010 | `EntityMergeTest#disjointEditsMergeWithoutConflict` | `MergeRequestIT#conflictIsResolvedPerField` |

**Непокрытые инварианты:** ни один инвариант пока не связан ни с одним use
case'ом — каталог `application/interchange/usecases/` пуст. Это ожидаемое
состояние после раскатки scaffold'а и закрывается режимом `add-usecase`.

**Приоритет:** `INV-IXC-001`, `INV-IXC-004`, `INV-IXC-005` покрываются
первыми — этап 1 закрывается именно round-trip референсной модели
([`docs/archi-creator.md` §12](../../../docs/archi-creator.md#12-этапы-работ)).
Инварианты 009 и 010 относятся к этапам 7a–7c и до них не исполнимы.
