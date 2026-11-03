package com.campus.continuity.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "request_log")
public class RequestLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String requestId, teamCode, bloomResult, exactResult, outcome, source, message;
    public Instant createdAt = Instant.now();
}
