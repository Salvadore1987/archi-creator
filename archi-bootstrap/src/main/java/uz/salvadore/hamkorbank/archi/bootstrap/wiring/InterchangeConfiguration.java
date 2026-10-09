package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportService;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.ImportService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelImportService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionService;

/** Сценарии interchange — бинами. Транзакция — общая с modeling. */
@Configuration
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
                                TextCatalog texts, Clock clock) {
        return new ImportService(sessions, policy, models, new StaxArchiDocumentReader(), unitOfWork, events, metrics,
                texts, clock);
    }

    @Bean
    ExportService exportService(VersionService versions, InterchangeEvents events, InterchangeMetrics metrics,
                                TextCatalog texts, Clock clock) {
        return new ExportService(versions, events, metrics, texts, clock);
    }
}
