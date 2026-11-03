package com.campus.continuity.web;

import com.campus.continuity.csv.ImportValidator;
import com.campus.continuity.dto.Dtos.*;
import com.campus.continuity.service.*;
import com.campus.continuity.service.AllocationService.AllocationRunView;
import com.campus.continuity.service.BenchmarkService.BenchmarkView;
import com.campus.continuity.service.SimulationService.*;
import com.campus.continuity.repo.Repos.ActivityRepo;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

/** All REST controllers. Controllers only validate and delegate; business logic lives in services and the engine package. */
public final class Controllers {
    private Controllers() {}

    @RestController @RequestMapping("/api")
    public static class CatalogController {
        private final CatalogService s;
        public CatalogController(CatalogService s) { this.s = s; }
        @GetMapping("/labs") public List<LabView> labs() { return s.labs(); }
        @GetMapping("/labs/{id}") public LabView lab(@PathVariable Long id) { return s.lab(id); }
        @PostMapping("/labs") @ResponseStatus(HttpStatus.CREATED) public LabView createLab(@Valid @RequestBody LabRequest r) { return s.createLab(r); }
        @PutMapping("/labs/{id}") public LabView updateLab(@PathVariable Long id, @Valid @RequestBody LabRequest r) { return s.updateLab(id, r); }
        @PatchMapping("/labs/{id}/status") public LabView labStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest r) { return s.setLabStatus(id, r); }
        @GetMapping("/slots") public List<SlotView> slots() { return s.slots(); }
        @PostMapping("/slots") @ResponseStatus(HttpStatus.CREATED) public SlotView createSlot(@Valid @RequestBody SlotRequest r) { return s.createSlot(r); }
        @PutMapping("/slots/{id}") public SlotView updateSlot(@PathVariable Long id, @Valid @RequestBody SlotRequest r) { return s.updateSlot(id, r); }
        @PatchMapping("/slots/{id}/status") public SlotView slotStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest r) { return s.setSlotStatus(id, r); }
    }

    @RestController @RequestMapping("/api")
    public static class TeamController {
        private final TeamService s; private final BloomScreeningService bloom; private final com.campus.continuity.repo.Repos.TeamRepo repo;
        public TeamController(TeamService s, BloomScreeningService bloom, com.campus.continuity.repo.Repos.TeamRepo repo) { this.s = s; this.bloom = bloom; this.repo = repo; }
        @GetMapping("/teams") public List<TeamView> list() { return s.list(); }
        @GetMapping("/teams/{id}") public TeamView get(@PathVariable Long id) { return s.get(id); }
        @PostMapping("/teams") @ResponseStatus(HttpStatus.CREATED)
        public RegisterResult register(@Valid @RequestBody TeamRequest r, @RequestParam(defaultValue = "false") boolean strict) { return s.register(r, "MANUAL", strict); }
        @PutMapping("/teams/{id}") public TeamView update(@PathVariable Long id, @Valid @RequestBody TeamRequest r) { return s.update(id, r); }
        @DeleteMapping("/teams/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { s.delete(id); }
        @GetMapping("/requests") public List<RequestLogView> requests() { return s.requestLog(); }
        /** Screens an identifier against the live Bloom filter, verifying positives against the database. Does not create a request. */
        @PostMapping("/requests/screen")
        public BloomScreeningService.QueryResult screen(@Valid @RequestBody ScreenRequest r) {
            return bloom.probe(TeamService.normalizeRequestId(r.requestId()), repo::existsByRequestId);
        }
    }

    @RestController @RequestMapping("/api/import")
    public static class ImportController {
        private final ImportService s;
        public ImportController(ImportService s) { this.s = s; }
        @PostMapping("/{type}/preview") public ImportValidator.Preview preview(@PathVariable String type, @RequestParam("file") MultipartFile f) { return s.preview(type, ImportService.read(f)); }
        @PostMapping("/{type}/commit") public ImportResult commit(@PathVariable String type, @RequestParam("file") MultipartFile f) { return s.commit(type, ImportService.read(f), "CSV"); }
        @GetMapping("/{type}/columns")
        public Map<String, List<String>> columns(@PathVariable String type) {
            return Map.of("columns", switch (type) {
                case "labs" -> ImportValidator.LAB_COLS; case "slots" -> ImportValidator.SLOT_COLS; case "teams" -> ImportValidator.TEAM_COLS;
                default -> throw new IllegalArgumentException("Unknown import type '" + type + "'");
            });
        }
    }

    @RestController @RequestMapping("/api")
    public static class AllocationController {
        private final AllocationService a; private final ObservatoryService o;
        public AllocationController(AllocationService a, ObservatoryService o) { this.a = a; this.o = o; }
        @PostMapping("/allocations") public AllocationRunView run(@Valid @RequestBody AllocationRequest r) { return a.run(r); }
        @GetMapping("/allocations") public List<RunSummary> history() { return a.history(); }
        @GetMapping("/allocations/{id}") public JsonNode get(@PathVariable Long id) { return a.get(id); }
        @GetMapping("/allocations/{id}/export")
        public ResponseEntity<String> export(@PathVariable Long id) {
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=allocation-" + id + ".csv")
                    .contentType(MediaType.parseMediaType("text/csv")).body(a.exportCsv(id));
        }
        @GetMapping("/observatory/presets") public List<ObservatoryService.PresetInfo> presets() { return o.presets(); }
        @PostMapping("/observatory/trace") public AllocationRunView trace(@Valid @RequestBody TraceRequest r) { return o.trace(r); }
    }

    @RestController @RequestMapping("/api/bloom")
    public static class BloomController {
        private final BloomScreeningService b; private final com.campus.continuity.repo.Repos.TeamRepo repo;
        public BloomController(BloomScreeningService b, com.campus.continuity.repo.Repos.TeamRepo repo) { this.b = b; this.repo = repo; }
        @GetMapping("/live") public BloomScreeningService.State live() { return b.state(); }
        @GetMapping("/sandbox") public BloomScreeningService.State sandbox() { return b.sandboxState(); }
        @PostMapping("/sandbox/reset") public BloomScreeningService.State reset(@Valid @RequestBody BloomConfigRequest r) { return b.sandboxReset(r.expectedInsertions(), r.falsePositiveProbability()); }
        @PostMapping("/sandbox/add") public BloomScreeningService.QueryResult add(@Valid @RequestBody ScreenRequest r) { return b.sandboxAdd(r.requestId().trim()); }
        @PostMapping("/sandbox/query") public BloomScreeningService.QueryResult query(@Valid @RequestBody ScreenRequest r) { return b.sandboxQuery(r.requestId().trim()); }
    }

    @RestController @RequestMapping("/api/simulations")
    public static class SimulationController {
        private final SimulationService s;
        public SimulationController(SimulationService s) { this.s = s; }
        @PostMapping public SimulationView run(@RequestBody SimulationRequest r) { return s.run(r); }
        @GetMapping public List<RunSummary> history() { return s.history(); }
        @GetMapping("/{id}") public JsonNode get(@PathVariable Long id) { return s.get(id); }
        @PostMapping("/apply") public ApplyResult apply(@RequestBody SimulationRequest r) { return s.apply(r.mutations()); }
    }

    @RestController @RequestMapping("/api/benchmarks")
    public static class BenchmarkController {
        private final BenchmarkService s;
        public BenchmarkController(BenchmarkService s) { this.s = s; }
        @PostMapping public BenchmarkView run(@Valid @RequestBody BenchmarkRequest r) { return s.run(r); }
        @GetMapping public List<RunSummary> history() { return s.history(); }
        @GetMapping("/{id}") public JsonNode get(@PathVariable Long id) { return s.get(id); }
    }

    @RestController @RequestMapping("/api")
    public static class DashboardController {
        private final DashboardService d; private final ActivityRepo activity; private final RunStore runs;
        public DashboardController(DashboardService d, ActivityRepo activity, RunStore runs) { this.d = d; this.activity = activity; this.runs = runs; }
        @GetMapping("/dashboard") public DashboardService.DashboardView dashboard() { return d.get(); }
        @GetMapping("/reports/activity") public List<ActivityView> activity() { return activity.findTop200ByOrderByIdDesc().stream().map(Mappers::view).toList(); }
        @GetMapping("/reports/runs") public List<RunSummary> runs(@RequestParam(defaultValue = "ALLOCATION") String kind) { return runs.list(kind.toUpperCase(Locale.ROOT)); }
    }

    @RestController @RequestMapping("/api/admin")
    public static class AdminController {
        private final AdminService s;
        public AdminController(AdminService s) { this.s = s; }
        @PostMapping("/seed") public Map<String, Object> seed(@RequestParam(defaultValue = "true") boolean reset) { return s.seed(reset); }
        @PostMapping("/reset") @ResponseStatus(HttpStatus.NO_CONTENT) public void reset() { s.reset(); }
        @GetMapping("/health") public AdminService.Health health() { return s.health(); }
    }
}
