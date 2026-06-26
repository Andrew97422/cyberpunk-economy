package ru.andrew.mainserver.terminal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.andrew.mainserver.common.entity.BaseEntity;

@Entity
@Table(name = "terminals")
@Getter
@Setter
public class Terminal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}