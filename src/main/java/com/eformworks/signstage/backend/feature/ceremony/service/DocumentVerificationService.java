package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.feature.ceremony.dto.DocumentVerificationDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyResult;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyResultRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 결과 PDF 위변조 검증(signstage-docs business/ceremony-feature-migration-review.md §2.5).
 * 조직 스코프가 없는 완전히 공개된 API라 {@link CeremonyService}의 조직/멤버 검증 헬퍼를
 * 재사용하지 않는다 — {@link SignerPortalService}와 같은 성격이다.
 *
 * <p>업로드된 파일 바이트 그대로의 SHA-256을 {@link CeremonyResult#getChecksum()}과 대조한다.
 * 이미 정확한 바이트열을 가진 사람만 검증을 통과시킬 수 있어(체크섬은 brute-force로 추측할 수
 * 없음) 공개 API로 열어도 안전하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentVerificationService {

    private final CeremonyResultRepository ceremonyResultRepository;

    @Transactional
    public DocumentVerificationDto.Response.VerificationResult verify(MultipartFile file) {
        String checksum = sha256Hex(readBytes(file));
        Optional<CeremonyResult> found = ceremonyResultRepository.findByChecksum(checksum);
        if (found.isEmpty()) {
            return new DocumentVerificationDto.Response.VerificationResult(false, null, null, null, null, null);
        }

        CeremonyResult result = found.get();
        result.markVerified();
        return new DocumentVerificationDto.Response.VerificationResult(
                true,
                result.getResultType().name(),
                result.getCeremonyEvent().getCeremony().getTitle(),
                result.getCeremonyEvent().getName(),
                result.getCreatedAt(),
                LocalDateTime.now()
        );
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("업로드된 파일을 읽을 수 없습니다.", e);
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}
