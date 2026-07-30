package com.bionote.notification;

import com.bionote.common.PagedResponse;
import java.util.UUID;

public interface NotificationUseCase {
    PagedResponse<NotificationDtos.View> list(UUID userId, boolean unreadOnly, int page, int size);

    long unread(UUID userId);

    void read(UUID userId, UUID notificationId);

    void readAll(UUID userId);
}
