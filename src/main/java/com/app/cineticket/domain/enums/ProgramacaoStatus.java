package com.app.cineticket.domain.enums;

/** Situação de um filme em um cinema. */
public enum ProgramacaoStatus {
    /** O admin enviou o filme; o gerente ainda não confirmou. */
    AGUARDANDO_GERENTE,
    /** Em cartaz no cinema: aparece no site e pode ter sessões. */
    ATIVO
}
