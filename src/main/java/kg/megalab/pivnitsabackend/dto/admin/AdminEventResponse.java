package kg.megalab.pivnitsabackend.dto.admin;

import kg.megalab.pivnitsabackend.entity.EventStatus;

import java.time.OffsetDateTime;

public record AdminEventResponse(
        Long id,
        String title,
        String description,
        String bannerUrl,
        EventStatus status,
        String shareUrl,
        Boolean notificationSent,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
