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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 페이지 내 서명란 좌표. 좌표는 해상도 무관하게 0~1 비율로 저장한다. {@code signer}는
 * nullable이다 — 아직 서명자를 배정하지 않은 필드도 둘 수 있다.
 */
@Entity
@Table(name = "template_fields")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateField extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_id")
    private Signer signer;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "page_index", nullable = false)
    private Integer pageIndex;

    @Column(name = "field_index", nullable = false)
    private Integer fieldIndex;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    /** 조직마다 자유롭게 붙이는 라벨이라 enum이 아닌 문자열로 유지한다(4.4절). */
    @Column(name = "role_code", length = 50)
    private String roleCode;

    @Column(name = "sign_order")
    private Integer signOrder;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    @Column(name = "x_ratio", nullable = false, precision = 6, scale = 5)
    private BigDecimal xRatio;

    @Column(name = "y_ratio", nullable = false, precision = 6, scale = 5)
    private BigDecimal yRatio;

    @Column(name = "width_ratio", nullable = false, precision = 6, scale = 5)
    private BigDecimal widthRatio;

    @Column(name = "height_ratio", nullable = false, precision = 6, scale = 5)
    private BigDecimal heightRatio;

    @Builder
    private TemplateField(
            Template template,
            Signer signer,
            String fieldKey,
            Integer pageIndex,
            Integer fieldIndex,
            String fieldName,
            String roleCode,
            Integer signOrder,
            Boolean isRequired,
            BigDecimal xRatio,
            BigDecimal yRatio,
            BigDecimal widthRatio,
            BigDecimal heightRatio
    ) {
        this.template = template;
        this.signer = signer;
        this.fieldKey = fieldKey;
        this.pageIndex = pageIndex;
        this.fieldIndex = fieldIndex;
        this.fieldName = fieldName;
        this.roleCode = roleCode;
        this.signOrder = signOrder;
        this.isRequired = isRequired != null ? isRequired : Boolean.TRUE;
        this.xRatio = xRatio;
        this.yRatio = yRatio;
        this.widthRatio = widthRatio;
        this.heightRatio = heightRatio;
    }
}
