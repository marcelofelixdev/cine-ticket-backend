package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

}


