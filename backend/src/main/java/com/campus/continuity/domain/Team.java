package com.campus.continuity.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "team")
public class Team {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String requestId, code, name;
    public int size, durationMinutes;
    public String requirements = "", eligibleSlots = "";
    public Instant createdAt = Instant.now();
}
