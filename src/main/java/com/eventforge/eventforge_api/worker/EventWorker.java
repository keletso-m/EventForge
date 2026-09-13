package com.eventforge.eventforge_api.worker;
import com.eventforge.eventforge_api.event.Event;
import com.eventforge.eventforge_api.event.EventRepository;
import com.eventforge.eventforge_api.event.EventStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.UUID;

@Component
public class EventWorker {

    private static final String QUEUE_URL =
            "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/events-queue";

    private final SqsClient sqsClient;
    private final EventRepository eventRepository;

    public EventWorker(SqsClient sqsClient, EventRepository eventRepository) {
        this.sqsClient = sqsClient;
        this.eventRepository = eventRepository;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollQueue() {
        List<Message> messages = sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                        .queueUrl(QUEUE_URL)
                        .maxNumberOfMessages(5)
                        .waitTimeSeconds(2)
                        .build()
        ).messages();

        for (Message message : messages) {
            processMessage(message);
        }
    }

    private void processMessage(Message message) {
        UUID eventId = UUID.fromString(message.body());

        eventRepository.findById(eventId).ifPresent(event -> {
            try {
                event.setStatus(EventStatus.PROCESSING);
                eventRepository.save(event);

                // Simulated work
                System.out.println("Processing event: " + event.getId() + " (" + event.getType() + ")");

                event.setStatus(EventStatus.COMPLETED);
                eventRepository.save(event);

                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(QUEUE_URL)
                        .receiptHandle(message.receiptHandle())
                        .build());

            } catch (Exception e) {
                System.out.println("Failed to process event " + eventId + ": " + e.getMessage());
                // Message is NOT deleted here, it'll reappear in the queue after visibility timeout,
            }
        });
    }
}
