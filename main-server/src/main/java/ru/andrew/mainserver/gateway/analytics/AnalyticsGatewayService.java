package ru.andrew.mainserver.gateway.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.gateway.core.KafkaCommandGateway;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsGatewayService {

    private final KafkaCommandGateway analyticsCommandGateway;

    public JsonNode overview(AuthenticatedUser actor) {
        return analyticsCommandGateway.send(actor, AnalyticsCommandType.OVERVIEW.name(),
                null, Map.of(), JsonNode.class);
    }
}
