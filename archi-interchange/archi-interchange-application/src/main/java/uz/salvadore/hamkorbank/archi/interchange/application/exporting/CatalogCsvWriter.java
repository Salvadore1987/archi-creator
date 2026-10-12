package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.Layer;

/**
 * Каталог модели в CSV: zip из {@code elements.csv}
 * и {@code relations.csv}. Источник — снимок зафиксированной версии, тот же, что у
 * выгрузки {@code .archimate}: таблица и файл одной версии не расходятся.
 *
 * <p>Решения по формату — ради того, чтобы файл открылся у получателя: UTF-8 с BOM
 * (иначе русский Excel читает windows-1251), разделитель {@code ;} (в русской локали
 * Excel запятая полей не делит), {@code \r\n} между строками (RFC 4180), столбцы свойств
 * по частоте ключа — заполненные первыми. Обратной загрузки нет и не будет.
 *
 * <p>Zip детерминирован: время записей фиксировано, порядок строк — порядок файла.
 */
public final class CatalogCsvWriter {

    private final String yes;
    private final String no;

    /** Значения «размещён / не размещён» — на языке запроса, как и прочий текст выгрузки. */
    public CatalogCsvWriter(TextCatalog texts) {
        this.yes = texts.text(Message.of(InterchangeMessages.CSV_YES));
        this.no = texts.text(Message.of(InterchangeMessages.CSV_NO));
    }

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final long FIXED_ENTRY_TIME = 0L;
    private static final ArchiTypeRegistry REGISTRY = ArchiTypeRegistry.archimate32();

    public byte[] write(byte[] archimate, CatalogCsvOptions options) {
        ModelDocument document = new StaxArchiDocumentReader().read(new ByteArrayInputStream(archimate));
        Catalog catalog = new Catalog(document, options);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            entry(zip, "elements.csv", catalog.elements(options.separator()));
            entry(zip, "relations.csv", catalog.relations(options.separator()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, String csv) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(FIXED_ENTRY_TIME);
        zip.putNextEntry(entry);
        zip.write(BOM);
        zip.write(csv.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Разобранный документ в терминах каталога: пути папок, размещения, свойства. */
    private final class Catalog {

        private final Map<String, String> folderPath = new HashMap<>();
        private final Map<String, Set<String>> viewsOf = new HashMap<>();
        private final List<DocumentNode> elements = new ArrayList<>();
        private final List<DocumentNode> relationships = new ArrayList<>();
        private final Map<String, DocumentNode> byId = new HashMap<>();

        Catalog(ModelDocument document, CatalogCsvOptions options) {
            document.allNodes().forEach(n -> byId.put(n.archiId().value(), n));
            for (DocumentNode folder : document.folders()) {
                walkFolder(folder, folder.attribute("name").orElse(""), false, options.folderArchiId());
            }
            for (DocumentNode view : document.views()) {
                collectPlacements(view, view.archiId().value());
            }
            Set<String> exported = elements.stream().map(e -> e.archiId().value()).collect(Collectors.toSet());
            for (DocumentNode relationship : document.relationships()) {
                String source = relationship.attribute("source").orElse("");
                String target = relationship.attribute("target").orElse("");
                if (options.folderArchiId().isEmpty() || (exported.contains(source) && exported.contains(target))) {
                    relationships.add(relationship);
                }
            }
        }

        private void walkFolder(DocumentNode folder, String path, boolean inside, Optional<String> filter) {
            boolean selected = inside || filter.isEmpty() || filter.get().equals(folder.archiId().value());
            folderPath.put(folder.archiId().value(), path);
            for (DocumentNode child : folder.children()) {
                if (child.tag().equals("folder")) {
                    walkFolder(child, path + " / " + child.attribute("name").orElse(""), selected, filter);
                } else if (selected && isElement(child)) {
                    folderPath.put(child.archiId().value(), path);
                    elements.add(child);
                }
            }
        }

        private void collectPlacements(DocumentNode node, String viewId) {
            for (DocumentNode child : node.children()) {
                child.attribute("archimateElement")
                        .ifPresent(e -> viewsOf.computeIfAbsent(e, k -> new LinkedHashSet<>()).add(viewId));
                collectPlacements(child, viewId);
            }
        }

        String elements(char separator) {
            List<String> keys = propertyKeysByFrequency();
            List<List<String>> rows = new ArrayList<>();
            List<String> header = new ArrayList<>(List.of("id", "type", "name", "layer", "folder", "documentation",
                    "views", "placed"));
            header.addAll(keys);
            rows.add(header);
            for (DocumentNode element : elements) {
                String id = element.archiId().value();
                String xsiType = element.archiType().orElse("");
                int views = viewsOf.getOrDefault(id, Set.of()).size();
                List<String> row = new ArrayList<>(List.of(id, typeName(xsiType), element.attribute("name").orElse(""),
                        layerName(xsiType), folderPath.getOrDefault(id, ""), element.documentation().orElse(""),
                        String.valueOf(views), views > 0 ? yes : no));
                Map<String, String> properties = properties(element);
                keys.forEach(k -> row.add(properties.getOrDefault(k, "")));
                rows.add(row);
            }
            return render(rows, separator);
        }

        String relations(char separator) {
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("id", "type", "source_id", "source_name", "target_id", "target_name"));
            for (DocumentNode relationship : relationships) {
                String source = relationship.attribute("source").orElse("");
                String target = relationship.attribute("target").orElse("");
                rows.add(List.of(relationship.archiId().value(),
                        typeName(relationship.archiType().orElse("")).replaceFirst(" Relationship$", ""),
                        source, name(source), target, name(target)));
            }
            return render(rows, separator);
        }

        /** Ключи по убыванию частоты; при равной — по алфавиту, чтобы файл не зависел от обхода. */
        private List<String> propertyKeysByFrequency() {
            Map<String, Integer> frequency = new LinkedHashMap<>();
            elements.forEach(e -> properties(e).keySet().forEach(k -> frequency.merge(k, 1, Integer::sum)));
            return frequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey).toList();
        }

        private String name(String archiId) {
            return Optional.ofNullable(byId.get(archiId)).flatMap(n -> n.attribute("name")).orElse("");
        }
    }

    /** Значения одного ключа у элемента — через запятую, в порядке файла. */
    private static Map<String, String> properties(DocumentNode element) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (DocumentValue property : element.values("property")) {
            String key = property.attribute("key").orElse("");
            if (key.isEmpty()) {
                continue;
            }
            String value = property.attribute("value").orElse("");
            properties.merge(key, value, (a, b) -> a + ", " + b);
        }
        return properties;
    }

