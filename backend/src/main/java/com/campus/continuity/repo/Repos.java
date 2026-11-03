package com.campus.continuity.repo;

import com.campus.continuity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** Spring Data repositories (infrastructure only — no assessed algorithm lives here). */
public final class Repos {
    private Repos() {}
    public interface LabRepo extends JpaRepository<Laboratory, Long> {
        Optional<Laboratory> findByCodeIgnoreCase(String code);
        boolean existsByCodeIgnoreCase(String code);
    }
    public interface SlotRepo extends JpaRepository<TimeSlot, Long> {
        Optional<TimeSlot> findByCodeIgnoreCase(String code);
        boolean existsByCodeIgnoreCase(String code);
    }
    public interface TeamRepo extends JpaRepository<Team, Long> {
        boolean existsByRequestId(String requestId);
        boolean existsByCodeIgnoreCase(String code);
        Optional<Team> findByCodeIgnoreCase(String code);
    }
    public interface RequestLogRepo extends JpaRepository<RequestLog, Long> {
        List<RequestLog> findTop300ByOrderByIdDesc();
    }
    public interface RunRepo extends JpaRepository<RunRecord, Long> {
        List<RunRecord> findTop50ByKindOrderByIdDesc(String kind);
        List<RunRecord> findTop10ByOrderByIdDesc();
    }
    public interface ActivityRepo extends JpaRepository<ActivityLog, Long> {
        List<ActivityLog> findTop200ByOrderByIdDesc();
    }
}
