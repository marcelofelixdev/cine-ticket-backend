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
    String endereco

    ) {}
