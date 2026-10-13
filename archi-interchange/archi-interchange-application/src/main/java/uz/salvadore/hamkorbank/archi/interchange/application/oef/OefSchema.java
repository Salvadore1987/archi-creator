package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Официальная схема ArchiMate Model Exchange File Format 3.1 и проверка документа по ней.
 *
 * <p>Схемы лежат в ресурсах рядом с классом: корень — {@code archimate3_Diagram.xsd},
 * он включает {@code View} и {@code Model}, а {@code xml.xsd} W3C, на который ссылается
 * {@code Model} по адресу в сети, подставляется отсюда же. За схемой сервер в сеть
 * не ходит: адрес, которого нет среди ресурсов, — ошибка загрузки, а не запрос.
 *
 * <p>Скомпилированная схема неизменяема и потокобезопасна — её достаточно одной
 * на приложение; проверка заводит свой {@link Validator} на каждый вызов.
 */
public final class OefSchema {

    public static final String NAMESPACE = "http://www.opengroup.org/xsd/archimate/3.0/";
    /** Адрес схемы в {@code xsi:schemaLocation} — так её объявляет Archi. */
    public static final String LOCATION = "http://www.opengroup.org/xsd/archimate/3.1/archimate3_Diagram.xsd";

    private static final String ROOT = "archimate3_Diagram.xsd";
    private static final Set<String> FILES = Set.of(ROOT, "archimate3_View.xsd", "archimate3_Model.xsd", "xml.xsd");

    private final Schema schema;

    private OefSchema(Schema schema) {
        this.schema = schema;
    }

    /** Схема значением — для проверки того, как сценарий обходится с невалидным результатом. */
    public static OefSchema of(Schema schema) {
        return new OefSchema(schema);
    }

    public static OefSchema load() {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setResourceResolver(new LocalResolver());
            URL root = Objects.requireNonNull(OefSchema.class.getResource(ROOT), ROOT);
            return new OefSchema(factory.newSchema(new StreamSource(root.openStream(), root.toExternalForm())));
        } catch (SAXException e) {
            throw new IllegalStateException(ROOT, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Первое нарушение схемы с номером строки и столбца; пусто — документ валиден.
     * Текст нарушения — от разборщика JDK, он для журнала сервера, а не для клиента.
     */
    public Optional<String> violation(byte[] xml) {
        Validator validator = schema.newValidator();
        FirstError errors = new FirstError();
        validator.setErrorHandler(errors);
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (SAXParseException e) {
            errors.record(e);
        } catch (SAXException e) {
            return Optional.of(e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return errors.first();
    }

    private static final class FirstError implements ErrorHandler {

        private String first;

        void record(SAXParseException e) {
            if (first == null) {
                first = e.getLineNumber() + ":" + e.getColumnNumber() + " " + e.getMessage();
            }
        }

        Optional<String> first() {
            return Optional.ofNullable(first);
        }

        @Override
        public void warning(SAXParseException e) {
        }

        @Override
        public void error(SAXParseException e) {
            record(e);
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            record(e);
            throw e;
        }
    }

    /** Подставляет схемы из ресурсов по имени файла в адресе; прочих адресов не знает. */
    private static final class LocalResolver implements LSResourceResolver {

        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId,
                                       String baseURI) {
            String file = systemId == null ? "" : systemId.substring(systemId.lastIndexOf('/') + 1);
            if (!FILES.contains(file)) {
                throw new IllegalStateException(systemId);
            }
            URL url = Objects.requireNonNull(OefSchema.class.getResource(file), file);
            return new Input(url.toExternalForm(), publicId, baseURI);
        }
    }

    /** Источник схемы: поток открывается из ресурса по требованию разборщика. */
    private record Input(String systemId, String publicId, String baseURI) implements LSInput {

        @Override
        public InputStream getByteStream() {
            try {
                return new URL(systemId).openStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public String getBaseURI() {
            return baseURI;
        }

        @Override
        public Reader getCharacterStream() {
            return null;
        }

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
        }

        @Override
        public void setByteStream(InputStream byteStream) {
        }

        @Override
        public void setStringData(String stringData) {
        }

        @Override
        public void setSystemId(String systemId) {
        }

        @Override
        public void setPublicId(String publicId) {
        }

        @Override
        public void setBaseURI(String baseURI) {
        }

        @Override
        public void setEncoding(String encoding) {
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
        }
    }
}
