package uz.salvadore.hamkorbank.archi.interchange.domain.common;

/**
 * Ключи сообщений interchange. Текст — в {@code i18n/messages*.properties}, по языку запроса;
 * в коде — только ключ и аргументы.
 */
public final class InterchangeMessages {

    public static final String OPERATION_DENIED = "interchange.access.operation-denied";
    public static final String CSV_NO = "interchange.csv.no";
    public static final String CSV_SEPARATOR = "interchange.csv.separator";
    public static final String CSV_SEPARATOR_LENGTH = "interchange.csv.separator-length";
    public static final String CSV_YES = "interchange.csv.yes";
    public static final String ARCHI_ID_INVALID = "interchange.document.archi-id-invalid";
    public static final String ATTRIBUTE_NAME_EMPTY = "interchange.document.attribute-name-empty";
    public static final String ATTRIBUTE_PAIRS_EXPECTED = "interchange.document.attribute-pairs-expected";
    public static final String ATTRIBUTE_REPEATED = "interchange.document.attribute-repeated";
    public static final String CORRUPT_SUMMARY = "interchange.document.corrupt";
    public static final String CORRUPT_AT_LINE = "interchange.document.corrupt-at-line";
    public static final String CORRUPT_MORE = "interchange.document.corrupt-more";
    public static final String DANGLING_REFERENCE = "interchange.document.dangling-reference";
    public static final String FRAGMENT_MISADDRESSED = "interchange.document.fragment-misaddressed";
    public static final String FRAGMENT_NOT_ELEMENT = "interchange.document.fragment-not-element";
    public static final String FRAGMENT_ROOT_MISADDRESSED = "interchange.document.fragment-root-misaddressed";
    public static final String HASH_NOT_SHA256 = "interchange.document.hash-not-sha256";
    public static final String ID_INVALID = "interchange.document.id-invalid";
    public static final String ID_REPEATED = "interchange.document.id-repeated";
    public static final String MODEL_ID_INVALID = "interchange.document.model-id-invalid";
    public static final String MODEL_WITHOUT_ID = "interchange.document.model-without-id";
    public static final String NODE_ID_MISSING = "interchange.document.node-id-missing";
    public static final String NODE_WITHOUT_ID = "interchange.document.node-without-id";
    public static final String NODE_WITHOUT_TYPE = "interchange.document.node-without-type";
    public static final String NOT_ARCHIMATE_ROOT = "interchange.document.not-archimate-root";
    public static final String NOT_DOCUMENT_NODE = "interchange.document.not-node";
    public static final String NOT_DOCUMENT_VALUE = "interchange.document.not-value";
    public static final String ORDER_MISMATCH = "interchange.document.order-mismatch";
    public static final String ORDER_NEGATIVE = "interchange.document.order-negative";
    public static final String RELATIONSHIP_WITHOUT_END = "interchange.document.relationship-without-end";
    public static final String TARGET_CONNECTIONS_DIFFER = "interchange.document.target-connections-differ";
    public static final String TARGET_CONNECTIONS_MISSING = "interchange.document.target-connections-missing";
    public static final String TEXT_OUTSIDE_VALUE = "interchange.document.text-outside-value";
    public static final String ARTIFACT_HASH_MISMATCH = "interchange.export.artifact-hash-mismatch";
    public static final String FORMAT_NOT_AVAILABLE = "interchange.export.format-not-available";
    public static final String EXPORT_LOSS_REPORT_MISSING = "interchange.export.loss-report-missing";
    public static final String LOSS_UNKNOWN_KIND = "interchange.export.loss-unknown-kind";
    public static final String LOSS_WITHOUT_REASON = "interchange.export.loss-without-reason";
    public static final String EXPORT_LOSSLESS_WITH_LOSSES = "interchange.export.lossless-with-losses";
    public static final String EXPORT_SCALE_POSITIVE = "interchange.export.scale-positive";
    public static final String EXPORT_TRANSITION = "interchange.export.transition";
    public static final String UNKNOWN_FORMAT = "interchange.export.unknown-format";
    public static final String EXPORT_VERSION_MISMATCH = "interchange.export.version-mismatch";
    public static final String EXPORT_VERSION_POSITIVE = "interchange.export.version-positive";
    public static final String EXPORT_WITHOUT_ARTIFACT = "interchange.export.without-artifact";
    public static final String FINDING_WITHOUT_CODE = "interchange.import.finding-without-code";
    public static final String IDEMPOTENCY_KEY_INVALID = "interchange.import.idempotency-key-invalid";
    public static final String IDEMPOTENCY_KEY_REUSED = "interchange.import.idempotency-key-reused";
    public static final String IMPORT_IN_PROGRESS = "interchange.import.in-progress";
    public static final String IMPORT_KEY_REUSED = "interchange.import.key-reused";
    public static final String REJECTED_CORRUPT = "interchange.import.rejected-corrupt";
    public static final String REJECTED_STRICT = "interchange.import.rejected-strict";
    public static final String RELATION_NOT_PERMITTED = "interchange.import.relation-not-permitted";
    public static final String SOURCE_EMPTY = "interchange.import.source-empty";
    public static final String SOURCE_NAME_INVALID = "interchange.import.source-name-invalid";
    public static final String IMPORT_TRANSITION = "interchange.import.transition";
    public static final String UNKNOWN_TYPE_STORED = "interchange.import.unknown-type";
    public static final String CORRUPT_WITHOUT_DEFECTS = "interchange.internal.corrupt-without-defects";
    public static final String DIGEST_UNAVAILABLE = "interchange.internal.digest-unavailable";
    public static final String EDGE_OUTSIDE = "interchange.mapping.edge-outside";
    public static final String RELATIONSHIP_OUTSIDE = "interchange.mapping.relationship-outside";
    public static final String ROOT_FOLDER_UNTYPED = "interchange.mapping.root-folder-untyped";
    public static final String UNEXPECTED_NODE = "interchange.mapping.unexpected-node";
    public static final String WHERE_FOLDER = "interchange.mapping.where.folder";
    public static final String WHERE_INSIDE = "interchange.mapping.where.inside";
    public static final String WHERE_MODEL_ROOT = "interchange.mapping.where.model-root";
    public static final String WHERE_VIEW = "interchange.mapping.where.view";
    public static final String WHERE_VIEW_EDGE = "interchange.mapping.where.view-edge";
    public static final String WHERE_VIEW_NODE = "interchange.mapping.where.view-node";
    public static final String WHERE_WITHOUT = "interchange.mapping.where.without";
    public static final String TITLE_BAD_FILE = "interchange.problem.bad-file";
    public static final String TITLE_INTERCHANGE = "interchange.problem.interchange";
    public static final String INVALID_REQUEST = "interchange.request.invalid";
    public static final String RESIDUE_CORRUPTED = "interchange.residue.corrupted";
    public static final String RESIDUE_RECORD_EXPECTED = "interchange.residue.record-expected";
    public static final String RESIDUE_UNKNOWN_RECORD = "interchange.residue.unknown-record";
    public static final String UNTYPED_VALUE_LITERAL = "interchange.residue.untyped-value-literal";
    public static final String RESIDUE_UNWRITABLE = "interchange.residue.unwritable";
    public static final String STORED_TEXT = "interchange.stored-text";
    public static final String COMMENT_IMPORTED = "interchange.version.comment.imported";
    public static final String WORKSPACE_NOT_FOUND = "interchange.workspace.not-found";
    public static final String NAMESPACE_MISSING = "interchange.writer.namespace-missing";
    public static final String XML_UNREADABLE = "interchange.xml.unreadable";
    public static final String XML_UNREADABLE_NO_DETAIL = "interchange.xml.unreadable-no-detail";

    private InterchangeMessages() {
    }
}
