package ru.andrew.bankingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.bankingservice.entity.OutboxEventEntity;
import ru.andrew.bankingservice.entity.enums.OutboxStatus;
import ru.andrew.bankingservice.repository.OutboxEventRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void addEvent(String aggregateType, String aggregateId, String eventType, Object payload) throws JsonProcessingException {
        try {
            log.info("OUTBOX start aggregateType={}, aggregateId={}, eventType={}", aggregateType, aggregateId, eventType);

            String json = objectMapper.writeValueAsString(payload);
            log.info("OUTBOX payload serialized: {}", json);

            OutboxEventEntity event = OutboxEventEntity.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(json)
                    .status(OutboxStatus.NEW)
                    .build();

            outboxEventRepository.save(event);
            log.info("OUTBOX saved event");
        } catch (Exception e) {
            log.error("OUTBOX failed aggregateType={}, aggregateId={}, eventType={}", aggregateType, aggregateId, eventType, e);
            throw e;
        }
    }
}