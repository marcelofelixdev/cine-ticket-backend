package com.app.cineticket.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta a grade de um dia para uma sala, sem sobreposição: cada sessão começa depois
 * do fim da anterior + 30 min de limpeza (a mesma regra do SessionService),
 * arredondado para múltiplos de 15 min. Função pura, sem banco.
 */
public final class DemoSchedulePlanner {

    static final LocalTime FIRST_START = LocalTime.of(13, 0);
    static final LocalTime LAST_START = LocalTime.of(22, 30);
    static final int CLEANING_MINUTES = 30;
    private static final int ROUND_MINUTES = 15;

    private DemoSchedulePlanner() {
    }

    /**
     * @param movieIndex índice do filme na lista de durações recebida
     */
    public record Slot(LocalDateTime start, int movieIndex) {
    }

    /**
     * @param day        dia da grade
     * @param durations  duração (min) de cada filme disponível
     * @param roomIndex  posição da sala, para variar os filmes entre salas
     * @param notBefore  horários que já passaram ficam de fora (a grade não muda por isso)
     */
    public static List<Slot> planDay(LocalDate day, List<Integer> durations, int roomIndex, LocalDateTime notBefore) {
        List<Slot> slots = new ArrayList<>();
        if (durations.isEmpty()) {
            return slots;
        }
        LocalDateTime start = day.atTime(FIRST_START);
        // Deslocamento entre salas sempre menor que a quantidade de filmes, para duas
        // salas nunca começarem o dia com o mesmo filme (quando há mais de um filme).
        int roomOffset = roomIndex * (durations.size() / 4 + 1);
        int sequence = 0;
        while (start.toLocalDate().equals(day) && !start.toLocalTime().isAfter(LAST_START)) {
            int movieIndex = Math.floorMod(day.getDayOfYear() * 3 + roomOffset + sequence, durations.size());
            if (start.isAfter(notBefore)) {
                slots.add(new Slot(start, movieIndex));
            }
            start = roundUp(start.plusMinutes((long) durations.get(movieIndex) + CLEANING_MINUTES));
            sequence++;
        }
        return slots;
    }

    private static LocalDateTime roundUp(LocalDateTime time) {
        int remainder = time.getMinute() % ROUND_MINUTES;
        LocalDateTime rounded = remainder == 0 ? time : time.plusMinutes((long) ROUND_MINUTES - remainder);
        return rounded.withSecond(0).withNano(0);
    }
}
