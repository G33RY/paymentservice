package hu.imregergo.paymentservice.eventsystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.imregergo.paymentservice.eventsystem.entity.OutboxMessage;
import hu.imregergo.paymentservice.eventsystem.entity.OutboxMessageStatus;
import hu.imregergo.paymentservice.eventsystem.repository.OutboxRepository;
import hu.imregergo.paymentservice.eventsystem.service.EventService;
import hu.imregergo.paymentservice.transfer.dto.NewTransferEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    private EventService eventService;

    @BeforeEach
    public void setUp() {
        eventService = new EventService(outboxRepository, objectMapper);
    }


    // ========== NEW EVENT TESTS ==========

    @Test
    void testNewEvent_success_createsOutboxMessageWithPendingStatus() throws JsonProcessingException {
        NewTransferEvent event = new NewTransferEvent();
        event.setFromAccountId(1L);
        event.setToAccountId(2L);
        event.setAmount(new BigDecimal("100.00"));

        String expectedJson = "{\"id\":\"" + event.getId() + "\",\"fromAccountId\":1,\"toAccountId\":2,\"amount\":100.00}";

        when(objectMapper.writeValueAsString(event)).thenReturn(expectedJson);
        when(outboxRepository.save(any(OutboxMessage.class))).thenAnswer(invocation -> {
            OutboxMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            return msg;
        });

        // Execute
        eventService.newEvent(event);

        // Verify
        ArgumentCaptor<OutboxMessage> messageCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(messageCaptor.capture());

        OutboxMessage savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getStatus(), is(OutboxMessageStatus.PENDING));
        assertThat(savedMessage.getPayload(), is(expectedJson));
    }

    @Test
    void testNewEvent_success_serializesEventToJson() throws JsonProcessingException {
        NewTransferEvent event = new NewTransferEvent();
        event.setFromAccountId(5L);
        event.setToAccountId(10L);
        event.setAmount(new BigDecimal("250.50"));

        String expectedJson = "{\"fromAccountId\":5,\"toAccountId\":10,\"amount\":250.50}";

        when(objectMapper.writeValueAsString(event)).thenReturn(expectedJson);
        when(outboxRepository.save(any(OutboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Execute
        eventService.newEvent(event);

        // Verify ObjectMapper was called with the event
        verify(objectMapper, times(1)).writeValueAsString(event);

        // Verify payload matches serialized JSON
        ArgumentCaptor<OutboxMessage> messageCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(messageCaptor.capture());

        assertThat(messageCaptor.getValue().getPayload(), is(expectedJson));
    }

    @Test
    void testNewEvent_jsonProcessingException_wrappedInRuntimeException() throws JsonProcessingException {
        NewTransferEvent event = new NewTransferEvent();
        event.setFromAccountId(1L);
        event.setToAccountId(2L);
        event.setAmount(new BigDecimal("100.00"));

        when(objectMapper.writeValueAsString(event))
                .thenThrow(new JsonProcessingException("Serialization failed") {
                });

        // Execute & Verify
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> eventService.newEvent(event)
        );

        assertThat(exception.getMessage(), containsString("Failed to serialize outbox event"));
        assertThat(exception.getCause().getMessage(), containsString("Serialization failed"));

        // Verify repository was never called
        verify(outboxRepository, never()).save(any(OutboxMessage.class));
    }

    @Test
    void testNewEvent_multipleEvents_createsMultipleOutboxMessages() throws JsonProcessingException {
        NewTransferEvent event1 = new NewTransferEvent();
        event1.setFromAccountId(1L);
        event1.setToAccountId(2L);
        event1.setAmount(new BigDecimal("100.00"));

        NewTransferEvent event2 = new NewTransferEvent();
        event2.setFromAccountId(3L);
        event2.setToAccountId(4L);
        event2.setAmount(new BigDecimal("200.00"));

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxRepository.save(any(OutboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Execute
        eventService.newEvent(event1);
        eventService.newEvent(event2);

        // Verify
        verify(outboxRepository, times(2)).save(any(OutboxMessage.class));
        verify(objectMapper, times(2)).writeValueAsString(any());
    }

    @Test
    void testNewEvent_complexEvent_handlesCorrectly() throws JsonProcessingException {
        NewTransferEvent event = new NewTransferEvent();
        event.setFromAccountId(999L);
        event.setToAccountId(1000L);
        event.setAmount(new BigDecimal("9999999.9999"));
        event.setRate(new BigDecimal("0.8567"));

        String complexJson = "{\"id\":\"" + event.getId() + "\",\"fromAccountId\":999,\"toAccountId\":1000,\"amount\":9999999.9999,\"rate\":0.8567}";

        when(objectMapper.writeValueAsString(event)).thenReturn(complexJson);
        when(outboxRepository.save(any(OutboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Execute
        eventService.newEvent(event);

        // Verify
        ArgumentCaptor<OutboxMessage> messageCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(messageCaptor.capture());

        OutboxMessage savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getPayload(), containsString("9999999.9999"));
        assertThat(savedMessage.getPayload(), containsString("0.8567"));
    }


    // ========== PUBLISH OUTBOX TESTS - SUCCESS SCENARIOS ==========

    @Test
    void testPublishOutbox_success_fetchesTop100PendingMessagesAndLocks() {
        List<OutboxMessage> messages = createOutboxMessages(10);

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);
        doNothing().when(outboxRepository).updateStateByIds(any(), any());

        // Execute
        eventService.publishOutbox();

        // Verify
        verify(outboxRepository, times(1)).findLast100PendingAndLock();
    }

    @Test
    void testPublishOutbox_success_updatesStatusToPublished() {
        List<OutboxMessage> messages = createOutboxMessages(5);
        List<Long> expectedIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);
        doNothing().when(outboxRepository).updateStateByIds(any(), any());

        // Execute
        eventService.publishOutbox();

        // Verify
        verify(outboxRepository, times(1)).updateStateByIds(expectedIds, OutboxMessageStatus.PUBLISHED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPublishOutbox_success_batchOf100Messages() {
        List<OutboxMessage> messages = createOutboxMessages(100);
        List<Long> expectedIds = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            expectedIds.add(i);
        }

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);
        doNothing().when(outboxRepository).updateStateByIds(any(), any());

        // Execute
        eventService.publishOutbox();

        // Verify
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).updateStateByIds(idsCaptor.capture(), eq(OutboxMessageStatus.PUBLISHED));

        assertThat(idsCaptor.getValue(), hasSize(100));
        assertThat(idsCaptor.getValue(), contains(expectedIds.toArray()));
    }

    @Test
    void testPublishOutbox_success_singleMessage() {
        List<OutboxMessage> messages = createOutboxMessages(1);

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);
        doNothing().when(outboxRepository).updateStateByIds(any(), any());

        // Execute
        eventService.publishOutbox();

        // Verify
        verify(outboxRepository).updateStateByIds(Collections.singletonList(1L), OutboxMessageStatus.PUBLISHED);
    }

    @Test
    void testPublishOutbox_success_correctOrderOfOperations() {
        List<OutboxMessage> messages = createOutboxMessages(10);

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);
        doNothing().when(outboxRepository).updateStateByIds(any(), any());

        // Execute
        eventService.publishOutbox();

        // Verify order: 1. fetch messages, 2. update status
        var inOrder = inOrder(outboxRepository);
        inOrder.verify(outboxRepository).findLast100PendingAndLock();
        inOrder.verify(outboxRepository).updateStateByIds(any(), eq(OutboxMessageStatus.PUBLISHED));
    }


    // ========== PUBLISH OUTBOX TESTS - EMPTY RESULTS ==========

    @Test
    void testPublishOutbox_emptyList_doesNotUpdateStatus() {
        when(outboxRepository.findLast100PendingAndLock()).thenReturn(Collections.emptyList());

        // Execute
        eventService.publishOutbox();

        // Verify
        verify(outboxRepository, times(1)).findLast100PendingAndLock();
        verify(outboxRepository, never()).updateStateByIds(any(), any());
    }

    @Test
    void testPublishOutbox_emptyList_returnsEarly() {
        when(outboxRepository.findLast100PendingAndLock()).thenReturn(Collections.emptyList());

        // Execute
        eventService.publishOutbox();

        // Verify no further operations after finding empty list
        verify(outboxRepository, times(1)).findLast100PendingAndLock();
        verifyNoMoreInteractions(outboxRepository);
    }


    // ========== PUBLISH OUTBOX TESTS - FAILURE SCENARIOS ==========

    @Test
    void testPublishOutbox_publishingFails_updatesStatusToFailed() {
        List<OutboxMessage> messages = createOutboxMessages(5);
        List<Long> expectedIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);

        // Simulate exception during state update (after publishing attempt)
        doThrow(new RuntimeException("Database connection lost"))
                .doNothing()
                .when(outboxRepository).updateStateByIds(any(), any());

        // Execute
        eventService.publishOutbox();

        // Verify status updated to FAILED
        verify(outboxRepository, times(1)).updateStateByIds(expectedIds, OutboxMessageStatus.FAILED);
    }

    @Test
    void testPublishOutbox_exceptionDuringPublish_marksAsFailed() {
        List<OutboxMessage> messages = createOutboxMessages(3);
        List<Long> expectedIds = Arrays.asList(1L, 2L, 3L);

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);

        // First call throws exception (simulating publish failure), second call succeeds (marking as failed)
        doThrow(new RuntimeException("Queue unavailable"))
                .doNothing()
                .when(outboxRepository).updateStateByIds(any(), any());

        // Execute
        eventService.publishOutbox();

        // Verify
        verify(outboxRepository, times(1)).updateStateByIds(expectedIds, OutboxMessageStatus.FAILED);
    }

    @Test
    void testPublishOutbox_failureRecovery_doesNotThrowException() {
        List<OutboxMessage> messages = createOutboxMessages(5);

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);
        doThrow(new RuntimeException("Network error"))
                .doNothing()
                .when(outboxRepository).updateStateByIds(any(), any());

        // Execute - should not throw exception
        assertDoesNotThrow(() -> eventService.publishOutbox());

        // Verify it attempted to mark as failed
        verify(outboxRepository, atLeastOnce()).updateStateByIds(any(), eq(OutboxMessageStatus.FAILED));
    }

    @Test
    void testPublishOutbox_multipleFailures_handlesGracefully() {
        List<OutboxMessage> messages = createOutboxMessages(10);

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);
        doThrow(new RuntimeException("Error 1"))
                .doNothing()
                .when(outboxRepository).updateStateByIds(any(), any());

        // Execute first time
        eventService.publishOutbox();

        // Verify failed status set
        verify(outboxRepository, atLeastOnce()).updateStateByIds(any(), eq(OutboxMessageStatus.FAILED));

        // Execute second time with different messages
        List<OutboxMessage> newMessages = createOutboxMessages(5, 100L);
        when(outboxRepository.findLast100PendingAndLock()).thenReturn(newMessages);

        eventService.publishOutbox();

        // Verify repository called multiple times
        verify(outboxRepository, atLeast(2)).findLast100PendingAndLock();
    }


    // ========== PUBLISH OUTBOX TESTS - EDGE CASES ==========

    @Test
    void testPublishOutbox_exactlyOneMessage_handlesCorrectly() {
        OutboxMessage message = new OutboxMessage();
        message.setId(42L);
        message.setStatus(OutboxMessageStatus.PENDING);
        message.setPayload("{\"test\":\"data\"}");

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(Collections.singletonList(message));
        doNothing().when(outboxRepository).updateStateByIds(any(), any());

        // Execute
        eventService.publishOutbox();

        // Verify
        verify(outboxRepository).updateStateByIds(Collections.singletonList(42L), OutboxMessageStatus.PUBLISHED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPublishOutbox_99Messages_processesBatch() {
        List<OutboxMessage> messages = createOutboxMessages(99);

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);
        doNothing().when(outboxRepository).updateStateByIds(any(), any());

        // Execute
        eventService.publishOutbox();

        // Verify
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).updateStateByIds(idsCaptor.capture(), eq(OutboxMessageStatus.PUBLISHED));

        assertThat(idsCaptor.getValue(), hasSize(99));
    }

    @Test
    void testPublishOutbox_messagesWithComplexPayload_handlesCorrectly() {
        OutboxMessage message = new OutboxMessage();
        message.setId(1L);
        message.setStatus(OutboxMessageStatus.PENDING);
        message.setPayload("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":9999999.9999,\"rate\":0.8567,\"metadata\":{\"key\":\"value\"}}");

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(Collections.singletonList(message));
        doNothing().when(outboxRepository).updateStateByIds(any(), any());

        // Execute
        eventService.publishOutbox();

        // Verify processing completes successfully
        verify(outboxRepository).updateStateByIds(Collections.singletonList(1L), OutboxMessageStatus.PUBLISHED);
    }


    // ========== INTEGRATION TESTS ==========

    @Test
    void testFullWorkflow_createEventThenPublish() throws JsonProcessingException {
        // Create event
        NewTransferEvent event = new NewTransferEvent();
        event.setFromAccountId(1L);
        event.setToAccountId(2L);
        event.setAmount(new BigDecimal("100.00"));

        String eventJson = "{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":100.00}";

        when(objectMapper.writeValueAsString(event)).thenReturn(eventJson);
        when(outboxRepository.save(any(OutboxMessage.class))).thenAnswer(invocation -> {
            OutboxMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            return msg;
        });

        // Execute newEvent
        eventService.newEvent(event);

        // Verify outbox message created
        verify(outboxRepository).save(any(OutboxMessage.class));

        // Now simulate publishing
        OutboxMessage savedMessage = new OutboxMessage();
        savedMessage.setId(1L);
        savedMessage.setStatus(OutboxMessageStatus.PENDING);
        savedMessage.setPayload(eventJson);

        when(outboxRepository.findLast100PendingAndLock()).thenReturn(Collections.singletonList(savedMessage));
        doNothing().when(outboxRepository).updateStateByIds(any(), any());

        // Execute publishOutbox
        eventService.publishOutbox();

        // Verify published
        verify(outboxRepository).updateStateByIds(Collections.singletonList(1L), OutboxMessageStatus.PUBLISHED);
    }

    @Test
    void testMultipleEventsPublishedInBatch() throws JsonProcessingException {
        // Create multiple events
        for (int i = 0; i < 5; i++) {
            NewTransferEvent event = new NewTransferEvent();
            event.setFromAccountId((long) i);
            event.setToAccountId((long) (i + 1));
            event.setAmount(new BigDecimal("100.00"));

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(outboxRepository.save(any(OutboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

            eventService.newEvent(event);
        }

        // Verify 5 messages created
        verify(outboxRepository, times(5)).save(any(OutboxMessage.class));

        // Simulate batch publish
        List<OutboxMessage> messages = createOutboxMessages(5);
        when(outboxRepository.findLast100PendingAndLock()).thenReturn(messages);
        doNothing().when(outboxRepository).updateStateByIds(any(), any());

        eventService.publishOutbox();

        // Verify batch published
        verify(outboxRepository).updateStateByIds(Arrays.asList(1L, 2L, 3L, 4L, 5L), OutboxMessageStatus.PUBLISHED);
    }


    // ========== HELPER METHODS ==========

    private List<OutboxMessage> createOutboxMessages(int count) {
        return createOutboxMessages(count, 1L);
    }

    private List<OutboxMessage> createOutboxMessages(int count, long startId) {
        List<OutboxMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OutboxMessage message = new OutboxMessage();
            message.setId(startId + i);
            message.setStatus(OutboxMessageStatus.PENDING);
            message.setPayload("{\"test\":\"data" + i + "\"}");
            messages.add(message);
        }
        return messages;
    }
}
