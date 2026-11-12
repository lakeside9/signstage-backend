package com.eformworks.signstage.backend.feature.order.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파트너의 범위 변경 요청(D08) — 파트너는 범위를 직접 바꾸지 않고 필요만 알린다. 플랫폼이 범위 변경을
 * 제안하면 그 수주의 열린 요청이 함께 닫히고, 반영하지 않을 때는 메모를 남기고 닫는다.
 */
@Entity
@Table(name = "scope_change_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScopeChangeRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_order_id", nullable = false)
    private PartnerOrder partnerOrder;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closing_note", length = 500)
    private String closingNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_change_id")
    private SalesOrderChange salesOrderChange;

    @Builder
    private ScopeChangeRequest(PartnerOrder partnerOrder, String content, Long requestedBy) {
        this.partnerOrder = partnerOrder;
        this.content = content;
        this.requestedBy = requestedBy;
        this.requestedAt = LocalDateTime.now();
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    public void close(Long userId, String note, SalesOrderChange change) {
        this.closedAt = LocalDateTime.now();
        this.closedBy = userId;
        this.closingNote = note;
        this.salesOrderChange = change;
    }
}
