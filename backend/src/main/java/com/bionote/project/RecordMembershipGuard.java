package com.bionote.project;

import java.util.List;
import java.util.UUID;

public interface RecordMembershipGuard {
    List<BlockingItem> recordsBlockingArchive(UUID projectId);
    List<BlockingItem> recordsBlockingMemberChange(UUID projectId, UUID userId);
    record BlockingItem(UUID id, String title, String status) {}
}

