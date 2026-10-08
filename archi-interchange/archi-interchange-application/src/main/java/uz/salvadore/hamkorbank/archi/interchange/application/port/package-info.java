/**
 * Исходящие порты interchange. Транзакцию interchange берёт у modeling
 * ({@code modeling.application.port.UnitOfWork}): импорт и запись модели — одна
 * транзакция (UC-IXC-001, п. 5), и две разные абстракции одной транзакции
 * разошлись бы.
 */
package uz.salvadore.hamkorbank.archi.interchange.application.port;
