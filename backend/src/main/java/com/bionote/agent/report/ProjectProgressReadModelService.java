package com.bionote.agent.report;

import com.bionote.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectProgressReadModelService {
    private final ProjectProgressStore store;private final int staleDays;
    public ProjectProgressReadModelService(ProjectProgressStore store,@Value("${agent.progress-stale-days:14}") int staleDays){this.store=store;this.staleDays=Math.max(1,staleDays);}
    public String requireMember(UUID projectId,UUID actorId){return store.findRole(projectId,actorId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Project not found or inaccessible"));}
    public Map<String,Object> overview(UUID projectId,UUID actorId,Instant start,Instant end){requireMember(projectId,actorId);Map<String,Object> p=store.findProjectOverview(projectId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Project not found or inaccessible")),out=new LinkedHashMap<>();out.put("id",p.get("id"));out.put("name",p.get("name"));out.put("status",p.get("status"));out.put("ownerName",p.get("owner_name"));out.put("memberCounts",store.memberCounts(projectId));out.put("recordCounts",store.recordCounts(projectId));Map<String,Object> activity=store.activityBounds(projectId,end);out.put("earliestActivityAt",activity.get("earliest"));out.put("latestActivityAt",activity.get("latest"));out.put("period",Map.of("start",start,"end",end));out.put("staleInProgressThresholdDays",staleDays);return out;}
    public Map<String,Object> progress(UUID projectId,UUID actorId,Instant start,Instant end){Map<String,Object> out=new LinkedHashMap<>(overview(projectId,actorId,start,end));out.put("periodEventCounts",store.eventCounts(projectId,start,end));out.put("blockingRecords",store.blockingRecords(projectId,end.minusSeconds(staleDays*86400L)));return out;}
}
