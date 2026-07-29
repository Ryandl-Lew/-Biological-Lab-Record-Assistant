package com.bionote.search;
import java.time.Instant;
import java.util.*;
public final class SearchDtos {private SearchDtos(){}public record Result(String entityType,String id,String title,String snippet,UUID projectId,String projectName,String status,Instant updatedAt,Map<String,Object> target){}public record Meta(int page,int size,long total,Map<String,Long> counts){}public record Response(List<Result> data,Meta meta){}}
