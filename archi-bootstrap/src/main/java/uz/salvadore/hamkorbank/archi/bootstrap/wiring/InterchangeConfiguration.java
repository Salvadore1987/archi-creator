package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.salvadore.hamkorbank.archi.bootstrap.export.BatikRasterizer;
import uz.salvadore.hamkorbank.archi.bootstrap.export.InterTypesetter;
import uz.salvadore.hamkorbank.archi.bootstrap.export.SharedNotation;
import uz.salvadore.hamkorbank.archi.bootstrap.git.GitSettings;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportService;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ViewImageService;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitBindingService;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.ImportService;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefSchema;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Rasterizer;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelImportService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionService;

/** Сценарии interchange — бинами. Транзакция — общая с modeling. */
@Configuration
@EnableConfigurationProperties(InterchangeSettings.class)
public class InterchangeConfiguration {

    @Bean
    InterchangeEvents interchangeEvents(AfterCommitEventPublisher publisher) {
        return publisher::publish;
    }

    @Bean
    InterchangeMetrics interchangeMetrics(MeterRegistry registry) {
        return new InterchangeMicrometerMetrics(registry);
    }

    @Bean
    ImportService importService(ImportSessionRepository sessions, ImportPolicy policy, ModelImportService models,
                                UnitOfWork unitOfWork, InterchangeEvents events, InterchangeMetrics metrics,
                                TextCatalog texts, Clock clock, InterchangeSettings settings) {
        return new ImportService(sessions, policy, models, new StaxArchiDocumentReader(), unitOfWork, events, metrics,
                texts, clock, settings.maxParallelImportsPerWorkspace());
    }

    @Bean
    ExportService exportService(VersionService versions, InterchangeEvents events, InterchangeMetrics metrics,
                                TextCatalog texts, Notation notation, OefSchema oefSchema, Clock clock) {
        return new ExportService(versions, events, metrics, texts, notation, oefSchema, clock);
    }

    @Bean
    GitBindingService gitBindingService(GitBindingRepository bindings, UnitOfWork unitOfWork,
                                        InterchangeMetrics metrics, Clock clock, GitSettings settings) {
        return new GitBindingService(bindings, unitOfWork, metrics, clock, settings.allowLocalRepositories());
    }

    /** Схема OEF компилируется один раз при старте: она неизменяема и потокобезопасна. */
    @Bean
    OefSchema oefSchema() {
        return OefSchema.load();
    }

    /** Визуальный язык канвы читается один раз при старте: файлы в classpath не меняются. */
    @Bean
    Notation notation() {
        return SharedNotation.load(InterTypesetter.FAMILY);
    }

    @Bean
    Typesetter typesetter() {
        return new InterTypesetter();
    }

    @Bean
    Rasterizer rasterizer() {
        return new BatikRasterizer();
    }

    @Bean
    ViewImageService viewImageService(ViewQueryApplicationService queries, VersionService versions, Notation notation,
                                      Typesetter typesetter, Rasterizer rasterizer, InterchangeEvents events,
                                      InterchangeMetrics metrics, TextCatalog texts, Clock clock) {
        return new ViewImageService(queries, versions, notation, typesetter, rasterizer, events, metrics, texts,
                clock);
    }
}
