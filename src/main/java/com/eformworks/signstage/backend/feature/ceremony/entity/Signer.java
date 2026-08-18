package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서명자. 계정 체계와 무관하다(로그인 없음, {@code accessKey} 소지만으로 포털에 접속한다 —
 * 실제 포털 인가는 다음 라운드에 만든다). {@code Ceremony} 직속이라 같은 행사의 TEST/MAIN
 * 하위 행사가 명단을 공유한다(signstage-docs business/ceremony-feature-migration-review.md
 * 4.3절 결정).
 */
@Entity
@Table(name = "signers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Signer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String position;

    /** 서명자가 소속된 곳의 자유 문자열 라벨 — 실제 {@link com.eformworks.signstage.backend.feature.organization.entity.Organization}과는 무관하다. */
    @Column(length = 100)
    private String affiliation;

    /** 조직마다 자유롭게 붙이는 라벨(예: "대표", "이사")이라 enum이 아닌 문자열로 유지한다(4.4절). */
    @Column(name = "role_code", length = 50)
    private String roleCode;

    @Column(name = "access_key", nullable = false, unique = true, length = 64)
    private String accessKey;

    @Builder
    private Signer(Ceremony ceremony, String name, String position, String affiliation, String roleCode, String accessKey) {
        this.ceremony = ceremony;
        this.name = name;
        this.position = position;
        this.affiliation = affiliation;
        this.roleCode = roleCode;
        this.accessKey = accessKey;
    }

    /** 서명자 수정 화면에서 기본 정보를 바꿀 때 쓴다. accessKey는 여기서 바꾸지 않는다. */
    public void updateInfo(String name, String position, String affiliation, String roleCode) {
        this.name = name;
        this.position = position;
        this.affiliation = affiliation;
        this.roleCode = roleCode;
    }
}
