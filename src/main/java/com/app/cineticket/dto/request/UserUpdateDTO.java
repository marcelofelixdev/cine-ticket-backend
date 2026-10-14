package com.app.cineticket.dto.request;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Atualização parcial do próprio perfil: campos nulos não são alterados. */
public record UserUpdateDTO(
        @Size(min = 1, max = 150, message = "O nome deve ter entre 1 e 150 caracteres") String nome,
        @Past(message = "A data de nascimento deve estar no passado") LocalDate dataNascimento
) {}
