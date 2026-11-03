package com.campus.continuity.domain;

import com.campus.continuity.engine.Model.Availability;
import jakarta.persistence.*;

/** JPA entity. Public fields keep the persistence classes compact; engine code never sees entities. */
@Entity @Table(name = "laboratory")
public class Laboratory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String code, name, building;
    public int capacity, maxTeamSize;
    public String equipment = "";
    @Enumerated(EnumType.STRING) public Availability status = Availability.AVAILABLE;
    public String reason;
}
