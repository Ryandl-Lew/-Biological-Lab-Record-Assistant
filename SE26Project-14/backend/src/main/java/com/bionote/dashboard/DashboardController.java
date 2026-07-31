package com.bionote.dashboard;

import com.bionote.common.ApiResponse;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardUseCase service;

    public DashboardController(DashboardUseCase service) {
        this.service = service;
    }

    private UUID current(Authentication a) {
        return UUID.fromString(a.getName());
    }

    @GetMapping("/tasks")
    ApiResponse<List<DashboardDtos.Task>> tasks(Authentication a) {
        return ApiResponse.of(service.tasks(current(a)));
    }

    @GetMapping("/summary")
    ApiResponse<DashboardDtos.Summary> summary(Authentication a) {
        return ApiResponse.of(service.summary(current(a)));
    }
}
