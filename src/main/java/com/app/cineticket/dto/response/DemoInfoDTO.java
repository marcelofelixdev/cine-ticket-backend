package com.app.cineticket.dto.response;

/** Informações do ambiente de demonstração para o frontend exibir. */
public record DemoInfoDTO(
        boolean demo,
        String aviso,
        String visitanteEmail,
        String visitanteSenha,
        String porteiroEmail,
        String porteiroSenha,
        String reset
) {
}
