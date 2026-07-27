package com.bionote.attachment;

import com.bionote.common.ApiResponse;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1")
public class AttachmentController {
    private final AttachmentService service;public AttachmentController(AttachmentService service){this.service=service;}private UUID current(Authentication a){return UUID.fromString(a.getName());}
    @GetMapping("/records/{recordId}/attachments") ApiResponse<List<AttachmentDtos.View>> list(Authentication a,@PathVariable UUID recordId){return ApiResponse.of(service.list(current(a),recordId));}
    @PostMapping(value="/records/{recordId}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) ApiResponse<AttachmentDtos.View> upload(Authentication a,@PathVariable UUID recordId,@RequestPart("file")MultipartFile file){return ApiResponse.of(service.upload(current(a),recordId,file));}
    @DeleteMapping("/attachments/{id}") ResponseEntity<Void> delete(Authentication a,@PathVariable UUID id){service.delete(current(a),id);return ResponseEntity.noContent().build();}
    @GetMapping("/attachments/{id}/preview") ResponseEntity<byte[]> preview(Authentication a,@PathVariable UUID id){return payload(service.load(current(a),id,true),"inline");}
    @GetMapping("/attachments/{id}/download") ResponseEntity<byte[]> download(Authentication a,@PathVariable UUID id){return payload(service.load(current(a),id,false),"attachment");}
    @GetMapping("/revisions/{revisionId}/attachments") ApiResponse<List<AttachmentDtos.View>> revisionAttachments(Authentication a,@PathVariable UUID revisionId){return ApiResponse.of(service.revisionAttachments(current(a),revisionId));}
    private ResponseEntity<byte[]> payload(AttachmentService.FilePayload p,String disposition){String encoded=UriUtils.encode(p.filename(),StandardCharsets.UTF_8).replace("+","%20");MediaType mediaType=MediaType.parseMediaType(p.mediaType());if("text".equals(mediaType.getType()))mediaType=new MediaType(mediaType,StandardCharsets.UTF_8);return ResponseEntity.ok().contentType(mediaType).header(HttpHeaders.CONTENT_DISPOSITION,disposition+"; filename*=UTF-8''"+encoded).header("X-Content-Type-Options","nosniff").body(p.bytes());}
}
