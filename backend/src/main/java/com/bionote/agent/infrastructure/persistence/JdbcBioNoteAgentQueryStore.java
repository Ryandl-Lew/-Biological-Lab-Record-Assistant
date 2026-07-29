package com.bionote.agent.infrastructure.persistence;

import com.bionote.agent.tool.bionote.BioNoteAgentQueryStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcBioNoteAgentQueryStore implements BioNoteAgentQueryStore {
    private final JdbcTemplate jdbc;
    public JdbcBioNoteAgentQueryStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public PageSlice listRecords(UUID projectId,Instant start,Instant end,List<String> statuses,int page,int size) {
        String filter=inFilter("r.status",statuses);List<Object> args=new ArrayList<>(List.of(projectId.toString(),Timestamp.from(start),Timestamp.from(end)));args.addAll(statuses);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM experiment_records r WHERE r.project_id=? AND r.deleted_at IS NULL AND r.provisional=FALSE AND r.updated_at>=? AND r.updated_at<=?"+filter,Long.class,args.toArray());
        List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add(page*size);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.id,r.code,r.title,r.status,r.updated_at,r.current_revision_no,u.display_name creator_name,rv.status review_status,rv.decision_comment review_comment FROM experiment_records r JOIN users u ON u.id=r.creator_id LEFT JOIN reviews rv ON rv.id=r.current_review_id WHERE r.project_id=? AND r.deleted_at IS NULL AND r.provisional=FALSE AND r.updated_at>=? AND r.updated_at<=?"+filter+" ORDER BY r.updated_at DESC,r.id DESC LIMIT ? OFFSET ?",paged.toArray());
        return new PageSlice(rows,total);
    }
    @Override public long revisionCount(UUID recordId,int maxRevisionNo) {return jdbc.queryForObject("SELECT COUNT(*) FROM record_revisions WHERE record_id=? AND revision_no<=?",Long.class,recordId.toString(),maxRevisionNo);}
    @Override public long activeAttachmentCount(UUID recordId) {return jdbc.queryForObject("SELECT COUNT(*) FROM attachments WHERE record_id=? AND deleted_at IS NULL",Long.class,recordId.toString());}
    @Override public List<Map<String,Object>> listRevisions(UUID recordId,int maxRevisionNo,Instant start,Instant end) {return jdbc.queryForList("SELECT rr.id,rr.revision_no,rr.submitted_at,rr.submit_note,rr.content_hash,u.display_name submitter_name,rv.id review_id,rv.status review_status,rv.decision_comment FROM record_revisions rr JOIN users u ON u.id=rr.submitted_by LEFT JOIN reviews rv ON rv.revision_id=rr.id WHERE rr.record_id=? AND rr.revision_no<=? AND rr.submitted_at>=? AND rr.submitted_at<=? ORDER BY rr.revision_no DESC LIMIT 20",recordId.toString(),maxRevisionNo,Timestamp.from(start),Timestamp.from(end));}
    @Override public List<Map<String,Object>> listReviews(UUID recordId,int maxRevisionNo) {return jdbc.queryForList("SELECT rv.id,rv.revision_id,rr.revision_no,u.display_name reviewer_name,rv.status,rv.decision_comment,rv.assigned_at,rv.decided_at FROM reviews rv JOIN record_revisions rr ON rr.id=rv.revision_id JOIN users u ON u.id=rv.reviewer_id WHERE rv.record_id=? AND rr.revision_no<=? ORDER BY rr.revision_no DESC LIMIT 20",recordId.toString(),maxRevisionNo);}
    @Override public PageSlice activity(UUID projectId,Instant start,Instant end,List<String> eventTypes,int page,int size) {
        String filter=inFilter("a.event_type",eventTypes);List<Object> args=new ArrayList<>(List.of(projectId.toString(),Timestamp.from(start),Timestamp.from(end)));args.addAll(eventTypes);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM audit_events a WHERE a.project_id=? AND a.created_at>=? AND a.created_at<=?"+filter,Long.class,args.toArray());
        List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add(page*size);
        return new PageSlice(jdbc.queryForList("SELECT a.id,a.event_type,a.created_at,a.record_id,a.target_type,a.target_id,a.metadata_json,u.display_name actor_name FROM audit_events a LEFT JOIN users u ON u.id=a.actor_id WHERE a.project_id=? AND a.created_at>=? AND a.created_at<=?"+filter+" ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",paged.toArray()),total);
    }
    @Override public Optional<Map<String,Object>> latestProjectReport(UUID projectId,UUID excludedRunId) {return jdbc.queryForList("SELECT aa.id,aa.created_at,aa.content_json FROM agent_artifacts aa WHERE aa.project_id=? AND aa.artifact_kind='PROJECT_PROGRESS' AND aa.run_id<>? ORDER BY aa.created_at DESC,aa.id DESC LIMIT 1",projectId.toString(),excludedRunId.toString()).stream().findFirst();}
    @Override public Optional<Map<String,Object>> findRecord(UUID recordId,UUID projectId) {return jdbc.queryForList("SELECT r.*,u.display_name creator_name FROM experiment_records r JOIN users u ON u.id=r.creator_id WHERE r.id=? AND r.project_id=? AND r.deleted_at IS NULL AND r.provisional=FALSE",recordId.toString(),projectId.toString()).stream().findFirst();}
    @Override public Optional<UUID> findRevisionRecord(UUID revisionId,UUID projectId,int maxRevisionNo,Instant through) {return jdbc.queryForList("SELECT rr.record_id FROM record_revisions rr JOIN experiment_records r ON r.id=rr.record_id WHERE rr.id=? AND r.project_id=? AND r.deleted_at IS NULL AND rr.revision_no<=? AND rr.submitted_at<=?",String.class,revisionId.toString(),projectId.toString(),maxRevisionNo,Timestamp.from(through)).stream().findFirst().map(UUID::fromString);}
    @Override public String inputCursorJson(UUID runId) {return jdbc.queryForObject("SELECT input_cursor_json FROM agent_runs WHERE id=?",String.class,runId.toString());}
    @Override public Optional<Map<String,Object>> findReview(UUID reviewId) {return jdbc.queryForList("SELECT rv.id,rv.revision_id,rv.status,rv.decision_comment,rv.decided_at,u.display_name reviewer_name FROM reviews rv JOIN users u ON u.id=rv.reviewer_id WHERE rv.id=?",reviewId.toString()).stream().findFirst();}
    private String inFilter(String column,List<String> values) {return values==null||values.isEmpty()?"":" AND "+column+" IN ("+String.join(",",values.stream().map(value -> "?").toList())+")";}
}
