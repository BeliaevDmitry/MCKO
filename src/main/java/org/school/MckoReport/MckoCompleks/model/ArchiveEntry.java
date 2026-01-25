package org.school.MckoReport.MckoCompleks.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;

@Data
@AllArgsConstructor
public class ArchiveEntry {
    private final Path archivePath;      // путь к самому ZIP архиву
    private final String entryPath;      // путь к файлу внутри архива
    private final long size;             // размер файла в архиве

    @Override
    public String toString() {
        return "ArchiveEntry{archive=" + archivePath + ", file=" + entryPath + ", size=" + size + "}";
    }
}
