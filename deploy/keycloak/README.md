# Realm Keycloak

`archi-realm.json` — экспорт realm'а `archi`, который
[`docker-compose.yml`](../../docker-compose.yml) монтирует в Keycloak
и импортирует при первом старте (`--import-realm`). Смысл файла — чтобы
поднятие было воспроизводимым: роли FR-28 и клиент SPA не настраиваются
руками в админке, а приезжают вместе с репозиторием.

## Что внутри

| Что | Значение |
|---|---|
| Realm | `archi`, issuer `http://localhost:8081/realms/archi` |
| Роли | `VIEWER`, `ARCHITECT`, `ADMIN` — ровно три роли FR-28 |
| Клиент | `archi-creator-ui` — публичный, authorization code + PKCE (S256) |

Бэкенд своего клиента не заводит: он ресурс-сервер и только проверяет
подпись токена по JWKS, ничего не запрашивая от своего имени.

Роли лежат в токене как `realm_access.roles` — оттуда их достаёт
`KeycloakRealmRolesConverter`.

## Демонстрационные пользователи

Три пользователя с паролем, равным имени: `viewer`, `architect`,
`administrator`. Они нужны, чтобы `docker compose up` сразу давал вход
с ролью — критерий готовности этапа 0.

**Это realm для локального поднятия, а не для боя.** В бою realm заводится
отдельно, пользователи приходят из каталога банка, а пароля в репозитории
нет и быть не может (NFR-06). Файл называется тем, чем является: набором
настроек для разработки.

## Проверить руками

```bash
curl -s -d 'client_id=archi-creator-ui' -d 'username=architect' \
     -d 'password=architect' -d 'grant_type=password' \
     http://localhost:8081/realms/archi/protocol/openid-connect/token
```

В полезной нагрузке ответа — `realm_access.roles: ["ARCHITECT"]`.
