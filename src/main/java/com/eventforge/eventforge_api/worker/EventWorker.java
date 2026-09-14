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
    private static final int MAX_ATTEMPTS = 3;
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

            // idempotency guard: if this event has already reached a terminal
            // state, skip processing entirely because this message is a duplicate
            if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.FAILED) {
                System.out.println("Skipping already-processed event: " + eventId + " (status: " + event.getStatus() + ")");
                deleteFromQueue(message); //  remove it, since it's a duplicate, not a failure
                return;
            }

            try {
                event.setStatus(EventStatus.PROCESSING);
                eventRepository.save(event);

                System.out.println("Processing event: " + event.getId() + " (" + event.getType() + ")");

                // for now( temporary): deliberately fail events of this type, to test retry behavior
                if ("test.failure".equals(event.getType())) {
                    throw new RuntimeException("Simulated processing failure");
                }

                event.setStatus(EventStatus.COMPLETED);
                eventRepository.save(event);

                deleteFromQueue(message);

            } catch (Exception e) {
                int attempts = event.getAttemptCount() + 1;
                event.setAttemptCount(attempts);
                System.out.println("Failed to process event " + eventId + ": " + e.getMessage());
                // message intentionally not deleted, SQS will redeliver after visibility timeout
            }
        });
    }

    private void deleteFromQueue(Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
