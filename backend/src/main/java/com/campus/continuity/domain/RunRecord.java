package com.campus.continuity.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "run_record")
public class RunRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String kind, name, summary;
    @Column(columnDefinition = "text") public String payload;
    public Instant createdAt = Instant.now();
}
