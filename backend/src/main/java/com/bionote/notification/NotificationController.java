package com.bionote.notification;

import com.bionote.common.ApiResponse;
import com.bionote.common.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service; public NotificationController(NotificationService service){this.service=service;}
    private UUID current(Authentication a){return UUID.fromString(a.getName());}
    @GetMapping PagedResponse<NotificationService.View> list(Authentication a,@RequestParam(defaultValue="false") boolean unreadOnly,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(current(a),unreadOnly,page,size);}
    @GetMapping("/unread-count") ApiResponse<Map<String,Long>> count(Authentication a){return ApiResponse.of(Map.of("count",service.unread(current(a))));}
    @PatchMapping("/{id}/read") ResponseEntity<Void> read(Authentication a,@PathVariable UUID id){service.read(current(a),id);return ResponseEntity.noContent().build();}
    @PostMapping("/read-all") ResponseEntity<Void> readAll(Authentication a){service.readAll(current(a));return ResponseEntity.noContent().build();}
}

