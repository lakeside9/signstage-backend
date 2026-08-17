package com.eformworks.signstage.backend.integration.storage;

import com.eformworks.signstage.backend.feature.ceremony.model.StoredFile;
import com.eformworks.signstage.backend.feature.ceremony.port.DocumentStoragePort;
import com.eformworks.signstage.backend.integration.storage.common.error.StorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 파일시스템 저장 구현. {@code storage.local.base-dir} 아래 {@code directory/{UUID}.pdf}
 * 형태로 저장한다. signstage-docs business/ceremony-feature-migration-review.md §5.2(파일
 * 스토리지 백엔드 선택)는 아직 미결이라, {@link DocumentStoragePort} 뒤에 감춰 나중에 S3
 * 어댑터로 교체할 수 있게 했다.
 */
@Component
public class LocalDiskStorageAdapter implements DocumentStoragePort {

    private final Path baseDir;

    public LocalDiskStorageAdapter(@Value("${storage.local.base-dir}") String baseDir) {
        this.baseDir = Path.of(baseDir);
    }

    @Override
    public StoredFile store(String directory, MultipartFile file) {
        try {
            String extension = resolveExtension(file.getOriginalFilename());
            String storedFilename = UUID.randomUUID() + extension;

            Path targetDir = baseDir.resolve(directory);
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(storedFilename);
            file.transferTo(targetPath);

            String storageKey = directory + "/" + storedFilename;
            return new StoredFile(storageKey, storedFilename);
        } catch (IOException e) {
            throw new StorageException("파일 저장에 실패했습니다.", e);
        }
    }

    @Override
    public StoredFile store(String directory, String filename, byte[] content) {
        try {
            String extension = resolveExtension(filename);
            String storedFilename = UUID.randomUUID() + extension;

            Path targetDir = baseDir.resolve(directory);
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(storedFilename);
            Files.write(targetPath, content);

            String storageKey = directory + "/" + storedFilename;
            return new StoredFile(storageKey, storedFilename);
        } catch (IOException e) {
            throw new StorageException("파일 저장에 실패했습니다.", e);
        }
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        Path path = baseDir.resolve(storageKey);
        if (!Files.exists(path)) {
            throw new StorageException("파일을 찾을 수 없습니다: " + storageKey);
        }
        return new FileSystemResource(path);
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        return dotIndex >= 0 ? originalFilename.substring(dotIndex) : "";
    }
}
