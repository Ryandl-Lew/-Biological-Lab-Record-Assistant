package com.bionote.audit;
import com.bionote.common.PagedResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/projects/{projectId}")
public class AuditController {private final AuditService service;public AuditController(AuditService service){this.service=service;}private UUID current(Authentication a){return UUID.fromString(a.getName());}@GetMapping("/audit-events")PagedResponse<AuditService.View>events(Authentication a,@PathVariable UUID projectId,@RequestParam(required=false)String eventType,@RequestParam(required=false)UUID actorId,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(current(a),projectId,eventType,actorId,from,to,page,size);}@GetMapping("/attachments")PagedResponse<AuditService.AttachmentSummary>attachments(Authentication a,@PathVariable UUID projectId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.attachments(current(a),projectId,page,size);}}
