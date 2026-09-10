package com.eformworks.signstage.backend.feature.demo.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 체험형 데모 사이트(legacy {@code ~/Works/eform/source/signstage/demo-signstage-frontend}를
 * 그대로 재사용)가 iframe으로 바라볼 행사/서명자(들)를 가리키는 프로필 — signstage-docs
 * business/demo-account-exhibition-signer-preview-review.md 13장 결정(2026-09-10). legacy
 * {@code feature.demo.entity.DemoConfig}와 같은 구조를 그대로 가져왔다 — legacy 데모 셸이
 * 이미 이 스키마 모양을 전제로 만들어져 있어(공개 API 응답 필드 이름이 이 컬럼들과 1:1),
 * 새로 설계하기보다 검증된 모양을 재사용하는 게 합리적이다.
 *
 * <p>slug로 여러 개를 동시에 운영할 수 있다(부스/도메인마다 서로 다른 행사를 보여줄 때). 서명자는
 * 한 프로필 안에 여러 명 지정할 수 있다(legacy 데모 셸은 그중 최대 2명까지만 나란히 보여준다).
 * {@code eventAccessKey}는 이 프로젝트의 {@code CeremonyEvent}를 FK 없이 가리킨다 — 서명자
 * 포털/프로젝터 화면들이 이미 accessKey 소지 기반 공개 접근 모델을 쓰고 있어(FK 없이 문자열로
 * 참조) 같은 관례를 따른다.
 */
@Entity
@Table(name = "demo_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemoConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(name = "event_access_key", nullable = false, length = 100)
    private String eventAccessKey;

    /** 전시 화면 도구모음의 페이지 구성을 미리 지정하는 값들. 전부 null이면 ProjectorView 기본값을 쓴다. */
    @Column
    private Integer pages;

    @Column(name = "start_page")
    private Integer startPage;

    @Column(length = 10)
    private String spacing;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @ElementCollection
    @CollectionTable(name = "demo_config_signers", joinColumns = @JoinColumn(name = "demo_config_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "signer_access_key", nullable = false, length = 100)
    private List<String> signerAccessKeys = new ArrayList<>();

    @Builder
    private DemoConfig(String slug, boolean enabled) {
        this.slug = slug;
        this.enabled = enabled;
    }

    /**
     * slug로 upsert할 때 신규/기존 행 구분 없이 항상 이 메서드로 나머지 필드를 채운다(legacy
     * {@code DemoConfigService#updateConfig}와 같은 패턴). {@code enabled}는 null이면 기존
     * 값을 유지한다.
     */
    public void update(
            String eventAccessKey,
            List<String> signerAccessKeys,
            Integer pages,
            Integer startPage,
            String spacing,
            Boolean enabled
    ) {
        this.eventAccessKey = eventAccessKey;
        this.signerAccessKeys.clear();
        this.signerAccessKeys.addAll(signerAccessKeys);
        this.pages = pages;
        this.startPage = startPage;
        this.spacing = spacing;
        if (enabled != null) {
            this.enabled = enabled;
        }
    }
}
