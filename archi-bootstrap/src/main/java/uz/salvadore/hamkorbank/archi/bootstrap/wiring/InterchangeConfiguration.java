package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.salvadore.hamkorbank.archi.bootstrap.export.BatikRasterizer;
import uz.salvadore.hamkorbank.archi.bootstrap.export.InterTypesetter;
import uz.salvadore.hamkorbank.archi.bootstrap.export.SharedNotation;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.RasterGate;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.AllViewImagesExportQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ArchimateFileImportCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ImportPolicyConfigureCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ImportPolicyGetQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.InterchangeKernel;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelExportQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ViewImageExportQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefSchema;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Rasterizer;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ExportApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ImportApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ImportPolicyApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ViewImageApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.application.service.ExportApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.interchange.application.service.ImportApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.interchange.application.service.ImportPolicyApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.interchange.application.service.ViewImageApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;

/** Сценарии interchange — бинами. Транзакция — общая с modeling. */
@Configuration(proxyBeanMethods = false)
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
    InterchangeKernel interchangeKernel(UnitOfWork unitOfWork, InterchangeEvents events, InterchangeMetrics metrics,
                                        TextCatalog texts, Clock clock) {
        return new InterchangeKernel(unitOfWork, events, metrics, texts, clock);
    }

    @Bean
    ImportApplicationService importApplicationService(InterchangeKernel kernel, ImportSessionRepository sessions,
                                                      ImportPolicy policy, ModelExchangeApplicationService models,
                                                      WorkspaceApplicationService workspaces,
                                                      InterchangeSettings settings) {
        return new ImportApplicationServiceImpl(new ArchimateFileImportCommandHandler(kernel, sessions, policy, models,
                workspaces, new StaxArchiDocumentReader(), settings.maxParallelImportsPerWorkspace()));
    }

    @Bean
    ImportPolicyApplicationService importPolicyApplicationService(InterchangeKernel kernel, ImportPolicy policy) {
        return new ImportPolicyApplicationServiceImpl(new ImportPolicyGetQueryHandler(kernel, policy),
                new ImportPolicyConfigureCommandHandler(kernel, policy));
    }

    @Bean
    ExportApplicationService exportApplicationService(InterchangeKernel kernel,
                                                      ModelExchangeApplicationService versions, Notation notation,
                                                      OefSchema oefSchema) {
        return new ExportApplicationServiceImpl(new ModelExportQueryHandler(kernel, versions, notation, oefSchema));
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

    /** Очередь растеризации PNG — одна на процесс: её делят картинка представления и пакет картинок. */
    @Bean
    RasterGate rasterGate(InterchangeSettings settings) {
        return new RasterGate(settings.pngMaxAreaPx(), settings.maxParallelRasterizations(),
                settings.rasterizationWait());
    }

    @Bean
    ViewImageApplicationService viewImageApplicationService(InterchangeKernel kernel,
                                                            ViewQueryApplicationService queries,
                                                            ModelExchangeApplicationService versions,
                                                            Notation notation, Typesetter typesetter,
                                                            Rasterizer rasterizer, RasterGate gate) {
        return new ViewImageApplicationServiceImpl(
                new ViewImageExportQueryHandler(kernel, queries, versions, notation, typesetter, rasterizer, gate),
                new AllViewImagesExportQueryHandler(kernel, versions, notation, typesetter, rasterizer, gate));
    }
}
