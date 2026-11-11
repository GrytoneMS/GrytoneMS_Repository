package com.grytonems.backend.domain.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemesterTest {

    @Test
    @DisplayName("3~8월은 1학기, 9~12월은 2학기, 1~2월은 전년도 2학기")
    void semester() {
        assertThat(Semester.of(LocalDate.of(2026, 3, 1))).isEqualTo("2026-1");
        assertThat(Semester.of(LocalDate.of(2026, 8, 31))).isEqualTo("2026-1");
        assertThat(Semester.of(LocalDate.of(2026, 9, 1))).isEqualTo("2026-2");
        assertThat(Semester.of(LocalDate.of(2026, 11, 11))).isEqualTo("2026-2");
        assertThat(Semester.of(LocalDate.of(2027, 2, 28))).isEqualTo("2026-2");
    }
}
