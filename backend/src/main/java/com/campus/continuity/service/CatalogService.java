package com.campus.continuity.service;

import com.campus.continuity.domain.*;
import com.campus.continuity.dto.Dtos.*;
import com.campus.continuity.engine.Model;
import com.campus.continuity.engine.Model.Availability;
import com.campus.continuity.repo.Repos.*;
import com.campus.continuity.web.ApiExceptions.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Laboratories and time slots: CRUD, disruption status, and construction of the engine Snapshot from the database. */
@Service
@Transactional
public class CatalogService {
    private final LabRepo labs; private final SlotRepo slots; private final TeamRepo teams; private final ActivityService activity;

    public CatalogService(LabRepo labs, SlotRepo slots, TeamRepo teams, ActivityService activity) {
        this.labs = labs; this.slots = slots; this.teams = teams; this.activity = activity;
    }

    @Transactional(readOnly = true) public List<LabView> labs() { return labs.findAll(Sort.by("id")).stream().map(Mappers::view).toList(); }
    @Transactional(readOnly = true) public List<SlotView> slots() { return slots.findAll(Sort.by("id")).stream().map(Mappers::view).toList(); }
    @Transactional(readOnly = true) public LabView lab(Long id) { return Mappers.view(findLab(id)); }

    public LabView createLab(LabRequest r) {
        if (labs.existsByCodeIgnoreCase(r.code().trim())) throw new ConflictException("Laboratory code already exists: " + r.code());
        Laboratory l = new Laboratory();
        fill(l, r);
        labs.save(l);
        activity.log("LAB_CREATED", l.code + " capacity " + l.capacity);
        return Mappers.view(l);
    }

    public LabView updateLab(Long id, LabRequest r) {
        Laboratory l = findLab(id);
        labs.findByCodeIgnoreCase(r.code().trim()).filter(x -> !x.id.equals(id)).ifPresent(x -> { throw new ConflictException("Laboratory code already exists: " + r.code()); });
        fill(l, r);
        activity.log("LAB_UPDATED", l.code + " capacity " + l.capacity + ", equipment " + l.equipment);
        return Mappers.view(l);
    }

    public LabView setLabStatus(Long id, StatusRequest r) {
        Laboratory l = findLab(id);
        l.status = r.status();
        l.reason = r.status() == Availability.AVAILABLE ? null : r.reason();
        activity.log("LAB_STATUS", l.code + " → " + l.status + (l.reason == null ? "" : " (" + l.reason + ")"));
        return Mappers.view(l);
    }

    public SlotView createSlot(SlotRequest r) {
        if (slots.existsByCodeIgnoreCase(r.code().trim())) throw new ConflictException("Time slot code already exists: " + r.code());
        TimeSlot s = new TimeSlot();
        fill(s, r);
        slots.save(s);
        activity.log("SLOT_CREATED", s.code);
        return Mappers.view(s);
    }

    public SlotView updateSlot(Long id, SlotRequest r) {
        TimeSlot s = findSlot(id);
        slots.findByCodeIgnoreCase(r.code().trim()).filter(x -> !x.id.equals(id)).ifPresent(x -> { throw new ConflictException("Time slot code already exists: " + r.code()); });
        fill(s, r);
        activity.log("SLOT_UPDATED", s.code);
        return Mappers.view(s);
    }

    public SlotView setSlotStatus(Long id, StatusRequest r) {
        TimeSlot s = findSlot(id);
        s.status = r.status();
        s.reason = r.status() == Availability.AVAILABLE ? null : r.reason();
        activity.log("SLOT_STATUS", s.code + " → " + s.status + (s.reason == null ? "" : " (" + s.reason + ")"));
        return Mappers.view(s);
    }

    /** Fresh engine input built from the current database state. Never cached. */
    @Transactional(readOnly = true)
    public Model.Snapshot snapshot() {
        return new Model.Snapshot(
                labs.findAll(Sort.by("id")).stream().map(Mappers::toModel).toList(),
                slots.findAll(Sort.by("id")).stream().map(Mappers::toModel).toList(),
                teams.findAll(Sort.by("id")).stream().map(Mappers::toModel).toList());
    }

    private Laboratory findLab(Long id) { return labs.findById(id).orElseThrow(() -> new NotFoundException("Laboratory " + id + " not found")); }
    private TimeSlot findSlot(Long id) { return slots.findById(id).orElseThrow(() -> new NotFoundException("Time slot " + id + " not found")); }

    private void fill(Laboratory l, LabRequest r) {
        l.code = r.code().trim(); l.name = r.name().trim(); l.building = r.building();
        l.capacity = r.capacity(); l.maxTeamSize = r.maxTeamSize(); l.equipment = Mappers.join(r.equipment());
    }
    private void fill(TimeSlot s, SlotRequest r) {
        s.code = r.code().trim(); s.label = r.label().trim(); s.dayOfWeek = r.dayOfWeek(); s.startTime = r.startTime(); s.durationMinutes = r.durationMinutes();
    }
}