    private static boolean isElement(DocumentNode node) {
        return node.tag().equals("element") && node.archiType()
                .filter(t -> !t.endsWith("Relationship") && !t.endsWith("Model")).isPresent();
    }

    /** {@code archimate:ApplicationComponent} → {@code Application Component}. */
    static String typeName(String xsiType) {
        String simple = xsiType.contains(":") ? xsiType.substring(xsiType.indexOf(':') + 1) : xsiType;
        return simple.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
    }

    private static String layerName(String xsiType) {
        Layer layer = REGISTRY.findByXsiType(xsiType).map(c -> c.layer()).orElse(Layer.OTHER);
        return switch (layer) {
            case BUSINESS -> "Business";
            case APPLICATION -> "Application";
            case TECHNOLOGY -> "Technology";
            case PHYSICAL -> "Physical";
            case MOTIVATION -> "Motivation";
            case STRATEGY -> "Strategy";
            case IMPLEMENTATION -> "Implementation & Migration";
            case OTHER -> "Other";
        };
    }

    private static String render(List<List<String>> rows, char separator) {
        return rows.stream().map(row -> row.stream().map(v -> quote(v, separator))
                        .collect(Collectors.joining(String.valueOf(separator))))
                .collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    /** RFC 4180: в кавычки — поле с разделителем, кавычкой, переводом строки или краевым пробелом. */
    static String quote(String value, char separator) {
        boolean needs = value.indexOf(separator) >= 0 || value.contains("\"") || value.contains("\n")
                || value.contains("\r") || (!value.isEmpty() && (Character.isWhitespace(value.charAt(0))
                || Character.isWhitespace(value.charAt(value.length() - 1))));
        return needs ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
