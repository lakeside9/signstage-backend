package com.eformworks.signstage.backend.feature.ceremony;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CeremonyEventEffectMigrationContractTest {

    private static final String BILLING_CATALOG_MIGRATION =
            "db/migration/V202608181000__create_billing_catalog.sql";
    private static final String EFFECT_SCHEMA_MIGRATION =
            "db/migration/V202609071000__create_ceremony_event_effect_schema.sql";
    private static final String EFFECT_SEED_MIGRATION =
            "db/migration/V202609071100__seed_and_backfill_ceremony_event_effects.sql";

    @Test
    void optionalFeatureCodeIsAlreadyUniqueInTheBaselineSchema() throws IOException {
        assertThat(read(BILLING_CATALOG_MIGRATION))
                .contains("CONSTRAINT uq_of_code UNIQUE (code)");
    }

    @Test
    void effectSchemaKeepsClassificationAndLifecycleConstraints() throws IOException {
        assertThat(read(EFFECT_SCHEMA_MIGRATION))
                .contains("CREATE TABLE ceremony_effect_definitions")
                .contains("CONSTRAINT uq_ced_code UNIQUE (code)")
                .contains("CONSTRAINT uq_ced_id_target_trigger UNIQUE (id, target_type, trigger_type)")
                .contains("required_optional_feature_id BIGINT NOT NULL")
                .contains("CREATE TABLE ceremony_event_effect_settings")
                .contains("PRIMARY KEY (event_id, target_type, trigger_type)")
                .contains("REFERENCES ceremony_events (id) ON DELETE CASCADE")
                .contains("REFERENCES ceremony_effect_definitions (id, target_type, trigger_type)")
                .contains("CREATE TABLE ceremony_event_signer_states")
                .contains("PRIMARY KEY (event_id, signer_id)")
                .contains("signature_status IN ('PENDING', 'SIGNING', 'COMPLETED')")
                .contains("ADD COLUMN auto_celebration_triggered_at TIMESTAMP NULL");
    }

    @Test
    void seedContainsEveryRendererContractIncludingFlash() throws IOException {
        String migration = read(EFFECT_SEED_MIGRATION);

        assertThat(migration)
                .contains("SELECT 'HIGHLIGHT' AS code, 'PROJECTOR' AS target_type")
                .contains("'SIGNATURE_COMPLETED' AS trigger_type, 'SIGNER_FIELD_ZOOM' AS feature_code")
                .contains("'PULSE', 'PROJECTOR', 'SIGNATURE_COMPLETED', 'SIGNER_FIELD_ZOOM'")
                .contains("'RIPPLE', 'PROJECTOR', 'SIGNATURE_COMPLETED', 'SIGNER_FIELD_ZOOM'")
                .contains("'FLASH', 'PROJECTOR', 'SIGNATURE_COMPLETED', 'SIGNER_FIELD_ZOOM'")
                .contains("'CONFETTI', 'PROJECTOR', 'ALL_SIGNATURES_COMPLETED', 'ALL_SIGNED_FIREWORKS'")
                .contains("'FIREWORKS', 'PROJECTOR', 'ALL_SIGNATURES_COMPLETED', 'ALL_SIGNED_FIREWORKS'")
                .contains("'SPARKLE', 'PROJECTOR', 'ALL_SIGNATURES_COMPLETED', 'ALL_SIGNED_FIREWORKS'")
                .contains("'AIR_SHOT', 'PROJECTOR', 'ALL_SIGNATURES_COMPLETED', 'ALL_SIGNED_FIREWORKS'")
                .contains("'PAPER_REEL', 'PROJECTOR', 'ALL_SIGNATURES_COMPLETED', 'ALL_SIGNED_FIREWORKS'")
                .contains("'projector-signature-highlight' AS renderer_key, 0 AS manually_triggerable")
                .contains("10 AS display_order")
                .contains("'projector-signature-pulse', 0, 20")
                .contains("'projector-signature-ripple', 0, 30")
                .contains("'projector-signature-flash', 0, 40")
                .contains("'projector-confetti', 1, 10")
                .contains("'projector-fireworks', 1, 20")
                .contains("'projector-sparkle', 1, 30")
                .contains("'projector-air-shot', 1, 40")
                .contains("'projector-paper-reel', 1, 50");
    }

    @Test
    void backfillUsesDeterministicLatestStateAndFireworksDefault() throws IOException {
        assertThat(read(EFFECT_SEED_MIGRATION))
                .contains("ORDER BY cel.created_at DESC, cel.id DESC")
                .contains("WHEN 'SIGNATURE_COMPLETE' THEN 'COMPLETED'")
                .contains("WHEN 'SIGNATURE_REPLACE' THEN 'SIGNING'")
                .contains("WHERE status IN ('FINISHED', 'FORCE_FINISHED')")
                .contains("definition.code = 'HIGHLIGHT'")
                .contains("definition.code = 'FIREWORKS'");
    }

    private String read(String path) throws IOException {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
