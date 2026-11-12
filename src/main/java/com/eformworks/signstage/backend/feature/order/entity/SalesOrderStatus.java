package com.eformworks.signstage.backend.feature.order.entity;

/** 수주 상태. 견적(QUOTED)에서 고객이 수락하면 ACCEPTED(수주 확정)다. */
public enum SalesOrderStatus {
    QUOTED,
    ACCEPTED,
    CANCELLED
}
