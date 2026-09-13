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

public class EventWorker {
}
