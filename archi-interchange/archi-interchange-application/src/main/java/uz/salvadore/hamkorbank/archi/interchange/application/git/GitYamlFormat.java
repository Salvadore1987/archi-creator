package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;

/**
 * Общие правила разложенного YAML: имена файлов и ключей, порядок по умолчанию.
 *
 * <p>Узел документа пишется ключами {@code id}, {@code type}, {@code name},
 * {@code source}, {@code target} и отображением {@code attributes} с остальными
 * атрибутами в порядке файла. Порядок атрибутов, который даёт Archi
 * ({@code xmlns…}, {@code xsi:type}, {@code name}, {@code id}, {@code source},
 * {@code target}, прочие), восстанавливается из этого без подсказок; любой другой
 * записывается списком {@code attribute_order}. Так же с содержимым: порядок
 * по умолчанию — {@link #CONTENT_ORDER}, отличие — список {@code content_order}.
 */
final class GitYamlFormat {

    static final String FORMAT_VERSION = "1";
    static final long STEP = 1000;

    static final String MODEL_FILE = "model.yaml";
    static final String FOLDERS_FILE = "folders.yaml";
    static final String ELEMENTS_DIR = "elements/";
    static final String RELATIONS_DIR = "relations/";
    static final String VIEWS_DIR = "views/";
    static final String EXTENSION = ".yaml";

    static final String K_FORMAT_VERSION = "format_version";
    static final String K_ID = "id";
    static final String K_TAG = "tag";
    static final String K_TYPE = "type";
    static final String K_XSI_TYPE = "xsi_type";
    static final String K_NAME = "name";
    static final String K_SOURCE = "source";
    static final String K_TARGET = "target";
    static final String K_FOLDER = "folder";
    static final String K_PARENT = "parent";
    static final String K_POSITION = "position";
    static final String K_ATTRIBUTES = "attributes";
    static final String K_ATTRIBUTE_ORDER = "attribute_order";
    static final String K_CONTENT_ORDER = "content_order";
    static final String K_ITEMS = "items";
    static final String K_FOLDERS = "folders";
    static final String K_KEY = "key";
    static final String K_VALUE = "value";
    static final String K_TEXT = "text";

    static final String A_ID = "id";
    static final String A_NAME = "name";
    static final String A_TYPE = "xsi:type";
    static final String A_SOURCE = "source";
    static final String A_TARGET = "target";
    static final String ARCHIMATE_PREFIX = "archimate:";

    static final String TAG_FOLDER = "folder";
    static final String TAG_ELEMENT = "element";
    static final String TAG_CHILD = "child";
    static final String TAG_CONNECTION = "sourceConnection";
    static final String TAG_DOCUMENTATION = "documentation";
    static final String TAG_PURPOSE = "purpose";
    static final String TAG_PROPERTY = "property";
    static final String TAG_BOUNDS = "bounds";
    static final String TAG_BENDPOINT = "bendpoint";
    static final String TAG_CONTENT = "content";

    /** Виды содержимого узла: ключ YAML и порядок по умолчанию. */
    enum Kind {
        PURPOSE("purpose"),
        DOCUMENTATION("documentation"),
        PROPERTY("properties"),
        BOUNDS("bounds"),
        BENDPOINT("bendpoints"),
        TEXT("text"),
        CONNECTION("connections"),
        CHILD("children"),
        NODE("nodes"),
        VALUE("values"),
        RAW("raw_xml");

        final String key;

        Kind(String key) {
            this.key = key;
        }

        /** Имя в {@code content_order} — единственное число ключа. */
        String token() {
            return switch (this) {
                case PROPERTY -> "property";
                case BENDPOINT -> "bendpoint";
                case CONNECTION -> "connection";
                case CHILD -> "child";
                case NODE -> "node";
                case VALUE -> "value";
                default -> key;
            };
        }

        static Kind ofToken(String token) {
            for (Kind kind : values()) {
                if (kind.token().equals(token)) {
                    return kind;
                }
            }
            return null;
        }

        /** Вид, который бывает у узла не больше одного раза. */
        boolean single() {
            return this == PURPOSE || this == DOCUMENTATION || this == BOUNDS || this == TEXT;
        }
    }

    static final List<Kind> CONTENT_ORDER = List.of(Kind.values());

    private GitYamlFormat() {
    }

    static boolean namespace(String attribute) {
        return attribute.equals("xmlns") || attribute.startsWith("xmlns:");
    }

    /**
     * Выносится ли атрибут в свой ключ. У узлов внутри представления — только
     * {@code id} и {@code xsi:type}: Archi пишет их первыми, а {@code name}
     * и концы связи — где придётся среди прочих, и порядок по умолчанию их бы не угадал.
     */
    static boolean promoted(String attribute, boolean nested) {
        if (attribute.equals(A_ID) || attribute.equals(A_TYPE)) {
            return true;
        }
        return !nested && (attribute.equals(A_NAME) || attribute.equals(A_SOURCE) || attribute.equals(A_TARGET));
    }

    /** Узел внутри представления: ребёнок или исходящее соединение. */
    static boolean nested(String tag) {
        return tag.equals(TAG_CHILD) || tag.equals(TAG_CONNECTION);
    }

    /**
     * Порядок атрибутов по умолчанию: объявления пространств имён, {@code xsi:type},
     * {@code name}, {@code id}, {@code source}, {@code target}, прочие — в порядке
     * отображения {@code attributes}.
     */
    static List<String> canonicalOrder(Map<String, String> promoted, List<String> rest) {
        List<String> order = new ArrayList<>();
        rest.stream().filter(GitYamlFormat::namespace).forEach(order::add);
        for (String name : List.of(A_TYPE, A_NAME, A_ID, A_SOURCE, A_TARGET)) {
            if (promoted.containsKey(name)) {
                order.add(name);
            }
        }
        rest.stream().filter(n -> !namespace(n)).forEach(order::add);
        return order;
    }

    static List<String> names(List<Attribute> attributes) {
        return attributes.stream().map(Attribute::name).toList();
    }
}
