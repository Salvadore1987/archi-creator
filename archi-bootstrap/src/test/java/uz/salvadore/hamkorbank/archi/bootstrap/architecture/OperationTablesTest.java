package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.application.access.AdvisorOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;

/**
 * Таблица ролей записана дважды — в спецификации контекста ({@code authorization}) и в перечислении
 * операций, по которому проверяется доступ. Тест держит их одинаковыми в обе стороны.
 */
class OperationTablesTest {

    private static final Path SPEC = Path.of("../spec/nfr");
    private static final Pattern ROW = Pattern.compile("^ {4}(\\w+):\\s*\\[([^]]*)]");

    @Test
    @DisplayName("FR-28: роли операций modeling совпадают с таблицей authorization спецификации")
    void modelingMatchesSpecification() throws IOException {
        assertEquals(table("modeling"), Arrays.stream(Operation.values()).collect(Collectors.toMap(
                Operation::useCase, o -> names(o.roles()), (a, b) -> a, TreeMap::new)));
    }

    @Test
    @DisplayName("FR-28: роли операций interchange совпадают с таблицей authorization спецификации")
    void interchangeMatchesSpecification() throws IOException {
        assertEquals(table("interchange"), Arrays.stream(InterchangeOperation.values()).collect(Collectors.toMap(
                InterchangeOperation::useCase, o -> names(o.roles()), (a, b) -> a, TreeMap::new)));
    }

    @Test
    @DisplayName("FR-28: роли операций advisor совпадают с таблицей authorization спецификации")
    void advisorMatchesSpecification() throws IOException {
        assertEquals(table("advisor"), Arrays.stream(AdvisorOperation.values()).collect(Collectors.toMap(
                AdvisorOperation::useCase, o -> names(o.roles()), (a, b) -> a, TreeMap::new)));
    }

    /** Строки раздела {@code authorization} файла контекста: сценарий → роли. */
    private static Map<String, Set<String>> table(String context) throws IOException {
        Map<String, Set<String>> table = new TreeMap<>();
        boolean inside = false;
        for (String line : Files.readAllLines(SPEC.resolve(context + ".yaml"))) {
            if (line.startsWith("  authorization:")) {
                inside = true;
            } else if (inside && !line.isBlank() && !line.startsWith("    ")) {
                break;
            } else if (inside) {
                Matcher row = ROW.matcher(line);
                if (row.find()) {
                    table.put(row.group(1), Arrays.stream(row.group(2).split(","))
                            .map(String::strip).collect(Collectors.toCollection(TreeSet::new)));
                }
            }
        }
        assertFalse(table.isEmpty(), context + ": раздел authorization не найден");
        return table;
    }

    private static Set<String> names(Set<? extends Enum<?>> roles) {
        return roles.stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
    }
}
