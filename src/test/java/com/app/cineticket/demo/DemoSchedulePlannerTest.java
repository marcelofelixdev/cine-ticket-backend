package com.app.cineticket.demo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoSchedulePlannerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);
    private static final List<Integer> DURATIONS = List.of(175, 97, 180, 116, 152);

    @Test
    void sessoesDoDiaNaoSeSobrepoemEIncluemALimpeza() {
        List<DemoSchedulePlanner.Slot> slots = DemoSchedulePlanner.planDay(DAY, DURATIONS, 0, DAY.atStartOfDay());

        assertTrue(slots.size() >= 3, "deve haver várias sessões por dia");
        for (int i = 1; i < slots.size(); i++) {
            DemoSchedulePlanner.Slot previous = slots.get(i - 1);
            LocalDateTime previousEnd = previous.start()
                    .plusMinutes(DURATIONS.get(previous.movieIndex()) + DemoSchedulePlanner.CLEANING_MINUTES);
            assertFalse(slots.get(i).start().isBefore(previousEnd), "sessão começa antes do fim da anterior");
        }
    }

    @Test
    void horariosFicamEntreTrezeHorasEVinteDuasEMeia() {
        for (DemoSchedulePlanner.Slot slot : DemoSchedulePlanner.planDay(DAY, DURATIONS, 1, DAY.atStartOfDay())) {
            LocalTime time = slot.start().toLocalTime();
            assertFalse(time.isBefore(LocalTime.of(13, 0)));
            assertFalse(time.isAfter(LocalTime.of(22, 30)));
            assertEquals(0, time.getMinute() % 15, "horários em múltiplos de 15 min");
        }
    }

    @Test
    void horariosQueJaPassaramFicamDeForaSemMudarAGrade() {
        List<DemoSchedulePlanner.Slot> fullDay = DemoSchedulePlanner.planDay(DAY, DURATIONS, 0, DAY.atStartOfDay());
        LocalDateTime now = fullDay.get(1).start();

        List<DemoSchedulePlanner.Slot> remaining = DemoSchedulePlanner.planDay(DAY, DURATIONS, 0, now);

        assertEquals(fullDay.subList(2, fullDay.size()), remaining);
    }

    @Test
    void salasDiferentesTemProgramacaoDiferente() {
        var room0 = DemoSchedulePlanner.planDay(DAY, DURATIONS, 0, DAY.atStartOfDay());
        var room1 = DemoSchedulePlanner.planDay(DAY, DURATIONS, 1, DAY.atStartOfDay());

        assertFalse(room0.get(0).movieIndex() == room1.get(0).movieIndex());
    }
}
