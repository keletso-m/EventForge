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
    private static final String DLQ_URL =
            "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/events-dlq";

    private final SqsClient sqsClient;
    private final EventRepository eventRepository;

    public EventWorker(SqsClient sqsClient, EventRepository eventRepository) {
        this.sqsClient = sqsClient;
        this.eventRepository = eventRepository;
    }

    // Polls the main queue, normal processing + retries.
    // SQS itself decides when a message has failed too many times and
    // moves it to the DLQ
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

    // Polls the dead-letter queue, events that SQS gave up retrying.
    // only failied event
    @Scheduled(fixedDelay = 5000)
    public void pollDeadLetterQueue() {
        List<Message> messages = sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                        .queueUrl(DLQ_URL)
                        .maxNumberOfMessages(5)
                        .waitTimeSeconds(2)
                        .build()
        ).messages();

        for (Message message : messages) {
            UUID eventId = UUID.fromString(message.body());

            eventRepository.findById(eventId).ifPresent(event -> {
                event.setStatus(EventStatus.FAILED);
                eventRepository.save(event);
                System.out.println("Event " + eventId + " landed in DLQ, marked FAILED");
            });

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(DLQ_URL)
                    .receiptHandle(message.receiptHandle())
                    .build());
        }
    }

    private void processMessage(Message message) {
        UUID eventId = UUID.fromString(message.body());

        eventRepository.findById(eventId).ifPresent(event -> {

            // idempotency guard
            if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.FAILED) {
                System.out.println("Skipping already-processed event: " + eventId + " (status: " + event.getStatus() + ")");
                deleteFromQueue(message);
                return;
            }

            try {
                event.setStatus(EventStatus.PROCESSING);
                eventRepository.save(event);

                System.out.println("Processing event: " + event.getId() + " (" + event.getType() + ") - attempt " + (event.getAttemptCount() + 1));

                if ("test.failure".equals(event.getType())) {
                    throw new RuntimeException("Simulated processing failure");
                }

                event.setStatus(EventStatus.COMPLETED);
                eventRepository.save(event);

                deleteFromQueue(message);

            } catch (Exception e) {
                int attempts = event.getAttemptCount() + 1;
                event.setAttemptCount(attempts);
                event.setStatus(EventStatus.RETRYING);
                eventRepository.save(event);

                System.out.println("Failed to process event " + eventId + " (attempt " + attempts + "): " + e.getMessage());
                // Message intentionally NOT deleted. SQS's redrive policy owns

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