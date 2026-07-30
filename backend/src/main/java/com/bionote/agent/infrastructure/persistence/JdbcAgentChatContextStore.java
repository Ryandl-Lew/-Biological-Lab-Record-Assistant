package com.bionote.agent.infrastructure.persistence;

import com.bionote.agent.api.AgentChatContextStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAgentChatContextStore implements AgentChatContextStore {
    private final JdbcTemplate jdbc;
    public JdbcAgentChatContextStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public List<Map<String,Object>> recentRevisions(UUID recordId) {return jdbc.queryForList("SELECT rr.revision_no, rr.submit_note, rr.submitted_at, u.display_name submitter FROM record_revisions rr JOIN users u ON u.id=rr.submitted_by WHERE rr.record_id=? ORDER BY rr.revision_no DESC LIMIT 5",recordId.toString());}
    @Override public List<Map<String,Object>> recentReviews(UUID recordId) {return jdbc.queryForList("SELECT rv.status, rv.decision_comment, rv.decided_at, u.display_name reviewer FROM reviews rv JOIN users u ON u.id=rv.reviewer_id WHERE rv.record_id=? ORDER BY rv.decided_at DESC NULLS LAST LIMIT 3",recordId.toString());}
    @Override public List<Map<String,Object>> activeAttachments(UUID recordId) {return jdbc.queryForList("SELECT id, original_filename, media_type, size_bytes, storage_key FROM attachments WHERE record_id=? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 10",recordId.toString());}
    @Override public List<Map<String,Object>> projectAttachments(UUID projectId) {return jdbc.queryForList("SELECT a.id, a.original_filename, a.media_type, a.size_bytes, a.storage_key, a.record_id, r.code record_code, r.title record_title FROM attachments a JOIN experiment_records r ON r.id=a.record_id WHERE r.project_id=? AND a.deleted_at IS NULL AND r.deleted_at IS NULL ORDER BY a.created_at DESC LIMIT 10",projectId.toString());}
    @Override public List<Map<String,Object>> latestRecordArtifact(UUID recordId) {return jdbc.queryForList("SELECT content_json FROM agent_artifacts WHERE record_id=? AND artifact_kind='RECORD_SUMMARY' ORDER BY created_at DESC LIMIT 1",recordId.toString());}
    @Override public long memberCount(UUID projectId) {Long value=jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=?",Long.class,projectId.toString());return value==null?0:value;}
    @Override public List<Map<String,Object>> memberRoleCounts(UUID projectId) {return jdbc.queryForList("SELECT role, COUNT(*) AS cnt FROM project_members WHERE project_id=? GROUP BY role",projectId.toString());}
    @Override public List<Map<String,Object>> recordStatusCounts(UUID projectId) {return jdbc.queryForList("SELECT status, COUNT(*) AS cnt FROM experiment_records WHERE project_id=? AND deleted_at IS NULL AND provisional=FALSE GROUP BY status",projectId.toString());}
    @Override public List<Map<String,Object>> recentRecords(UUID projectId) {return jdbc.queryForList("SELECT code, title, status, current_revision_no, updated_at FROM experiment_records WHERE project_id=? AND deleted_at IS NULL AND provisional=FALSE ORDER BY updated_at DESC LIMIT 15",projectId.toString());}
    @Override public List<Map<String,Object>> latestProjectArtifact(UUID projectId) {return jdbc.queryForList("SELECT content_json FROM agent_artifacts WHERE project_id=? AND artifact_kind='PROJECT_PROGRESS' AND record_id IS NULL ORDER BY created_at DESC LIMIT 1",projectId.toString());}
    @Override public Optional<Map<String,Object>> findVisibleRecord(UUID actorId,UUID recordId) {return jdbc.queryForList("SELECT r.* FROM experiment_records r JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=? WHERE r.id=? AND r.deleted_at IS NULL AND r.provisional=FALSE",actorId.toString(),recordId.toString()).stream().findFirst();}
    @Override public Optional<Map<String,Object>> findVisibleProject(UUID actorId,UUID projectId) {return jdbc.queryForList("SELECT p.* FROM projects p JOIN project_members pm ON pm.project_id=p.id AND pm.user_id=? WHERE p.id=?",actorId.toString(),projectId.toString()).stream().findFirst();}
}
