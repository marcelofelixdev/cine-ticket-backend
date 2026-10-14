package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    boolean existsByCnpj(String cnpj);

    /** Primeiro cinema cadastrado (menor ID), usado para vincular a equipe criada no boot. */
    java.util.Optional<Cinema> findFirstByOrderByIdAsc();

    org.springframework.data.domain.Page<Cinema> findByCidadeIgnoreCase(String cidade,
                                                                        org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select distinct new com.app.cineticket.dto.response.CidadeDTO(c.cidade, c.uf) from Cinema c
            where c.cidade is not null order by c.cidade""")
    java.util.List<com.app.cineticket.dto.response.CidadeDTO> findCidades();
}


