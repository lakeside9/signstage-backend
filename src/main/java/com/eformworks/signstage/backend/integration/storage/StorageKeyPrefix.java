package com.eformworks.signstage.backend.integration.storage;

import java.time.LocalDateTime;

/**
 * 문서 스토리지 키(경로) 접두어 조립 — signstage-docs
 * business/document-storage-key-convention-review.md 결정(2026-09-10, §4.1 organizationId를
 * 날짜보다 최상위에 두는 안 채택 — 조직 단위 내보내기/삭제/접근 제어가 전역 시점 아카이빙보다
 * 더 자주 필요하다고 판단했다). 형태:
 *
 * <pre>{@code
 * {organizationId}/{yyyy}/{mm}/ceremonies/{ceremonyId}/templates/{templateId}/{uuid}.{ext}
 * {organizationId}/{yyyy}/{mm}/ceremonies/{ceremonyId}/results/{ceremonyEventId}/{uuid}.{ext}
 * }</pre>
 *
 * <p>한 ceremony의 원본 문서(templates)와 결과 문서(results)가 같은 {@code ceremonies/{id}}
 * 서브트리 아래 모인다 — 그 폴더 하나만 압축·이관해도 그 행사 관련 파일 전체가 딸려온다는 게
 * 이 제안의 핵심(§3.1). {@code {yyyy}/{mm}}은 {@code Ceremony.createdAt}(불변) 기준이다 —
 * {@code CeremonyEvent.scheduledStartAt}은 재조정될 수 있고 ceremony 하나가 여러 달에 걸친
 * 이벤트를 가질 수도 있어, 생성일 기준으로 고정해야 한 ceremony의 파일이 항상 같은 연/월
 * 폴더에 모인다는 성질이 유지된다.
 *
 * <p>{@link com.eformworks.signstage.backend.feature.ceremony.port.DocumentStoragePort}
 * 인터페이스 자체는 바뀌지 않는다 — {@code store(directory, ...)} 호출부가 넘기는 directory
 * 문자열 조립만 여기로 모았다(TemplateService/CeremonyResultService가 공유). 기존에 이미
 * 저장된 레코드는 storageKey를 DB에 그대로 들고 있어(§2) 스킴을 바꿔도 계속 정상 동작한다 —
 * 새로 저장되는 파일부터만 이 스킴이 적용된다(§4.2, 이관 스크립트 없음).
 */
public final class StorageKeyPrefix {

    private StorageKeyPrefix() {
    }

    /** 새 문서 양식을 저장할 때 쓴다. templateId는 엔티티를 먼저 저장해 발급받은 값이다. */
    public static String forTemplate(Long organizationId, LocalDateTime ceremonyCreatedAt, Long ceremonyId, Long templateId) {
        return base(organizationId, ceremonyCreatedAt, ceremonyId) + "/templates/" + templateId;
    }

    /** 서명 결과 PDF를 저장할 때 쓴다. ceremonyEventId는 결과 생성 시점에 이미 존재하는 값이다. */
    public static String forResult(Long organizationId, LocalDateTime ceremonyCreatedAt, Long ceremonyId, Long ceremonyEventId) {
        return base(organizationId, ceremonyCreatedAt, ceremonyId) + "/results/" + ceremonyEventId;
    }

    private static String base(Long organizationId, LocalDateTime ceremonyCreatedAt, Long ceremonyId) {
        return organizationId + "/" + yearMonth(ceremonyCreatedAt) + "/ceremonies/" + ceremonyId;
    }

    private static String yearMonth(LocalDateTime createdAt) {
        return String.format("%04d/%02d", createdAt.getYear(), createdAt.getMonthValue());
    }
}
