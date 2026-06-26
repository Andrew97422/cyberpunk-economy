package ru.andrew.newsservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.andrew.newsservice.entity.NewsPost;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsEventPublisher {

    private static final String TOPIC = "news.events";
    private static final String AGGREGATE_TYPE = "NEWS";
    private static final String SOURCE = "news-service";
    private static final String VERSION = "v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishCreated(NewsPost post) { publish(post, "news.created"); }
    public void publishUpdated(NewsPost post) { publish(post, "news.updated"); }
    public void publishPublished(NewsPost post) { publish(post, "news.published"); }
    public void publishArchived(NewsPost post) { publish(post, "news.archived"); }

    private void publish(NewsPost post, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("newsId", post.getId());
        payload.put("title", post.getTitle());
        payload.put("category", post.getCategory());
        payload.put("pinned", post.isPinned());
        payload.put("status", post.getStatus().name());
        payload.put("authorAccountId", post.getAuthorAccountId());
        payload.put("authorPublicName", post.getAuthorPublicName());

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("aggregateType", AGGREGATE_TYPE);
        envelope.put("aggregateId", post.getId().toString());
        envelope.put("source", SOURCE);
        envelope.put("version", VERSION);
        envelope.put("occurredAt", LocalDateTime.now().toString());
        envelope.put("payload", payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(TOPIC, post.getId().toString(), json);
            log.info("Published {} for newsId={}", eventType, post.getId());
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize news event {}", eventType, ex);
            throw new IllegalStateException("Cannot serialize news event", ex);
        }
    }
}
