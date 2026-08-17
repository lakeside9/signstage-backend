package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OPERATOR의 "본인/배정 건" 판정 테이블(signstage-docs
 * business/ceremony-feature-migration-review.md 4.7절). Ceremony 생성 시점에 생성자를
 * (역할과 무관하게) 자동으로 배정하고, 이후 OWNER/ADMIN이 다른 사용자를 추가로 배정할 수 있다.
 * OWNER/ADMIN은 권한 매트릭스상 이미 모든 행사에 접근 가능하므로 이 테이블을 조회할 필요가
 * 없다 — OPERATOR 판정에만 쓰인다. {@code role} 컬럼은 없다 — 배정 여부 플래그일 뿐이고
 * 권한 등급은 계속 {@code organization_members.role}이 갖는다.
 */
@Entity
@Table(
        name = "ceremony_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uq_ca_ceremony_user", columnNames = {"ceremony_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    private CeremonyAssignment(Ceremony ceremony, User user) {
        this.ceremony = ceremony;
        this.user = user;
    }
}
