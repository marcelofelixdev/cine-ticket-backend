package com.app.cineticket.domain.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "tb_room")
public class Room extends AuditableEntity {

    public static final int CADEIRAS_POR_FILA_PADRAO = 10;

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

    @Column(name = "cadeiras_por_fila", nullable = false)
    private Integer cadeirasPorFila = CADEIRAS_POR_FILA_PADRAO;

    /** Número de cada cadeira depois da qual há um corredor, em ordem crescente. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "integer[]")
    private List<Integer> corredores = new ArrayList<>();
}
