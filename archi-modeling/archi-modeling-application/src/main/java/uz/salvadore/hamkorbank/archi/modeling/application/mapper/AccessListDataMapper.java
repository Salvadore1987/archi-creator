package uz.salvadore.hamkorbank.archi.modeling.application.mapper;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.AccessListResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.ModelAccessList;

/** Список доступа — в результат сценария. */
public final class AccessListDataMapper {

    private AccessListDataMapper() {
    }

    /** Список доступа как результат: строки в порядке списка. */
    public static AccessListResponse response(ModelAccessList list) {
        return new AccessListResponse(list.id().value(), list.entries().stream()
                .map(e -> new AccessListResponse.Entry(e.principalType().name(), e.principal(), e.access().name()))
                .toList());
    }
}
