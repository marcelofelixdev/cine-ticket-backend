package com.app.cineticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CinemaRequestDTO(

    @NotBlank(message = "O nome do cinema é obrigatório")
    @Size(max = 100)
    String nome,

    @NotBlank(message = "O CNPJ é obrigatório")
    @Size(min = 14, max = 18, message = "O CNPJ deve ter entre 14 e 18 caracteres")
    String cnpj,

    @NotBlank(message = "O endereço é obrigatório")
    @Size(max = 200)
    String endereco,

    @NotBlank(message = "A cidade é obrigatória")
    @Size(max = 100)
    String cidade,

    @NotBlank(message = "A UF é obrigatória")
    @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{2}", message = "UF deve ter 2 letras maiúsculas (ex.: SP)")
    String uf

    ) {}
