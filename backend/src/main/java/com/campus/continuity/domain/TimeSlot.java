package com.campus.continuity.domain;

import com.campus.continuity.engine.Model.Availability;
import jakarta.persistence.*;

@Entity @Table(name = "time_slot")
public class TimeSlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String code, label, dayOfWeek, startTime;
    public int durationMinutes;
    @Enumerated(EnumType.STRING) public Availability status = Availability.AVAILABLE;
    public String reason;
}
