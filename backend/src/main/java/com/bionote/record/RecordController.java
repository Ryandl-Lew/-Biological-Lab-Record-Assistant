package com.bionote.record;

import com.bionote.common.ApiResponse;
import com.bionote.common.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/records")
public class RecordController {
    private final RecordService service;public RecordController(RecordService service){this.service=service;}private UUID current(Authentication a){return UUID.fromString(a.getName());}
    @GetMapping PagedResponse<RecordDtos.View> list(Authentication a,@RequestParam(required=false)UUID projectId,@RequestParam(required=false)UUID creatorId,@RequestParam(required=false)String status,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(current(a),projectId,creatorId,status,keyword,page,size);}
    @PostMapping("/reservations") ApiResponse<RecordDtos.View> reserve(Authentication a,@Valid @RequestBody RecordDtos.ReserveRequest r){return ApiResponse.of(service.reserve(current(a),r));}
    @DeleteMapping("/{id}/reservation") ResponseEntity<Void> discardReservation(Authentication a,@PathVariable UUID id){service.discardReservation(current(a),id);return ResponseEntity.noContent().build();}
    @PostMapping ApiResponse<RecordDtos.View> create(Authentication a,@Valid @RequestBody RecordDtos.CreateRequest r){return ApiResponse.of(service.create(current(a),r));}
    @GetMapping("/{id}") ApiResponse<RecordDtos.View> get(Authentication a,@PathVariable UUID id){return ApiResponse.of(service.get(current(a),id));}
    @PutMapping("/{id}") ApiResponse<RecordDtos.View> update(Authentication a,@PathVariable UUID id,@Valid @RequestBody RecordDtos.UpdateRequest r){return ApiResponse.of(service.update(current(a),id,r));}
    @DeleteMapping("/{id}") ResponseEntity<Void> delete(Authentication a,@PathVariable UUID id){service.delete(current(a),id);return ResponseEntity.noContent().build();}
}
