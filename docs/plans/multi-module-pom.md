# Перекройка `pom.xml` в multi-module по ADR-0001

## Контекст

[`ADR-0001`](../../spec/adr/0001-maven-multi-module.md) требует, чтобы граница
«домен не знает о фреймворке» держалась **сборкой**, а не соглашением: у доменного
модуля в `pom.xml` нет ни Spring, ни JPA, поэтому нарушение не компилируется.

До этой задачи проект был одномодульным: корневой `pom.xml` — голый Maven без
зависимостей, единственный класс `Main.java` — заготовка IDEA. ADR был принят
2026-09-22 и помечен как нереализованный.

Момент выбран не случайно: кода нет, и цена перекройки минимальна. Через три этапа
это был бы переезд всего дерева исходников.

## Раскладка

Четыре каталога в корне вместо тринадцати: у каждого bounded context'а свой
агрегатор, внутри — четыре модуля-слоя.

```
pom.xml                                   parent, packaging pom
archi-modeling/                           агрегатор BC modeling
  archi-modeling-domain/                  чистый Java, зависимостей нет
  archi-modeling-application/             -> domain
  archi-modeling-adapter-rest/            -> application + Spring Web
  archi-modeling-adapter-persistence/     -> application + Spring Data JPA
archi-interchange/                        то же для interchange
archi-advisor/                            то же для advisor
archi-bootstrap/                          все 12 модулей + Spring Boot, здесь jar
```

Пакеты: `uz.salvadore.hamkorbank.archi.<bc>.<layer>`, класс запуска —
`uz.salvadore.hamkorbank.archi.ArchiCreatorApplication`, чтобы сканирование
компонентов накрывало все модули без явного `basePackages`.

## Решения по ходу

### Агрегатор на контекст, а не тринадцать каталогов в корне

Плоский список из тринадцати модулей в корне рядом с `docs/`, `spec/` и `tools/`
читается плохо. Промежуточный агрегатор стоит три лишних `pom.xml` и повторяет
раскладку спеки (`spec/domain/<bc>/`), где контекст — единица группировки.

### Граница держится ещё и enforcer'ом

ADR говорит: гарантия в отсутствии зависимости. Этого достаточно, пока никто
не добавил её обратно. Поэтому в шести модулях (`domain` и `application` трёх
контекстов) включено правило `maven-enforcer-plugin`, запрещающее
`org.springframework*`, `jakarta.persistence`, `jakarta.transaction`,
`org.hibernate*` в любом scope, включая транзитивные. Конфигурация правила —
одна, в `pluginManagement` родителя; модуль только объявляет плагин.

Проверено умышленным нарушением: добавление `spring-context` в
`archi-modeling-domain` останавливает сборку на фазе `validate`.

### Что НЕ сделано сознательно

- **Изоляция контекстов друг от друга не enforced.** `archi-advisor-domain`
  не должен зависеть от `archi-modeling-domain` — в спеке идентификаторы чужих
  контекстов объявляются локально (`external_context: modeling`). Сейчас это
  держится тем, что зависимость просто не объявлена. Правилом enforcer'а это
  не выразить одной общей конфигурацией: каждому модулю нужен свой список
  запрещённых, а описывать шесть разных — дороже, чем ArchUnit-правило,
  которое всё равно предусмотрено спекой в разделе «Верификация».
- **Генерация из `contracts/*.openapi.yaml` не подключена.** В таблице
  `spec/README.md` модуль `archi-<bc>-adapter-rest` помечен как генерируемый.
  Подключение `openapi-generator` — отдельная работа со своими решениями
  (что генерировать: интерфейсы или полные контроллеры, куда кладутся DTO).
- **`application.yaml`, `docker-compose.yml`, Flyway-миграции не заведены.**
  Конфигурация без БД и Keycloak — это выдумывание параметров; §10.1 и §10.3
  описывают целевое состояние, но их содержимое рождается вместе с этапом 2.
- **Фронтенд-модуля нет.** `frontend-maven-plugin` из §10.2 подключать некуда:
  ни `package.json`, ни исходников React в репозитории пока нет.
- **Тестов нет.** `junit-jupiter` в test-scope доступен всем модулям, но
  ни одного теста не написано: первым по §12 идёт golden-file round-trip
  этапа 1.

## Расхождения с `docs/`

`docs/` ненормативен, но расхождения лучше зафиксировать, чем обнаружить.

**§3.3 описывает пакеты одного модуля** (`web`, `service`, `repository`,
`entity`, `archi`, `oef`, `metamodel`, `export`, `ai`, `security`) — это взгляд
до ADR-0001. Ответственности разъезжаются по модулям так:

| Пакет §3.3 | Куда уезжает |
|---|---|
| `web` | `archi-<bc>-adapter-rest` каждого контекста |
| `service` | `archi-<bc>-application` (сценарии) и `archi-<bc>-domain` (правила) |
| `repository`, `entity` | `archi-<bc>-adapter-persistence` |
| `archi` (кодек), `oef`, `export` | `archi-interchange-*` |
| `metamodel` | `archi-modeling-domain` |
| `ai` | `archi-advisor-*` |
| `security` | `archi-bootstrap` |

**§10.2 обещает `target/archi-creator-1.0-SNAPSHOT.jar`.** Теперь jar собирается
в `archi-bootstrap/target/`. Имя сохранено через `finalName`, изменился каталог —
это придётся учесть в `docker-compose.yml` и в CI, когда они появятся.

## Версии

| Что | Версия | Откуда |
|---|---|---|
| Java | 25 | `maven.compiler.release`, требование §3.2 |
| Spring Boot | 4.1.1 | BOM `spring-boot-dependencies`, импортируется в `dependencyManagement` родителя |
| maven-compiler-plugin | 3.16.0 | последняя стабильная; 4.0.0 в бете |
| maven-surefire-plugin | 3.6.0 | JUnit Platform 6 |
| maven-enforcer-plugin | 3.6.3 | |

Версии Flyway (12.4.0), PostgreSQL-драйвера и JUnit (6.0.3) приходят из BOM —
в модулях не пинятся.

Импорт BOM в родителе **не** тащит Spring на classpath: `dependencyManagement`
только фиксирует версии. Доменные модули остаются чистыми.

## Проверка

```
mvn -q clean package        13 модулей собираются, jar в archi-bootstrap/target/
mvn -q dependency:tree      у доменных модулей только junit в test-scope
```
