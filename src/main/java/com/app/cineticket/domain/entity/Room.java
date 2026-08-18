package com.app.cineticket.domain.entity;


import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "tb_room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false)
    private Integer capacidade;

    @ManyToOne(fetch = FetchType.LAZY)

    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;
}
