package com.app.cineticket.service;

import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.dto.request.RoomRequestDTO;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exclusão de sala e de cinema contra o banco H2: os assentos têm FK para a sala
 * (e a sala para o cinema), então antes desta correção o DELETE de sala sempre falhava.
 */
class RoomDeletionTest extends AbstractIntegrationTest {

    @Autowired private TestData testData;
    @Autowired private RoomService roomService;
    @Autowired private CinemaService cinemaService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private CinemaRepository cinemaRepository;

    private Long cinemaId;

    @BeforeEach
    void setUp() {
        cleanUp();
        cinemaId = testData.cinema("Cine Teste", "12345678000199").getId();
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
    }

    @Test
    void excluiSalaSemSessoesJuntoComOsAssentos() {
        Long roomId = roomService.create(new RoomRequestDTO("Sala 1", 25, cinemaId)).id();
        assertEquals(25, seatRepository.count());

        roomService.delete(roomId);

        assertFalse(roomRepository.existsById(roomId));
        assertEquals(0, seatRepository.count());
    }

    @Test
    void naoExcluiSalaComSessoes() {
        Long roomId = roomService.create(new RoomRequestDTO("Sala 1", 10, cinemaId)).id();
        criarSessao(roomRepository.findById(roomId).orElseThrow());

        assertThrows(ConflictException.class, () -> roomService.delete(roomId));

        assertTrue(roomRepository.existsById(roomId));
        assertEquals(10, seatRepository.count());
    }

    @Test
    void salaInexistenteRetornaNaoEncontrada() {
        assertThrows(ResourceNotFoundException.class, () -> roomService.delete(999_999L));
    }

    @Test
    void excluiCinemaSemSessoesJuntoComSalasEAssentos() {
        roomService.create(new RoomRequestDTO("Sala 1", 10, cinemaId));
        roomService.create(new RoomRequestDTO("Sala 2", 15, cinemaId));

        cinemaService.delete(cinemaId);

        assertFalse(cinemaRepository.existsById(cinemaId));
        assertEquals(0, roomRepository.count());
        assertEquals(0, seatRepository.count());
    }

    @Test
    void naoExcluiCinemaComSessoesEmAlgumaSala() {
        roomService.create(new RoomRequestDTO("Sala 1", 10, cinemaId));
        Long roomComSessao = roomService.create(new RoomRequestDTO("Sala 2", 10, cinemaId)).id();
        criarSessao(roomRepository.findById(roomComSessao).orElseThrow());

        assertThrows(ConflictException.class, () -> cinemaService.delete(cinemaId));

        assertTrue(cinemaRepository.existsById(cinemaId));
        assertEquals(2, roomRepository.count());
        assertEquals(20, seatRepository.count());
    }

    private void criarSessao(Room room) {
        testData.session(testData.movie("Filme Teste", 120), room, LocalDateTime.now().plusDays(1));
    }
}
