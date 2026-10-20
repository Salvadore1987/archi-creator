package uz.salvadore.hamkorbank.archi.modeling.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    public record Header(String archiId, String name, String documentation, String archiVersion,
                         List<Property> properties, String rawXml) {

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
    public record Folder(UUID id, UUID parentId, String archiId, String name, String type, long sortOrder,
                         String rawXml) {
    }

    /** Элемент; поддержан ли его тип, решает modeling по каталогу. */
    public record Element(UUID id, UUID folderId, String archiId, String archiType, String name,
                          String documentation, List<Property> properties, long sortOrder, String rawXml) {

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
    public record Relationship(UUID id, UUID folderId, String archiId, String archiType, UUID sourceId,
                               UUID targetId, String name, String documentation, String accessType,
                               Boolean directed, List<Property> properties, long sortOrder, String rawXml) {

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
                       String documentation, String viewpoint, List<Property> properties, long sortOrder,
                       String rawXml, List<Node> nodes, List<Edge> edges) {

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
    public record Node(UUID id, UUID parentId, String archiId, String archiType, UUID elementId, Box bounds,
                       StyleValue style, String label, String content, long sortOrder, String rawXml) {
    }

    /**
     * Ребро представления; конец — ключ узла или другого ребра.
     *
     * @param relationshipId связь модели; {@code null} — соединение без связи
     */
    public record Edge(UUID id, String archiId, String archiType, UUID relationshipId, UUID sourceId,
                       UUID targetId, List<BendpointValue> bendpoints, StyleValue style, long sortOrder,
                       String rawXml) {

        public Edge {
            bendpoints = List.copyOf(bendpoints);
        }
    }

    /** Прямоугольник узла. */
    public record Box(int x, int y, int width, int height) {
    }
}
