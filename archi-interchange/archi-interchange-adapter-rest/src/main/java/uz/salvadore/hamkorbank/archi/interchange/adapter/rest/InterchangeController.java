package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ImportResult;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ImportSettings;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureImportPolicyCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ImportArchimateFileCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetImportPolicyQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportRequest;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportService;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ImageRequest;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ViewImageService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ImportApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ImportPolicyApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.Artifact;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Импорт и экспорт {@code .archimate}, строгость импорта пространства.
 */
@RestController
@RequestMapping("/api/v1")
public class InterchangeController {

    /** Число записей отчёта о потерях выгрузки; у {@code .archimate} — ноль. */
    static final String LOSS_COUNT = "X-Archi-Loss-Count";

    private final ImportApplicationService imports;
    private final ImportPolicyApplicationService policies;
    private final ExportService exports;
    private final ViewImageService images;
    private final TextCatalog text;

    public InterchangeController(ImportApplicationService imports, ImportPolicyApplicationService policies,
                                 ExportService exports, ViewImageService images, TextCatalog text) {
        this.text = text;
        this.images = images;
        this.imports = imports;
        this.policies = policies;
        this.exports = exports;
    }

    /**
     * {@code 201} — модель создана; {@code 200} — повтор ключа; {@code 400} — повреждённые
     * данные; {@code 422} — строгий режим и нарушения матрицы. Отказ несёт
     * отчёт в поле {@code findings}.
     */
    @PostMapping(value = "/models/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importFile(EditorIdentity actor, @RequestPart("file") MultipartFile file,
                                        @RequestParam(required = false) UUID workspaceId,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                        HttpServletRequest request) {
        ImportArchimateFileResponse result = imports.importArchimateFile(actor,
                new ImportArchimateFileCommand(workspaceId, file.getOriginalFilename(), bytes(file), key));
        ImportResult body = new ImportResult(result.sessionId(), result.status(), result.modelId(),
                result.versionNo(), result.replayed(), FindingMapper.findings(result.findings()));
        return switch (result.outcome()) {
            case APPLIED -> ResponseEntity.created(URI.create("/api/v1/models/" + body.modelId())).body(body);
            case REPLAYED -> ResponseEntity.ok(body);
            case REJECTED_CORRUPT -> rejected(HttpStatus.BAD_REQUEST, result, body, request);
            case REJECTED_STRICT -> rejected(HttpStatus.UNPROCESSABLE_CONTENT, result, body, request);
        };
    }

    /**
     * Выгрузка зафиксированной версии: {@code version} — номер, без него —
     * последняя; {@code folder} и {@code sep} — параметры каталога CSV. Какие форматы
     * доступны и чем обслуживаются, решает сервис выгрузки — адаптер только
     * собирает запрос. Число записей отчёта о потерях — в {@code X-Archi-Loss-Count}.
     */
    @GetMapping("/models/{id}/export")
    public ResponseEntity<byte[]> export(EditorIdentity actor, @PathVariable UUID id,
                                         @RequestParam(defaultValue = ExportService.FORMAT_ARCHIMATE) String fmt,
                                         @RequestParam(required = false) Long version,
                                         @RequestParam(required = false) String folder,
                                         @RequestParam(required = false) String sep) {
        ExportService.Export export = exports.export(actor, id, new ExportRequest(fmt, version, sep, folder));
        return artifact(export.artifact(), export.versionNo())
                .header(LOSS_COUNT, String.valueOf(export.lossCount()))
                .body(export.artifact().bytes());
    }

    /**
     * Картинка представления из зафиксированной версии: {@code fmt} — {@code svg} или
     * {@code png}, {@code scale}, {@code outline}, {@code background}, {@code version}.
     * Допустимые значения и умолчания решает сервис картинок; число записей отчёта
     * о потерях — в {@code X-Archi-Loss-Count}.
     */
    @GetMapping("/views/{id}/export")
    public ResponseEntity<byte[]> exportView(EditorIdentity actor, @PathVariable UUID id,
                                             @RequestParam(required = false) String fmt,
                                             @RequestParam(required = false) Integer scale,
                                             @RequestParam(required = false) Boolean outline,
                                             @RequestParam(required = false) String background,
                                             @RequestParam(required = false) Long version) {
        return image(images.viewImage(actor, id, new ImageRequest(fmt, scale, outline, background, version)));
    }

    /** Все представления модели одним архивом из одной версии. */
    @GetMapping("/models/{id}/views/export")
    public ResponseEntity<byte[]> exportAllViews(EditorIdentity actor, @PathVariable UUID id,
                                                 @RequestParam(required = false) String fmt,
                                                 @RequestParam(required = false) Integer scale,
                                                 @RequestParam(required = false) Boolean outline,
                                                 @RequestParam(required = false) String background,
                                                 @RequestParam(required = false) Long version) {
        return image(images.allViewImages(actor, id, new ImageRequest(fmt, scale, outline, background, version)));
    }

    /** Настройка импорта рабочего пространства: включён ли строгий режим. */
    @GetMapping("/workspaces/{id}/import-settings")
    public ImportSettings importSettings(EditorIdentity actor, @PathVariable UUID id) {
        return new ImportSettings(policies.getImportPolicy(actor, new GetImportPolicyQuery(id)).strictImport());
    }

    /** Включает или выключает строгий режим импорта пространства и отдаёт итоговую настройку. */
    @PutMapping("/workspaces/{id}/import-settings")
    public ImportSettings configure(EditorIdentity actor, @PathVariable UUID id, @RequestBody ImportSettings settings) {
        return new ImportSettings(policies.configureImportPolicy(actor,
                new ConfigureImportPolicyCommand(id, settings.strictImport())).strictImport());
    }

    private static ResponseEntity<byte[]> image(ViewImageService.ImageExport export) {
        return artifact(export.artifact(), export.versionNo())
                .header(LOSS_COUNT, String.valueOf(export.lossCount()))
                .body(export.artifact().bytes());
    }

    private static ResponseEntity.BodyBuilder artifact(Artifact artifact, long versionNo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(artifact.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Archi-Version-No", String.valueOf(versionNo))
                .eTag("\"" + artifact.hash().value() + "\"")
                .lastModified(Instant.EPOCH);
    }

    private ResponseEntity<ProblemDetail> rejected(HttpStatus status, ImportArchimateFileResponse result,
                                                   ImportResult body, HttpServletRequest request) {
        ProblemDetail problem = InterchangeProblemHandler.problem(status, result.refusalCode(), result.refusalText(),
                Map.of("sessionId", body.sessionId(), "findings", body.findings()), request, text);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
