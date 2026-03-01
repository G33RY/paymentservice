package hu.imregergo.paymentservice.eventsystem.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.imregergo.paymentservice.eventsystem.dto.Event;
import hu.imregergo.paymentservice.eventsystem.entity.OutboxMessage;
import hu.imregergo.paymentservice.eventsystem.entity.OutboxMessageStatus;
import hu.imregergo.paymentservice.eventsystem.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {
    private final static Logger LOG = LoggerFactory.getLogger(EventService.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <ET extends Event> void newEvent(ET event) throws JsonProcessingException {
        OutboxMessage msg = new OutboxMessage();
        msg.setStatus(OutboxMessageStatus.PENDING);
        msg.setPayload(objectMapper.writeValueAsString(event));
        outboxRepository.save(msg);
    }


    private void publishMessages(List<OutboxMessage> msg) {
        // Here I would use some message queue but for now I just LOG the messages
        LOG.info("Publishing message: {}", msg);
    }


    @Transactional
    public void publishOutbox() {
        List<OutboxMessage> messages = outboxRepository.findLast100PendingAndLock();
        LOG.info("Publishing to outbox {} messages", messages.size());
        if(messages.isEmpty()) {
            return;
        }

        List<Long> ids = messages.stream().map(OutboxMessage::getId).toList();
        try {
            publishMessages(messages);
            outboxRepository.updateStateByIds(ids, OutboxMessageStatus.PUBLISHED);
        } catch (Exception e) {
            LOG.error("Error while publishing messages", e);
            outboxRepository.updateStateByIds(ids, OutboxMessageStatus.FAILED);
            return;
        }


    }

}
