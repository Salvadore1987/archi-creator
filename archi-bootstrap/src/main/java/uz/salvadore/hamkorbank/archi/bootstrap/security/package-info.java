/**
 * Безопасность приложения: проверка JWT Keycloak, маппинг ролей и правила доступа.
 *
 * <p>По §3.3 это пакет {@code security}. Он живёт в {@code archi-bootstrap},
 * а не в адаптерах: правила доступа общие для всех трёх контекстов, и разносить
 * их по три копии значило бы получить три разных ответа на один вопрос.
 *
 * <p>Роли — {@code VIEWER}, {@code ARCHITECT}, {@code ADMIN} (FR-28). Какая роль
 * что может, перечислено не здесь, а в {@code spec/nfr/<bc>.yaml},
 * секция {@code security.authorization}: правило принадлежит use case'у,
 * а не фильтру.
 */
package uz.salvadore.hamkorbank.archi.bootstrap.security;
