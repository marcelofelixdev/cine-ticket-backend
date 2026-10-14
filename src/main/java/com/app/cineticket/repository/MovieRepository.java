package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Movie> {

    boolean existsByTmdbId(String tmdbId);

    java.util.List<Movie> findByTmdbIdIn(java.util.Collection<String> tmdbIds);

    java.util.Optional<Movie> findByTmdbId(String tmdbId);

    Page<Movie> findAll(Pageable pageable);

}


