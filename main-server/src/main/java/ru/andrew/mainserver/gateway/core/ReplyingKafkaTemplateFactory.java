package ru.andrew.mainserver.gateway.core;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper that builds a {@link ReplyingKafkaTemplate} for a given commands/replies topic pair.
 * Reused by every downstream service the gateway talks to.
 */
public final class ReplyingKafkaTemplateFactory {

    private ReplyingKafkaTemplateFactory() {
    }

    public static ReplyingKafkaTemplate<String, String, String> create(
            ProducerFactory<String, String> producerFactory,
            String bootstrapServers,
            String replyTopic,
            String replyGroupId,
            Duration defaultTimeout) {

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, replyGroupId);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        ConsumerFactory<String, String> replyConsumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);

        ContainerProperties containerProps = new ContainerProperties(replyTopic);
        KafkaMessageListenerContainer<String, String> replyContainer =
                new KafkaMessageListenerContainer<>(replyConsumerFactory, containerProps);
        replyContainer.getContainerProperties().setMissingTopicsFatal(false);

        ReplyingKafkaTemplate<String, String, String> template =
                new ReplyingKafkaTemplate<>(producerFactory, replyContainer);
        template.setSharedReplyTopic(true);
        template.setDefaultReplyTimeout(defaultTimeout);
        return template;
    }
}
