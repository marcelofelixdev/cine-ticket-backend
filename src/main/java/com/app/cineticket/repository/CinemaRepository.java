package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    boolean existsByCnpj(String cnpj);
}


