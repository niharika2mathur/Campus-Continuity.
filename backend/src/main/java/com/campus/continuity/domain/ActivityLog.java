package com.campus.continuity.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "activity_log")
public class ActivityLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String action, detail;
    public Instant createdAt = Instant.now();
}
