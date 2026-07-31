package com.bionote.project.infrastructure.persistence;

import com.bionote.project.RecordMembershipGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaRecordMembershipGuard implements RecordMembershipGuard {
    private final JpaProjectPersistenceAdapter persistence;

    public JpaRecordMembershipGuard(JpaProjectPersistenceAdapter persistence) {
        this.persistence = persistence;
    }

    @Override
    public List<BlockingItem> recordsBlockingArchive(UUID projectId) {
        return persistence.blockingArchive(projectId).stream()
                .map(record -> new BlockingItem(record.id, record.title, record.status))
                .toList();
    }

    @Override
    public List<BlockingItem> recordsBlockingMemberChange(UUID projectId, UUID userId) {
        return persistence.blockingMember(projectId, userId).stream()
                .map(record -> new BlockingItem(record.id, record.title, record.status))
                .toList();
    }
}
