package hu.imregergo.paymentservice.eventsystem.service;

import hu.imregergo.paymentservice.eventsystem.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final EventService eventService;

    // here some leader election would be required or a central scheduler
    @Scheduled(fixedRate = 2000)
    public void publishPendingMessages() {
        eventService.publishOutbox();
    }
}
