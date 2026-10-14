package com.app.cineticket.demo;

import com.app.cineticket.dto.response.DemoInfoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.bootstrap.staff.email:}")
    private String staffEmail;

    @Value("${app.bootstrap.staff.password:}")
    private String staffPassword;

    @Value("${app.bootstrap.manager.email:}")
    private String managerEmail;

    @Value("${app.bootstrap.manager.password:}")
    private String managerPassword;

    @GetMapping("/info")
    public ResponseEntity<DemoInfoDTO> info() {
        return ResponseEntity.ok(new DemoInfoDTO(
                true,
                "Ambiente de demonstração: pagamento em modo de teste, nenhum valor é cobrado. "
                        + "Reserve em POST /api/v1/tickets e pague em POST /api/v1/tickets/{id}/payment "
                        + "com paymentMethodId pm_card_visa (aprovado) ou pm_card_chargeDeclined (recusado). "
                        + "O funcionário valida ingressos em POST /api/v1/tickets/validate com o QR de GET /api/v1/tickets/{id}/qr. "
                        + "O gerente cadastra funcionários do próprio cinema em /api/v1/manager/staff. "
                        + "Na demonstração, o gerente não cancela sessões nem vendas.",
                demoDataService.getVisitorEmail(),
                demoDataService.getVisitorPassword(),
                staffEmail,
                staffPassword,
                managerEmail,
                managerPassword,
                "Compras, pedidos de filme e contas criadas são apagados todos os dias às 04:00 (horário de Brasília)."));
    }
}
