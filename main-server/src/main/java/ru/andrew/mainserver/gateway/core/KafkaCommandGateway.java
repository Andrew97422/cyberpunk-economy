package ru.andrew.mainserver.gateway.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyFuture;
import org.springframework.web.server.ResponseStatusException;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Generic helper that sends a {@link ServiceCommand} to a per-service commands topic
 * and awaits a {@link ServiceReply} on the corresponding replies topic.
 *
 * One instance per downstream worker service (banking, accounts, ...).
 */
@Slf4j
public class KafkaCommandGateway {

    private final ReplyingKafkaTemplate<String, String, String> replyingTemplate;
    private final String commandsTopic;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final String downstreamName;

    public KafkaCommandGateway(ReplyingKafkaTemplate<String, String, String> replyingTemplate,
                               String commandsTopic,
                               String downstreamName,
                               ObjectMapper objectMapper,
                               Duration timeout) {
        this.replyingTemplate = replyingTemplate;
        this.commandsTopic = commandsTopic;
        this.downstreamName = downstreamName;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
    }

    public <T> T send(AuthenticatedUser actor, String commandType, Object payload, Class<T> resultType) {
        return send(actor, commandType, null, payload, resultType);
    }

    public <T> T send(AuthenticatedUser actor, String commandType, String idempotencyKey,
                      Object payload, Class<T> resultType) {

        Map<String, Object> payloadMap = payload == null
                ? Map.of()
                : objectMapper.convertValue(payload, Map.class);

        ServiceCommand command = new ServiceCommand(
                commandType,
                actor == null ? null : actor.getAccountId(),
                actor == null ? null : actor.getRole(),
                idempotencyKey,
                payloadMap
        );

        ServiceReply reply = exchange(command);
        if (!reply.success()) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(reply.status() == 0 ? 502 : reply.status()),
                    reply.errorMessage() == null ? "Upstream error" : reply.errorMessage()
            );
        }
        if (resultType == Void.class || reply.result() == null) {
            return null;
        }
        return objectMapper.convertValue(reply.result(), resultType);
    }

    private ServiceReply exchange(ServiceCommand command) {
        try {
            String correlationKey = UUID.randomUUID().toString();
            String json = objectMapper.writeValueAsString(command);
            ProducerRecord<String, String> record = new ProducerRecord<>(commandsTopic, correlationKey, json);

            RequestReplyFuture<String, String, String> future = replyingTemplate.sendAndReceive(record);
            String replyJson = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS).value();
            return objectMapper.readValue(replyJson, ServiceReply.class);
        } catch (TimeoutException ex) {
            log.error("{} command {} timed out", downstreamName, command.commandType(), ex);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    downstreamName + " did not reply in time");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("{} command {} failed", downstreamName, command.commandType(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    downstreamName + " unavailable");
        }
    }
}
