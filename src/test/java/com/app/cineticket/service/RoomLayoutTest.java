package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.dto.request.RoomRequestDTO;
import com.app.cineticket.dto.response.RoomResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Layout da sala: cadeiras por fila, corredores e o limite de 26 filas (A a Z). */
class RoomLayoutTest extends AbstractIntegrationTest {

    @Autowired private RoomService roomService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private TestData testData;
    @Autowired private MockMvc mockMvc;

    private Long cinemaId;

    @BeforeEach
    void setUp() {
        cleanUp();
        cinemaId = testData.cinema("Cine Layout", "11222333000144").getId();
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
    }

    @Test
    void semLayoutUsaDezCadeirasPorFilaESemCorredores() {
        RoomResponseDTO room = roomService.create(new RoomRequestDTO("Sala padrão", 12, cinemaId));

        assertEquals(10, room.cadeirasPorFila());
        assertEquals(List.of(), room.corredores());
        List<Seat> seats = seatRepository.findByRoomIdOrderByFilaAscNumeroAsc(room.id());
        assertEquals("A-10", seats.get(9).label());
        assertEquals("B-1", seats.get(10).label());
    }

    @Test
    void geraAsFilasPeloLayoutEOMapaDevolveOsCorredoresEmOrdem() throws Exception {
        RoomResponseDTO room = roomService.create(
                new RoomRequestDTO("Sala 12", 24, cinemaId, 12, List.of(9, 3)));

        assertEquals(List.of(3, 9), room.corredores());
        List<Seat> seats = seatRepository.findByRoomIdOrderByFilaAscNumeroAsc(room.id());
        assertEquals("A-12", seats.get(11).label());
        assertEquals("B-12", seats.get(23).label());

        Room entity = roomRepository.findById(room.id()).orElseThrow();
        Long sessionId = testData.session(testData.movie("Filme Layout", 100), entity,
                LocalDateTime.now().plusDays(1)).getId();
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cadeirasPorFila").value(12))
                .andExpect(jsonPath("$.corredores", contains(3, 9)));
    }

    @Test
    void salaQuePassariaDaFilaZERecusada() {
        RoomRequestDTO request = new RoomRequestDTO("Sala gigante", 261, cinemaId);

        BusinessException error = assertThrows(BusinessException.class, () -> roomService.create(request));
        assertEquals("Com 10 cadeiras por fila, a sala teria 27 filas; o máximo é 26 (A a Z). "
                + "Aumente as cadeiras por fila.", error.getMessage());
    }

    @Test
    void corredorRepetidoOuDepoisDaUltimaCadeiraERecusado() {
        RoomRequestDTO repetido = new RoomRequestDTO("Sala", 20, cinemaId, 10, List.of(3, 3));
        RoomRequestDTO foraDaFila = new RoomRequestDTO("Sala", 20, cinemaId, 10, List.of(10));

        assertThrows(BusinessException.class, () -> roomService.create(repetido));
        assertThrows(BusinessException.class, () -> roomService.create(foraDaFila));
    }

    @Test
    void edicaoTrocaOsCorredoresMasNaoAsCadeirasPorFila() {
        RoomResponseDTO room = roomService.create(new RoomRequestDTO("Sala", 20, cinemaId, 10, List.of(5)));

        RoomResponseDTO semCorredores = roomService.update(room.id(),
                new RoomRequestDTO("Sala", 20, cinemaId));
        assertEquals(List.of(5), semCorredores.corredores(), "corredores nulos mantêm os atuais");

        RoomResponseDTO novos = roomService.update(room.id(),
                new RoomRequestDTO("Sala", 20, cinemaId, 10, List.of(2, 8)));
        assertEquals(List.of(2, 8), novos.corredores());

        RoomRequestDTO outraLargura = new RoomRequestDTO("Sala", 20, cinemaId, 12, null);
        Long roomId = room.id();
        assertThrows(BusinessException.class, () -> roomService.update(roomId, outraLargura));
    }
}
