package com.campus.continuity.service;

import com.campus.continuity.domain.RunRecord;
import com.campus.continuity.dto.Dtos.RunSummary;
import com.campus.continuity.repo.Repos.RunRepo;
import com.campus.continuity.web.ApiExceptions.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.List;

/** Persists full run results (allocation / simulation / benchmark) as JSON so history can be re-opened and exported. */
@Service
public class RunStore {
    private final RunRepo repo; private final ObjectMapper mapper;
    public RunStore(RunRepo repo, ObjectMapper mapper) { this.repo = repo; this.mapper = mapper; }

    public RunRecord save(String kind, String name, String summary, Object payload) {
        RunRecord r = new RunRecord();
        r.kind = kind; r.name = name.length() > 190 ? name.substring(0, 190) : name;
        r.summary = summary.length() > 990 ? summary.substring(0, 990) : summary;
        try { r.payload = mapper.writeValueAsString(payload); } catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialise run payload", e); }
        return repo.save(r);
    }
    public List<RunSummary> list(String kind) { return repo.findTop50ByKindOrderByIdDesc(kind).stream().map(Mappers::view).toList(); }
    public List<RunSummary> recent() { return repo.findTop10ByOrderByIdDesc().stream().map(Mappers::view).toList(); }
    public JsonNode payload(Long id, String kind) {
        RunRecord r = repo.findById(id).filter(x -> x.kind.equals(kind)).orElseThrow(() -> new NotFoundException(kind + " run " + id + " not found"));
        try { return mapper.readTree(r.payload); } catch (JsonProcessingException e) { throw new IllegalStateException("Stored payload is corrupt", e); }
    }
}
