package com.bionote.dashboard;
import com.bionote.common.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/dashboard")
public class DashboardController {private final DashboardService service;public DashboardController(DashboardService service){this.service=service;}private UUID current(Authentication a){return UUID.fromString(a.getName());}@GetMapping("/tasks")ApiResponse<List<DashboardService.Task>>tasks(Authentication a){return ApiResponse.of(service.tasks(current(a)));}@GetMapping("/summary")ApiResponse<DashboardService.Summary>summary(Authentication a){return ApiResponse.of(service.summary(current(a)));}}
