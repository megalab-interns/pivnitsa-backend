package kg.megalab.pivnitsabackend.service;

import kg.megalab.pivnitsabackend.dto.admin.AdminEventResponse;
import kg.megalab.pivnitsabackend.dto.admin.CreateEventRequest;
import kg.megalab.pivnitsabackend.dto.admin.UpdateEventRequest;
import kg.megalab.pivnitsabackend.dto.event.*;
import kg.megalab.pivnitsabackend.dto.notification.EventPublishedEvent;
import kg.megalab.pivnitsabackend.entity.Event;
import kg.megalab.pivnitsabackend.entity.EventStatus;
import kg.megalab.pivnitsabackend.exception.EventNotFoundException;
import kg.megalab.pivnitsabackend.exception.InvalidEventStatusException;
import kg.megalab.pivnitsabackend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    @Value("${app.mobile.canonicalUrl}")
    private String canonicalUrl;

    private final EventRepository eventRepository;
    private final S3FileStorageService s3FileStorageService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;


    public EventBannersResponse getEventBanners(int limit) {

        int safeLimit = Math.clamp(limit, 1, 50);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Pageable pageable = PageRequest.of(0, safeLimit);

        List<Event> events = eventRepository.findUpcomingPublishedEvents(now, pageable);

        List<EventBannerResponse> eventBannersResponse = events.stream()
                .map(entity -> new EventBannerResponse(
                        entity.getId(),
                        entity.getTitle(),
                        s3FileStorageService.toFullUrl(entity.getBannerUrl()),
                        entity.getStartsAt()
                ))
                .toList();

        return new EventBannersResponse(eventBannersResponse);
    }

    public EventResponse create(CreateEventRequest request) {

        if (request.status() == EventStatus.ARCHIVED)
            throw new InvalidEventStatusException("Нельзя создать архивированное мероприятие");

        String bannerUrl = s3FileStorageService.upload(request.file(), "banners");

        try {
            Event savedEvent = transactionTemplate.execute(_ -> {
                Event event = Event.builder()
                        .title(request.title())
                        .description(request.description())
                        .bannerUrl(bannerUrl)
                        .status(request.status())
                        .startsAt(request.startsAt())
                        .endsAt(request.endsAt())
                        .build();

                Event saved = eventRepository.save(event);
                checkAndTriggerNotification(saved);
                return saved;
            });

            return new EventResponse(
                    savedEvent.getId(),
                    savedEvent.getTitle(),
                    savedEvent.getDescription(),
                    s3FileStorageService.toFullUrl(bannerUrl),
                    savedEvent.getStatus(),
                    canonicalUrl + savedEvent.getId(),
                    savedEvent.getStartsAt(),
                    savedEvent.getEndsAt(),
                    savedEvent.getCreatedAt(),
                    savedEvent.getUpdatedAt()
            );
        } catch (Exception e) {
            s3FileStorageService.delete(bannerUrl);
            throw e;
        }
    }

    public EventResponse update(Long id, UpdateEventRequest request) {

        Event initialEvent = getEvent(id);
        if (initialEvent.getStatus() == EventStatus.ARCHIVED) {
            throw new InvalidEventStatusException("Нельзя редактировать архивированное мероприятие");
        }

        boolean hasNewFile = request.file() != null && !request.file().isEmpty();
        String newBannerUrl = hasNewFile
                ? s3FileStorageService.upload(request.file(), "banners")
                : null;

        final String[] oldBannerUrlHolder = new String[1];

        try {
            Event updatedEvent = transactionTemplate.execute(_ -> {
                Event event = getEvent(id);

                if (event.getStatus() == EventStatus.ARCHIVED) {
                    throw new InvalidEventStatusException("Нельзя редактировать архивированное мероприятие");
                }

                oldBannerUrlHolder[0] = event.getBannerUrl();

                if (request.title() != null) event.setTitle(request.title());
                if (request.description() != null) event.setDescription(request.description());
                if (request.status() != null) event.setStatus(request.status());
                if (request.startsAt() != null) event.setStartsAt(request.startsAt());
                if (request.endsAt() != null) event.setEndsAt(request.endsAt());

                if (newBannerUrl != null) {
                    event.setBannerUrl(newBannerUrl);
                }

                checkAndTriggerNotification(event);
                return eventRepository.save(event);
            });

            if (newBannerUrl != null && oldBannerUrlHolder[0] != null) {
                s3FileStorageService.delete(oldBannerUrlHolder[0]);
            }

            return new EventResponse(
                    updatedEvent.getId(),
                    updatedEvent.getTitle(),
                    updatedEvent.getDescription(),
                    s3FileStorageService.toFullUrl(updatedEvent.getBannerUrl()),
                    updatedEvent.getStatus(),
                    canonicalUrl + updatedEvent.getId(),
                    updatedEvent.getStartsAt(),
                    updatedEvent.getEndsAt(),
                    updatedEvent.getCreatedAt(),
                    updatedEvent.getUpdatedAt()
            );

        } catch (Exception e) {
            if (newBannerUrl != null) {
                s3FileStorageService.delete(newBannerUrl);
            }
            throw e;
        }
    }

    public void delete(Long id) {

        Event event = getEvent(id);

        String bannerKey = event.getBannerUrl();

        eventRepository.delete(event);

        if (bannerKey != null && !bannerKey.isBlank()) {
            s3FileStorageService.delete(bannerKey);
        }
    }

    @Transactional(readOnly = true)
    public EventPageResponse getUpcomingEvents(int page, int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 50);

        Pageable pageable = PageRequest.of(safePage, safeSize);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Page<Event> result =
                eventRepository.findUpcomingPublishedEventsPage(now, pageable);

        List<EventListItemResponse> items = result.getContent()
                .stream()
                .map(event -> new EventListItemResponse(
                        event.getId(),
                        event.getTitle(),
                        s3FileStorageService.toFullUrl(event.getBannerUrl()),
                        event.getStartsAt()
                ))
                .toList();

        return new EventPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id) {

        Event event = eventRepository.findById(id)
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(() -> new EventNotFoundException("Event not found, id: " + id));

        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                s3FileStorageService.toFullUrl(event.getBannerUrl()),
                event.getStatus(),
                canonicalUrl + event.getId(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    public void publishEvent(Long id) {

        Event event = getEvent(id);

        switch (event.getStatus()) {
            case PUBLISHED -> throw new InvalidEventStatusException("Мероприятие уже опубликовано");
            case ARCHIVED -> throw new InvalidEventStatusException("Нельзя опубликовать архивированное мероприятие");
            case DRAFT -> transactionTemplate.executeWithoutResult(_ -> {
                event.setStatus(EventStatus.PUBLISHED);
                eventRepository.save(event);
                checkAndTriggerNotification(event);
            });
        }
    }

    @Transactional(readOnly = true)
    public List<AdminEventResponse> getAdminEvents() {
        return eventRepository.findAll().stream()
                .map(this::mapToAdminEventResponse)
                .toList();
    }

    private Event getEvent(Long id) {
        return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException("Event not found, id: " + id));
    }

    private void checkAndTriggerNotification(Event event) {

        if (event.getStatus() != EventStatus.PUBLISHED || event.isNotificationSent()) {
            return;
        }

        int updated = eventRepository.markNotifiedIfNotAlready(event.getId());

        if (updated == 1) {
            event.setNotificationSent(true);
            eventPublisher.publishEvent(new EventPublishedEvent(event.getId(), event.getTitle()));
            log.info("Запланирована отправка push-уведомления для мероприятия id={}", event.getId());
        } else {
            log.info("Уведомление для мероприятия id={} уже было отправлено параллельно, пропуск", event.getId());
        }
    }

    private AdminEventResponse mapToAdminEventResponse(Event event) {
        return new AdminEventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                s3FileStorageService.toFullUrl(event.getBannerUrl()),
                event.getStatus(),
                canonicalUrl + event.getId(),
                event.isNotificationSent(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}