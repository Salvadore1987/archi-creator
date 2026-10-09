package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.FindingDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ImportResult;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ImportSettings;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportRequest;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportService;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.ImportReport;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.ImportService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.Artifact;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportRequest;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.modeling.application.service.WorkspaceService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Импорт и экспорт {@code .archimate}, строгость импорта пространства.
 */
@RestController
@RequestMapping("/api/v1")
public class InterchangeController {

    private final ImportService imports;
    private final ExportService exports;
    private final WorkspaceService workspaces;
    private final TextCatalog text;

    public InterchangeController(ImportService imports, ExportService exports, WorkspaceService workspaces,
                                 TextCatalog text) {
        this.text = text;
        this.imports = imports;
        this.exports = exports;
        this.workspaces = workspaces;
    }

    /**
     * {@code 201} — модель создана; {@code 200} — повтор ключа; {@code 400} — повреждённые
     * данные; {@code 422} — строгий режим и нарушения матрицы. Отказ несёт
     * отчёт в поле {@code findings}.
     */
    @PostMapping(value = "/models/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importFile(EditorIdentity actor, @RequestPart("file") MultipartFile file,
                                        @RequestParam Optional<UUID> workspaceId,
                                        @RequestHeader("Idempotency-Key") Optional<String> key,
                                        HttpServletRequest request) {
        ImportReport report = imports.importFile(actor, workspace(workspaceId),
                ImportRequest.sourceNameOrDefault(Optional.ofNullable(file.getOriginalFilename())),
                bytes(file), key);
        ImportResult body = result(report, text);
        if (report.status() == ImportStatus.APPLIED) {
            return report.replayed() ? ResponseEntity.ok(body)
                    : ResponseEntity.created(URI.create("/api/v1/models/" + body.modelId())).body(body);
        }
        HttpStatus status = report.rejectedAsCorrupt() ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_CONTENT;
        String code = report.rejectedAsCorrupt() ? InterchangeCodes.CORRUPT_DOCUMENT
                : InterchangeCodes.STRICT_IMPORT_REJECTED;
        String detail = text.text(Message.of(report.rejectedAsCorrupt()
                ? InterchangeMessages.REJECTED_CORRUPT : InterchangeMessages.REJECTED_STRICT));
        ProblemDetail problem = InterchangeProblemHandler.problem(status, code, detail,
                Map.of("sessionId", body.sessionId(), "findings", body.findings()), request, text);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /**
     * Выгрузка зафиксированной версии: {@code version} — номер, без него —
     * последняя; {@code folder} и {@code sep} — параметры каталога CSV. Какие форматы
     * доступны и чем обслуживаются, решает сервис выгрузки — адаптер только
     * собирает запрос.
     */
    @GetMapping("/models/{id}/export")
    public ResponseEntity<byte[]> export(EditorIdentity actor, @PathVariable UUID id,
                                         @RequestParam(defaultValue = ExportService.FORMAT_ARCHIMATE) String fmt,
                                         @RequestParam Optional<Long> version,
                                         @RequestParam Optional<String> folder,
                                         @RequestParam Optional<String> sep) {
        return artifact(exports.export(actor, id, new ExportRequest(fmt, version, sep, folder)));
    }

    @GetMapping("/workspaces/{id}/import-settings")
    public ImportSettings importSettings(@PathVariable UUID id) {
        return new ImportSettings(imports.strictImport(id));
    }

    @PutMapping("/workspaces/{id}/import-settings")
    public ImportSettings configure(EditorIdentity actor, @PathVariable UUID id, @RequestBody ImportSettings settings) {
        imports.configureStrictImport(actor, id, settings.strictImport());
        return new ImportSettings(imports.strictImport(id));
    }

    static ResponseEntity<byte[]> artifact(ExportService.Export export) {
        Artifact artifact = export.artifact();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(artifact.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Archi-Version-No", String.valueOf(export.versionNo()))
                .eTag("\"" + artifact.hash().value() + "\"")
                .lastModified(Instant.EPOCH)
                .body(artifact.bytes());
    }

    private UUID workspace(Optional<UUID> requested) {
        return workspaces.resolve(requested.map(WorkspaceId::of)).value();
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ImportResult result(ImportReport report, TextCatalog text) {
        return new ImportResult(report.sessionId().value(), report.status().name(), report.modelId().orElse(null),
                report.versionNo() > 0 ? report.versionNo() : null, report.replayed(),
                report.findings().stream().map(f -> finding(f, text)).toList());
    }

    private static FindingDto finding(ImportFinding f, TextCatalog text) {
        return new FindingDto(f.severity().name(), f.code(), text.text(f.message()),
                f.archiId().map(a -> a.value()).orElse(null),
                f.xmlLine().orElse(null));
    }
}
