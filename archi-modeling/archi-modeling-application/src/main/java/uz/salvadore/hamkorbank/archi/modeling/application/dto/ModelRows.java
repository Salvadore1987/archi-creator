package uz.salvadore.hamkorbank.archi.modeling.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Содержимое модели строками из простых значений — то, чем modeling обменивается с раскладкой
 * и сборкой документа: заголовок, папки, элементы, связи, представления с узлами и рёбрами.
 * Ключи строк — внутренние ключи ({@code UUID}), ссылки между строками — по ним же; идентификаторы
 * Archi, типы и стиль — строками и числами; {@code rawXml} — остаток XML строки, modeling его не разбирает.
 * Позиция ({@code sortOrder}) — разреженный номер в родителе.
 *
 * <p>Списки — в порядке строк модели: сборка обходит их по порядку, и одно содержимое даёт один файл.
 */
public record ModelRows(Header header, List<Folder> folders, List<Element> elements,
                        List<Relationship> relationships, List<View> views) {

    public ModelRows {
        Objects.requireNonNull(header, "header");
        folders = List.copyOf(folders);
        elements = List.copyOf(elements);
        relationships = List.copyOf(relationships);
        views = List.copyOf(views);
    }

    /**
     * Заголовок модели.
     *
     * @param documentation описание; {@code null} — нет
     * @param archiVersion  версия Archi из файла как есть; недопустимую modeling заменит своей по умолчанию
     */
    public record Header(String archiId, String name, @Nullable String documentation, @Nullable String archiVersion,
                         List<Property> properties, @Nullable String rawXml) {

        public Header {
            properties = List.copyOf(properties);
        }
    }

    /** Свойство в порядке родителя. */
    public record Property(String key, String value, long sortOrder) {
    }

    /**
     * Папка.
     *
     * @param parentId {@code null} — корневая
     * @param type     тип корневой папки значением из файла; у вложенной — {@code null}
     */
    public record Folder(UUID id, @Nullable UUID parentId, String archiId, String name, @Nullable String type,
                         long sortOrder, @Nullable String rawXml) {
    }

    /** Элемент; поддержан ли его тип, решает modeling по каталогу. */
    public record Element(UUID id, UUID folderId, String archiId, String archiType, String name,
                          @Nullable String documentation, List<Property> properties, long sortOrder,
                          @Nullable String rawXml) {

        public Element {
            properties = List.copyOf(properties);
        }
    }

    /**
     * Связь; конец — ключ элемента или другой связи.
     *
     * @param name       {@code null} — без имени
     * @param accessType вид доступа значением из файла; {@code null} — не задан
     * @param directed   направленность связи влияния; {@code null} — не задана
     */
    public record Relationship(UUID id, UUID folderId, String archiId, String archiType, UUID sourceId, UUID targetId,
                               @Nullable String name, @Nullable String documentation, @Nullable String accessType,
                               @Nullable Boolean directed, List<Property> properties, long sortOrder,
                               @Nullable String rawXml) {

        public Relationship {
            properties = List.copyOf(properties);
        }
    }

    /**
     * Представление с узлами и рёбрами.
     *
     * @param viewpoint {@code null} — без точки зрения
     */
    public record View(UUID id, UUID folderId, String archiId, String archiType, String name,
                       @Nullable String documentation, @Nullable String viewpoint, List<Property> properties,
                       long sortOrder, @Nullable String rawXml, List<Node> nodes, List<Edge> edges) {

        public View {
            properties = List.copyOf(properties);
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }
    }

    /**
     * Узел представления.
     *
     * @param parentId  {@code null} — узел в корне представления
     * @param elementId элемент модели; {@code null} — узел без элемента
     * @param bounds    геометрия относительно родителя; {@code null} — не задана, modeling подставит свою
     * @param label     подпись; {@code null} — нет
     * @param content   текст заметки; {@code null} — нет
     */
    public record Node(UUID id, @Nullable UUID parentId, String archiId, String archiType, @Nullable UUID elementId,
                       @Nullable Box bounds, @Nullable StyleValue style, @Nullable String label,
                       @Nullable String content, long sortOrder, @Nullable String rawXml) {
    }

    /**
     * Ребро представления; конец — ключ узла или другого ребра.
     *
     * @param relationshipId связь модели; {@code null} — соединение без связи
     */
    public record Edge(UUID id, String archiId, String archiType, @Nullable UUID relationshipId, UUID sourceId,
                       UUID targetId, List<BendpointValue> bendpoints, @Nullable StyleValue style, long sortOrder,
                       @Nullable String rawXml) {

        public Edge {
            bendpoints = List.copyOf(bendpoints);
        }
    }

    /** Прямоугольник узла. */
    public record Box(int x, int y, int width, int height) {
    }
}
