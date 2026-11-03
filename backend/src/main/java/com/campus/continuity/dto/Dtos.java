package com.campus.continuity.dto;

import com.campus.continuity.engine.Model.Availability;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

/** Request/response records for the REST API. Validation annotations produce field-level 400 errors. */
public final class Dtos {
    private Dtos() {}

    public record LabRequest(@NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 160) String name, @Size(max = 160) String building,
                             @Min(0) @Max(100000) int capacity, @Min(1) @Max(100000) int maxTeamSize, List<String> equipment) {}
    public record SlotRequest(@NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 160) String label, @Size(max = 20) String dayOfWeek,
                              @Size(max = 10) String startTime, @Min(1) @Max(100000) int durationMinutes) {}
    public record StatusRequest(@NotNull Availability status, @Size(max = 500) String reason) {}
    public record TeamRequest(@NotBlank @Size(max = 80) String requestId, @NotBlank @Size(max = 80) String code, @Size(max = 160) String name,
                              @Min(1) @Max(100000) int size, List<String> requirements, @Min(1) @Max(100000) int durationMinutes,
                              List<String> eligibleSlots) {}
    public record ScreenRequest(@NotBlank @Size(max = 80) String requestId) {}
    public record BloomConfigRequest(@Min(1) @Max(5000) int expectedInsertions, @DecimalMin("0.0001") @DecimalMax("0.5") double falsePositiveProbability) {}
    public record AllocationRequest(@Size(max = 200) String scenarioName, boolean includeTrace) {}
    public record TraceRequest(@NotBlank String source, @Min(1) @Max(200) Integer maxTeams) {}
    public record BenchmarkRequest(@NotNull @Size(min = 1, max = 8) List<@Min(1) @Max(3000) Integer> sizes, @Min(1) @Max(15) int repetitions,
                                   long seed, @DecimalMin("0.0001") @DecimalMax("0.5") double falsePositiveProbability) {}

    public record LabView(Long id, String code, String name, String building, int capacity, int maxTeamSize, List<String> equipment,
                          Availability status, String reason) {}
    public record SlotView(Long id, String code, String label, String dayOfWeek, String startTime, int durationMinutes,
                           Availability status, String reason) {}
    public record TeamView(Long id, String requestId, String code, String name, int size, List<String> requirements, int durationMinutes,
                           List<String> eligibleSlots, Instant createdAt) {}
    public record ScreeningView(String bloomResult, String exactResult, String outcome) {}
    public record RegisterResult(TeamView team, ScreeningView screening, List<String> warnings) {}
    public record RequestLogView(Long id, String requestId, String teamCode, String bloomResult, String exactResult, String outcome,
                                 String source, String message, Instant createdAt) {}
    public record RunSummary(Long id, String kind, String name, String summary, Instant createdAt) {}
    public record ActivityView(Long id, String action, String detail, Instant createdAt) {}
    public record ImportResult(String type, int created, int skippedDuplicates, int skippedInvalid, int bloomRejected, List<String> messages) {}
}
