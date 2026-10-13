package com.app.cineticket.demo;

import com.app.cineticket.dto.response.DemoInfoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Só existe no perfil demo: fora dele, a rota responde 404. */
@RestController
@RequestMapping("/api/v1/demo")
@Profile("demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoDataService demoDataService;

    @GetMapping("/info")
    public ResponseEntity<DemoInfoDTO> info() {
        return ResponseEntity.ok(new DemoInfoDTO(
                true,
                "Ambiente de demonstração: o pagamento é simulado e nenhum valor é cobrado.",
                demoDataService.getVisitorEmail(),
                demoDataService.getVisitorPassword(),
                "Compras e contas criadas são apagadas todos os dias às 04:00 (horário de Brasília)."));
    }
}
