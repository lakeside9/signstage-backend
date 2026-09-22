package com.eformworks.signstage.backend.feature.ceremony.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CeremonyScheduleTest {
    @Test
    void scheduleCanBeEditedAndCleared() {
        Ceremony ceremony = Ceremony.builder().title("행사").build();
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 10, 0);
        ceremony.updateSchedule(" 서울 행사장 ", start, start.plusHours(2));
        assertThat(ceremony.getLocation()).isEqualTo("서울 행사장");
        assertThat(ceremony.getStartsAt()).isEqualTo(start);
        assertThat(ceremony.getEndsAt()).isEqualTo(start.plusHours(2));

        ceremony.updateSchedule(" ", null, null);
        assertThat(ceremony.getLocation()).isNull();
        assertThat(ceremony.getStartsAt()).isNull();
        assertThat(ceremony.getEndsAt()).isNull();
    }

    @Test
    void rejectsEndBeforeStartWithoutChangingSchedule() {
        Ceremony ceremony = Ceremony.builder().title("행사").build();
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 10, 0);
        assertThatThrownBy(() -> ceremony.updateSchedule("서울", start, start.minusMinutes(1)))
                .isInstanceOf(ApplicationException.class);
        assertThat(ceremony.getStartsAt()).isNull();
    }
}
