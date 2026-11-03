package com.campus.continuity.service;

import com.campus.continuity.domain.ActivityLog;
import com.campus.continuity.repo.Repos.ActivityRepo;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {
    private final ActivityRepo repo;
    public ActivityService(ActivityRepo repo) { this.repo = repo; }

    public void log(String action, String detail) {
        ActivityLog a = new ActivityLog();
        a.action = action;
        a.detail = detail.length() > 990 ? detail.substring(0, 990) : detail;
        repo.save(a);
    }
}
