package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

/** Имена файлов выгрузки: из имени модели или представления, без символов, недопустимых в имени файла. */
public final class ExportFileNames {

    private ExportFileNames() {
    }

    /** Имя без символов, недопустимых в имени файла; пустое — {@code fallback}. */
    public static String safe(String name, String fallback) {
        String base = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        return base.isEmpty() ? fallback : base;
    }
}
