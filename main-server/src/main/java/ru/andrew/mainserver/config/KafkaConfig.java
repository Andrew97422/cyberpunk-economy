package ru.andrew.mainserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import ru.andrew.mainserver.gateway.core.KafkaCommandGateway;
import ru.andrew.mainserver.gateway.core.ReplyingKafkaTemplateFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    public static final String BANKING_COMMANDS_TOPIC = "banking.commands.v1";
    public static final String BANKING_REPLIES_TOPIC = "banking.replies.v1";
    public static final String ACCOUNTS_COMMANDS_TOPIC = "accounts.commands.v1";
    public static final String ACCOUNTS_REPLIES_TOPIC = "accounts.replies.v1";
    public static final String ACCESS_COMMANDS_TOPIC = "access.commands.v1";
    public static final String ACCESS_REPLIES_TOPIC = "access.replies.v1";
    public static final String AUDIT_COMMANDS_TOPIC = "audit.commands.v1";
    public static final String AUDIT_REPLIES_TOPIC = "audit.replies.v1";
    public static final String AUTH_EVENTS_TOPIC = "auth.events";
    public static final String CARDS_COMMANDS_TOPIC = "cards.commands.v1";
    public static final String CARDS_REPLIES_TOPIC = "cards.replies.v1";
    public static final String TERMINALS_COMMANDS_TOPIC = "terminals.commands.v1";
    public static final String TERMINALS_REPLIES_TOPIC = "terminals.replies.v1";
    public static final String MARKETPLACE_COMMANDS_TOPIC = "marketplace.commands.v1";
    public static final String MARKETPLACE_REPLIES_TOPIC = "marketplace.replies.v1";
    public static final String NEWS_COMMANDS_TOPIC = "news.commands.v1";
    public static final String NEWS_REPLIES_TOPIC = "news.replies.v1";
    public static final String ANALYTICS_COMMANDS_TOPIC = "analytics.commands.v1";
    public static final String ANALYTICS_REPLIES_TOPIC = "analytics.replies.v1";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.audit-events}")
    private String auditTopic;

    @Value("${app.kafka.topics.domain-events}")
    private String domainTopic;

    @Value("${app.kafka.topics.security-events}")
    private String securityTopic;

    @Value("${app.kafka.topics.account-events}")
    private String accountEventsTopic;

    @Value("${app.kafka.topics.banking-events}")
    private String bankingEventsTopic;

    /**
     * Plain fire-and-forget producer template for gateway-only auth events
     * ({@code auth.events}). Marked {@link Primary} so it is unambiguously injected
     * where a generic {@code KafkaTemplate} is needed (e.g. AuthEventPublisher) —
     * the per-service {@link ReplyingKafkaTemplate} beans are injected by their
     * narrower type/name and are unaffected.
     */
    @Bean
    @Primary
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic auditTopic() {
        return TopicBuilder.name(auditTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic domainTopic() {
        return TopicBuilder.name(domainTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic securityTopic() {
        return TopicBuilder.name(securityTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic accountEventsTopic() {
        return TopicBuilder.name(accountEventsTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic bankingEventsTopic() {
        return TopicBuilder.name(bankingEventsTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic bankingCommandsTopic() {
        return TopicBuilder.name(BANKING_COMMANDS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic bankingRepliesTopic() {
        return TopicBuilder.name(BANKING_REPLIES_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic accountsCommandsTopic() {
        return TopicBuilder.name(ACCOUNTS_COMMANDS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic accountsRepliesTopic() {
        return TopicBuilder.name(ACCOUNTS_REPLIES_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic accessCommandsTopic() {
        return TopicBuilder.name(ACCESS_COMMANDS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic accessRepliesTopic() {
        return TopicBuilder.name(ACCESS_REPLIES_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic sessionEventsTopic() {
        return TopicBuilder.name("session.events").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic auditCommandsTopic() {
        return TopicBuilder.name(AUDIT_COMMANDS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic auditRepliesTopic() {
        return TopicBuilder.name(AUDIT_REPLIES_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic authEventsTopic() {
        return TopicBuilder.name(AUTH_EVENTS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic cardsCommandsTopic() {
        return TopicBuilder.name(CARDS_COMMANDS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic cardsRepliesTopic() {
        return TopicBuilder.name(CARDS_REPLIES_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic cardEventsTopic() {
        return TopicBuilder.name("card.events").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic terminalsCommandsTopic() {
        return TopicBuilder.name(TERMINALS_COMMANDS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic terminalsRepliesTopic() {
        return TopicBuilder.name(TERMINALS_REPLIES_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic terminalEventsTopic() {
        return TopicBuilder.name("terminal.events").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic marketplaceCommandsTopic() {
        return TopicBuilder.name(MARKETPLACE_COMMANDS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic marketplaceRepliesTopic() {
        return TopicBuilder.name(MARKETPLACE_REPLIES_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic marketplaceEventsTopic() {
        return TopicBuilder.name("marketplace.events").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic newsCommandsTopic() {
        return TopicBuilder.name(NEWS_COMMANDS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic newsRepliesTopic() {
        return TopicBuilder.name(NEWS_REPLIES_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic newsEventsTopic() {
        return TopicBuilder.name("news.events").partitions(1).replicas(1).build();
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> bankingReplyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return ReplyingKafkaTemplateFactory.create(
                producerFactory, bootstrapServers,
                BANKING_REPLIES_TOPIC,
                "main-server-banking-replies",
                Duration.ofSeconds(10));
    }

    @Bean
    public KafkaCommandGateway bankingCommandGateway(
            ReplyingKafkaTemplate<String, String, String> bankingReplyingKafkaTemplate,
            ObjectMapper objectMapper) {
        return new KafkaCommandGateway(
                bankingReplyingKafkaTemplate,
                BANKING_COMMANDS_TOPIC,
                "banking-service",
                objectMapper,
                Duration.ofSeconds(10));
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> accountsReplyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return ReplyingKafkaTemplateFactory.create(
                producerFactory, bootstrapServers,
                ACCOUNTS_REPLIES_TOPIC,
                "main-server-accounts-replies",
                Duration.ofSeconds(10));
    }

    @Bean
    public KafkaCommandGateway accountsCommandGateway(
            ReplyingKafkaTemplate<String, String, String> accountsReplyingKafkaTemplate,
            ObjectMapper objectMapper) {
        return new KafkaCommandGateway(
                accountsReplyingKafkaTemplate,
                ACCOUNTS_COMMANDS_TOPIC,
                "account-service",
                objectMapper,
                Duration.ofSeconds(10));
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> accessReplyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return ReplyingKafkaTemplateFactory.create(
                producerFactory, bootstrapServers,
                ACCESS_REPLIES_TOPIC,
                "main-server-access-replies",
                Duration.ofSeconds(10));
    }

    @Bean
    public KafkaCommandGateway accessCommandGateway(
            ReplyingKafkaTemplate<String, String, String> accessReplyingKafkaTemplate,
            ObjectMapper objectMapper) {
        return new KafkaCommandGateway(
                accessReplyingKafkaTemplate,
                ACCESS_COMMANDS_TOPIC,
                "access-service",
                objectMapper,
                Duration.ofSeconds(10));
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> auditReplyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return ReplyingKafkaTemplateFactory.create(
                producerFactory, bootstrapServers,
                AUDIT_REPLIES_TOPIC,
                "main-server-audit-replies",
                Duration.ofSeconds(10));
    }

    @Bean
    public KafkaCommandGateway auditCommandGateway(
            ReplyingKafkaTemplate<String, String, String> auditReplyingKafkaTemplate,
            ObjectMapper objectMapper) {
        return new KafkaCommandGateway(
                auditReplyingKafkaTemplate,
                AUDIT_COMMANDS_TOPIC,
                "audit-service",
                objectMapper,
                Duration.ofSeconds(10));
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> cardsReplyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return ReplyingKafkaTemplateFactory.create(
                producerFactory, bootstrapServers,
                CARDS_REPLIES_TOPIC,
                "main-server-cards-replies",
                Duration.ofSeconds(10));
    }

    @Bean
    public KafkaCommandGateway cardsCommandGateway(
            ReplyingKafkaTemplate<String, String, String> cardsReplyingKafkaTemplate,
            ObjectMapper objectMapper) {
        return new KafkaCommandGateway(
                cardsReplyingKafkaTemplate,
                CARDS_COMMANDS_TOPIC,
                "card-service",
                objectMapper,
                Duration.ofSeconds(10));
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> terminalsReplyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return ReplyingKafkaTemplateFactory.create(
                producerFactory, bootstrapServers,
                TERMINALS_REPLIES_TOPIC,
                "main-server-terminals-replies",
                Duration.ofSeconds(10));
    }

    @Bean
    public KafkaCommandGateway terminalsCommandGateway(
            ReplyingKafkaTemplate<String, String, String> terminalsReplyingKafkaTemplate,
            ObjectMapper objectMapper) {
        return new KafkaCommandGateway(
                terminalsReplyingKafkaTemplate,
                TERMINALS_COMMANDS_TOPIC,
                "terminal-service",
                objectMapper,
                Duration.ofSeconds(10));
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> marketplaceReplyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return ReplyingKafkaTemplateFactory.create(
                producerFactory, bootstrapServers,
                MARKETPLACE_REPLIES_TOPIC,
                "main-server-marketplace-replies",
                Duration.ofSeconds(10));
    }

    @Bean
    public KafkaCommandGateway marketplaceCommandGateway(
            ReplyingKafkaTemplate<String, String, String> marketplaceReplyingKafkaTemplate,
            ObjectMapper objectMapper) {
        return new KafkaCommandGateway(
                marketplaceReplyingKafkaTemplate,
                MARKETPLACE_COMMANDS_TOPIC,
                "marketplace-service",
                objectMapper,
                Duration.ofSeconds(10));
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> newsReplyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return ReplyingKafkaTemplateFactory.create(
                producerFactory, bootstrapServers,
                NEWS_REPLIES_TOPIC,
                "main-server-news-replies",
                Duration.ofSeconds(10));
    }

    @Bean
    public KafkaCommandGateway newsCommandGateway(
            ReplyingKafkaTemplate<String, String, String> newsReplyingKafkaTemplate,
            ObjectMapper objectMapper) {
        return new KafkaCommandGateway(
                newsReplyingKafkaTemplate,
                NEWS_COMMANDS_TOPIC,
                "news-service",
                objectMapper,
                Duration.ofSeconds(10));
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> analyticsReplyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return ReplyingKafkaTemplateFactory.create(
                producerFactory, bootstrapServers,
                ANALYTICS_REPLIES_TOPIC,
                "main-server-analytics-replies",
                Duration.ofSeconds(10));
    }

    @Bean
    public KafkaCommandGateway analyticsCommandGateway(
            ReplyingKafkaTemplate<String, String, String> analyticsReplyingKafkaTemplate,
            ObjectMapper objectMapper) {
        return new KafkaCommandGateway(
                analyticsReplyingKafkaTemplate,
                ANALYTICS_COMMANDS_TOPIC,
                "analytics-service",
                objectMapper,
                Duration.ofSeconds(10));
    }
}
