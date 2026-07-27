package com.bionote.search;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/search")
public class SearchController {private final SearchService service;public SearchController(SearchService service){this.service=service;}@GetMapping SearchDtos.Response search(Authentication a,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="ALL")String entityType,@RequestParam(required=false)UUID projectId,@RequestParam(required=false)UUID creatorId,@RequestParam(required=false)String recordStatus,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.search(UUID.fromString(a.getName()),keyword,entityType,projectId,creatorId,recordStatus,page,size);}}
