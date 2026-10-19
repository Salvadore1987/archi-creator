package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.FindingId;

/**
 * Помощник не изменяет модель — и проверяется это формой типов: результат сессии
 * состоит только из текста, ссылок на существующие объекты и предложений по имени
 * и типу. Новое поле, способное нести идентификатор создаваемого объекта или
 * команду, роняет тест.
 */
class AdvisorResultTest {

    private static final List<Class<?>> RESULT_TYPES = List.of(Finding.class, ModelPlan.class, PlannedElement.class,
            PlannedRelation.class, AdvisorAnswer.class, ElementRef.class);

    /** Что может лежать в результате: текст, флаги, перечисления и сами типы результата. */
    private static final Set<Class<?>> ALLOWED_LEAVES = Set.of(String.class, boolean.class, FindingId.class);

    @Test
    @DisplayName("INV-ADV-004: типы результата состоят только из текста, ссылок и предложений")
    void resultCarriesNoMutations() {
        List<String> offending = new ArrayList<>();
        for (Class<?> type : RESULT_TYPES) {
            assertTrue(type.isRecord(), type.getSimpleName() + " — неизменяемая запись");
            for (RecordComponent component : type.getRecordComponents()) {
                if (!allowed(component.getGenericType())) {
                    offending.add(type.getSimpleName() + "." + component.getName());
                }
            }
        }
        assertEquals(List.of(), offending, "поле результата может нести изменение модели");
    }

    @Test
    @DisplayName("INV-ADV-004: у результата нет метода, который можно применить")
    void resultHasNothingToApply() {
        for (Class<?> type : RESULT_TYPES) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    String name = method.getName().toLowerCase(Locale.ROOT);
                    assertTrue(!name.startsWith("apply") && !name.startsWith("execute") && !name.startsWith("create"),
                            type.getSimpleName() + "#" + method.getName());
                }
            }
        }
    }

    @Test
    @DisplayName("INV-ADV-004: план называет элементы по имени и типу, без идентификаторов создаваемых объектов")
    void planIsNeverApplied() {
        ModelPlan plan = new ModelPlan(
                List.of(new PlannedElement("archimate:ApplicationService", "Платежи", null)),
                List.of(new PlannedRelation("archimate:ServingRelationship", "Платежи", "Клиент", true)),
                null);

        assertEquals(List.of("archimate:ApplicationService"), plan.elementTypes());
        assertNull(plan.elements().getFirst().existingRef());
    }

    private static boolean allowed(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            return (raw == List.class || raw == Optional.class)
                    && allowed(parameterized.getActualTypeArguments()[0]);
        }
        Class<?> raw = (Class<?>) type;
        return ALLOWED_LEAVES.contains(raw) || raw.isEnum() || RESULT_TYPES.contains(raw);
    }
}
