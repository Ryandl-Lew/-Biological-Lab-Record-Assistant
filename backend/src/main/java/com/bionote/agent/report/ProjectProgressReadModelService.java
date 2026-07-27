package com.bionote.agent.report;

import com.bionote.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectProgressReadModelService {
    private final JdbcTemplate jdbc;private final int staleDays;
    public ProjectProgressReadModelService(JdbcTemplate jdbc,@Value("${agent.progress-stale-days:14}") int staleDays){this.jdbc=jdbc;this.staleDays=Math.max(1,staleDays);}
    public String requireMember(UUID projectId,UUID actorId){List<String> roles=jdbc.queryForList("SELECT role FROM project_members WHERE project_id=? AND user_id=?",String.class,projectId.toString(),actorId.toString());if(roles.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Project not found or inaccessible");return roles.get(0);}
    public Map<String,Object> overview(UUID projectId,UUID actorId,Instant start,Instant end){requireMember(projectId,actorId);List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.id,p.name,p.status,p.owner_id,u.display_name owner_name,p.created_at,p.updated_at FROM projects p JOIN users u ON u.id=p.owner_id WHERE p.id=?",projectId.toString());if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Project not found or inaccessible");Map<String,Object> p=rows.get(0),out=new LinkedHashMap<>();out.put("id",p.get("id"));out.put("name",p.get("name"));out.put("status",p.get("status"));out.put("ownerName",p.get("owner_name"));out.put("memberCounts",counts("SELECT role k,COUNT(*) c FROM project_members WHERE project_id=? GROUP BY role",projectId));out.put("recordCounts",counts("SELECT status k,COUNT(*) c FROM experiment_records WHERE project_id=? AND deleted_at IS NULL AND provisional=FALSE GROUP BY status",projectId));Map<String,Object> activity=jdbc.queryForMap("SELECT MIN(created_at) earliest,MAX(created_at) latest FROM audit_events WHERE project_id=? AND created_at<=?",projectId.toString(),Timestamp.from(end));out.put("earliestActivityAt",activity.get("earliest"));out.put("latestActivityAt",activity.get("latest"));out.put("period",Map.of("start",start,"end",end));out.put("staleInProgressThresholdDays",staleDays);return out;}
    public Map<String,Object> progress(UUID projectId,UUID actorId,Instant start,Instant end){Map<String,Object> out=new LinkedHashMap<>(overview(projectId,actorId,start,end));out.put("periodEventCounts",counts("SELECT event_type k,COUNT(*) c FROM audit_events WHERE project_id=? AND created_at>=? AND created_at<=? GROUP BY event_type",projectId,start,end));out.put("blockingRecords",jdbc.queryForList("SELECT id,code,title,status,updated_at FROM experiment_records WHERE project_id=? AND deleted_at IS NULL AND provisional=FALSE AND (status IN ('CHANGES_REQUESTED','IN_REVIEW') OR (status='IN_PROGRESS' AND updated_at<?)) ORDER BY updated_at,id",projectId.toString(),Timestamp.from(end.minusSeconds(staleDays*86400L))));return out;}
    private Map<String,Long> counts(String sql,UUID project,Object... times){Object[] args=new Object[1+times.length];args[0]=project.toString();for(int i=0;i<times.length;i++)args[i+1]=times[i] instanceof Instant instant?Timestamp.from(instant):times[i];Map<String,Long> result=new LinkedHashMap<>();jdbc.query(sql,rs->{result.put(rs.getString("k"),rs.getLong("c"));},args);return result;}
}
