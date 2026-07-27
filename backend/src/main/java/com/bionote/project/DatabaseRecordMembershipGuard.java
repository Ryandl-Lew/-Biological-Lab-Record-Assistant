package com.bionote.project;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class DatabaseRecordMembershipGuard implements RecordMembershipGuard {
    private final JdbcTemplate jdbc;public DatabaseRecordMembershipGuard(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public List<BlockingItem> recordsBlockingArchive(UUID projectId){return jdbc.query("SELECT id,title,status FROM experiment_records WHERE project_id=? AND deleted_at IS NULL AND provisional=FALSE AND status<>'COMPLETED'",(rs,n)->new BlockingItem(UUID.fromString(rs.getString("id")),rs.getString("title"),rs.getString("status")),projectId.toString());}
    @Override public List<BlockingItem> recordsBlockingMemberChange(UUID projectId,UUID userId){return jdbc.query("SELECT id,title,status FROM experiment_records WHERE project_id=? AND creator_id=? AND deleted_at IS NULL AND provisional=FALSE AND status<>'COMPLETED'",(rs,n)->new BlockingItem(UUID.fromString(rs.getString("id")),rs.getString("title"),rs.getString("status")),projectId.toString(),userId.toString());}
}
