package com.eformworks.signstage.backend;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling}은 조직 구독 만료 배치(signstage-docs
 * business/organization-event-discount-pricing-review.md 8장 결정, 2026-09-10)를 위해 처음
 * 도입했다 — {@code OrganizationSubscriptionExpirationScheduler} 참고.
 */
@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	static {
		// 저장·전송하는 실제 시각은 UTC로 통일하고, 사용자 화면에서 IANA time zone으로 변환한다.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
