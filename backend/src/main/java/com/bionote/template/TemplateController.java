package com.bionote.template;

import com.bionote.common.ApiResponse;
import com.bionote.common.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/templates")
public class TemplateController {
    private final TemplateService service;public TemplateController(TemplateService service){this.service=service;}private UUID current(Authentication a){return UUID.fromString(a.getName());}
    @GetMapping PagedResponse<TemplateDtos.View> list(Authentication a,@RequestParam(required=false)String scope,@RequestParam(required=false)String category,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(current(a),scope,category,keyword,page,size);}
    @PostMapping ApiResponse<TemplateDtos.View> create(Authentication a,@Valid @RequestBody TemplateDtos.SaveRequest r){return ApiResponse.of(service.create(current(a),r));}
    @GetMapping("/{id}") ApiResponse<TemplateDtos.View> get(Authentication a,@PathVariable UUID id){return ApiResponse.of(service.get(current(a),id));}
    @PutMapping("/{id}") ApiResponse<TemplateDtos.View> update(Authentication a,@PathVariable UUID id,@Valid @RequestBody TemplateDtos.SaveRequest r){return ApiResponse.of(service.update(current(a),id,r));}
    @DeleteMapping("/{id}") ResponseEntity<Void> delete(Authentication a,@PathVariable UUID id){service.delete(current(a),id);return ResponseEntity.noContent().build();}
    @PostMapping("/{id}/copy") ApiResponse<TemplateDtos.View> copy(Authentication a,@PathVariable UUID id){return ApiResponse.of(service.copy(current(a),id));}
}

