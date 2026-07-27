package com.bionote.export;

import com.bionote.common.ApiResponse;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/records/{recordId}/exports")
public class ExportController {
    private final ExportService service;public ExportController(ExportService service){this.service=service;}private UUID current(Authentication a){return UUID.fromString(a.getName());}
    @GetMapping("/preview") ApiResponse<ExportService.Preview> preview(Authentication a,@PathVariable UUID recordId){return ApiResponse.of(service.preview(current(a),recordId));}
    @GetMapping("/markdown") ResponseEntity<byte[]> markdown(Authentication a,@PathVariable UUID recordId){return file(service.markdown(current(a),recordId));}
    @GetMapping("/pdf") ResponseEntity<byte[]> pdf(Authentication a,@PathVariable UUID recordId){return file(service.pdf(current(a),recordId));}
    private ResponseEntity<byte[]> file(ExportService.FileExport f){String encoded=UriUtils.encode(f.filename(),StandardCharsets.UTF_8).replace("+","%20");return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.mediaType())).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+encoded).header("X-Content-Type-Options","nosniff").body(f.bytes());}
}
