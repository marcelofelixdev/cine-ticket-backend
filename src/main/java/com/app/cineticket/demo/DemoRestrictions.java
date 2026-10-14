package com.app.cineticket.demo;

import com.app.cineticket.exception.ForbiddenException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * No perfil demo, o login do gerente é público. Ações que afetam outros visitantes
 * (cancelar sessão, cancelar venda) ficam desligadas lá. Fora do demo, não faz nada.
 */
@Component
public class DemoRestrictions {

    private final boolean demo;

    public DemoRestrictions(Environment environment) {
        this.demo = environment.acceptsProfiles(Profiles.of("demo"));
    }

    /** Responde 403 no demo. {@code action} entra na mensagem, ex.: "Cancelar sessões". */
    public void requireNotDemo(String action) {
        if (demo) {
            throw new ForbiddenException(action + " fica desativado na demonstração pública.");
        }
    }
}
