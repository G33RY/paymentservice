package hu.imregergo.paymentservice.idempotency;


import hu.imregergo.paymentservice.idempotency.entity.IdempotencyStatus;
import hu.imregergo.paymentservice.idempotency.entity.IdempotentItem;
import hu.imregergo.paymentservice.idempotency.exception.IdempotentItemExists;
import hu.imregergo.paymentservice.idempotency.repository.IdempotentItemRepository;
import hu.imregergo.paymentservice.idempotency.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IdempotencyServiceTest {

    @Mock
    private IdempotentItemRepository idempotentItemRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private IdempotencyService idempotencyService;

    @BeforeEach
    public void setUp() {
        idempotencyService = new IdempotencyService(idempotentItemRepository, transactionTemplate);
    }


    @Test
    void testCreate_success_createsIdempotentItemWithCorrectKeyAndStatus() {
        String key = "test-key-123";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        // Mock transactionTemplate to execute the callback
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        doNothing().when(idempotentItemRepository).insert(eq(key), eq(IdempotencyStatus.PROCESSING), eq(expiresAt));

        // Execute
        idempotencyService.create(key, expiresAt);

        // Verify
        verify(transactionTemplate, times(1)).execute(any());
        verify(idempotentItemRepository, times(1)).insert(key, IdempotencyStatus.PROCESSING, expiresAt);
    }

    @Test
    void testCreate_withDifferentKey_createsWithCorrectKey() {
        String key = "unique-key-456";
        Instant expiresAt = Instant.now().plusSeconds(7200);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        doNothing().when(idempotentItemRepository).insert(eq(key), eq(IdempotencyStatus.PROCESSING), eq(expiresAt));

        idempotencyService.create(key, expiresAt);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IdempotencyStatus> statusCaptor = ArgumentCaptor.forClass(IdempotencyStatus.class);
        ArgumentCaptor<Instant> expiresAtCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(idempotentItemRepository).insert(keyCaptor.capture(), statusCaptor.capture(), expiresAtCaptor.capture());

        assertThat(keyCaptor.getValue(), is(key));
        assertThat(statusCaptor.getValue(), is(IdempotencyStatus.PROCESSING));
        assertThat(expiresAtCaptor.getValue(), is(expiresAt));
    }

    @Test
    void testCreate_duplicate_throwsIdempotentItemExistsWithCorrectData() {
        String key = "duplicate-key";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        // Simulate duplicate key error
        when(transactionTemplate.execute(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        // Mock existing item
        IdempotentItem existingItem = new IdempotentItem();
        existingItem.setId(key);
        existingItem.setStatus(IdempotencyStatus.PROCESSING);
        existingItem.setResponseStatus(null);
        existingItem.setResponseJson(null);

        when(idempotentItemRepository.findById(key)).thenReturn(Optional.of(existingItem));

        // Execute and verify exception
        IdempotentItemExists exception = assertThrows(
                IdempotentItemExists.class,
                () -> idempotencyService.create(key, expiresAt)
        );

        assertThat(exception.getKey(), is(key));
        assertThat(exception.getStatus(), is(IdempotencyStatus.PROCESSING));
        assertThat(exception.getResponseStatus(), nullValue());
        assertThat(exception.getResponseJson(), nullValue());

        verify(idempotentItemRepository, times(1)).findById(key);
    }

    @Test
    void testCreate_duplicateWithCompletedStatus_throwsExceptionWithResponseData() {
        String key = "completed-duplicate-key";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        when(transactionTemplate.execute(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        // Mock existing completed item with response data
        IdempotentItem existingItem = new IdempotentItem();
        existingItem.setId(key);
        existingItem.setStatus(IdempotencyStatus.COMPLETED);
        existingItem.setResponseStatus(200);
        existingItem.setResponseJson("{\"result\":\"success\"}");

        when(idempotentItemRepository.findById(key)).thenReturn(Optional.of(existingItem));

        IdempotentItemExists exception = assertThrows(
                IdempotentItemExists.class,
                () -> idempotencyService.create(key, expiresAt)
        );

        assertThat(exception.getKey(), is(key));
        assertThat(exception.getStatus(), is(IdempotencyStatus.COMPLETED));
        assertThat(exception.getResponseStatus(), is(200));
        assertThat(exception.getResponseJson(), is("{\"result\":\"success\"}"));
    }

    @Test
    void testCreate_transactionExceptionButItemNotFound_rethrowsException() {
        String key = "error-key";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        when(transactionTemplate.execute(any()))
                .thenThrow(new DataIntegrityViolationException("Some other error"));

        when(idempotentItemRepository.findById(key)).thenReturn(Optional.empty());

        assertThrows(
                DataIntegrityViolationException.class,
                () -> idempotencyService.create(key, expiresAt)
        );

        verify(idempotentItemRepository, times(1)).findById(key);
    }


    @Test
    void testComplete_existingItem_updatesStatusAndResponseData() {
        String key = "complete-key";
        String responseJson = "{\"status\":\"success\",\"amount\":100}";
        Integer responseStatus = 200;

        IdempotentItem item = new IdempotentItem();
        item.setId(key);
        item.setStatus(IdempotencyStatus.PROCESSING);
        item.setResponseJson(null);
        item.setResponseStatus(null);

        when(idempotentItemRepository.findById(key)).thenReturn(Optional.of(item));
        when(idempotentItemRepository.save(any(IdempotentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        idempotencyService.complete(key, responseJson, responseStatus);

        ArgumentCaptor<IdempotentItem> itemCaptor = ArgumentCaptor.forClass(IdempotentItem.class);
        verify(idempotentItemRepository).save(itemCaptor.capture());

        IdempotentItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.getStatus(), is(IdempotencyStatus.COMPLETED));
        assertThat(savedItem.getResponseJson(), is(responseJson));
        assertThat(savedItem.getResponseStatus(), is(responseStatus));
    }

    @Test
    void testComplete_withDifferentResponseStatus_savesCorrectStatus() {
        String key = "complete-key-2";
        String responseJson = "{\"error\":\"Not Found\"}";
        Integer responseStatus = 404;

        IdempotentItem item = new IdempotentItem();
        item.setId(key);
        item.setStatus(IdempotencyStatus.PROCESSING);

        when(idempotentItemRepository.findById(key)).thenReturn(Optional.of(item));
        when(idempotentItemRepository.save(any(IdempotentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        idempotencyService.complete(key, responseJson, responseStatus);

        ArgumentCaptor<IdempotentItem> itemCaptor = ArgumentCaptor.forClass(IdempotentItem.class);
        verify(idempotentItemRepository).save(itemCaptor.capture());

        IdempotentItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.getResponseStatus(), is(404));
        assertThat(savedItem.getResponseJson(), is(responseJson));
    }

    @Test
    void testComplete_itemNotFound_doesNothing() {
        String key = "non-existent-key";
        String responseJson = "{\"data\":\"test\"}";
        Integer responseStatus = 200;

        when(idempotentItemRepository.findById(key)).thenReturn(Optional.empty());

        idempotencyService.complete(key, responseJson, responseStatus);

        verify(idempotentItemRepository, times(1)).findById(key);
        verify(idempotentItemRepository, never()).save(any(IdempotentItem.class));
    }

    @Test
    void testComplete_nullResponseJson_savesNull() {
        String key = "null-response-key";
        String responseJson = null;
        Integer responseStatus = 204;

        IdempotentItem item = new IdempotentItem();
        item.setId(key);
        item.setStatus(IdempotencyStatus.PROCESSING);

        when(idempotentItemRepository.findById(key)).thenReturn(Optional.of(item));
        when(idempotentItemRepository.save(any(IdempotentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        idempotencyService.complete(key, responseJson, responseStatus);

        ArgumentCaptor<IdempotentItem> itemCaptor = ArgumentCaptor.forClass(IdempotentItem.class);
        verify(idempotentItemRepository).save(itemCaptor.capture());

        IdempotentItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.getResponseJson(), nullValue());
        assertThat(savedItem.getResponseStatus(), is(204));
        assertThat(savedItem.getStatus(), is(IdempotencyStatus.COMPLETED));
    }


    @Test
    void testFail_existingItem_deletesItem() {
        String key = "fail-key";

        doNothing().when(idempotentItemRepository).deleteById(key);

        idempotencyService.fail(key);

        verify(idempotentItemRepository, times(1)).deleteById(key);
    }

    @Test
    void testFail_nonExistentItem_callsDeleteAnyway() {
        String key = "non-existent-fail-key";

        doNothing().when(idempotentItemRepository).deleteById(key);

        idempotencyService.fail(key);

        verify(idempotentItemRepository, times(1)).deleteById(key);
    }

    @Test
    void testFail_multipleKeys_deletesEachKey() {
        String key1 = "fail-key-1";
        String key2 = "fail-key-2";
        String key3 = "fail-key-3";

        doNothing().when(idempotentItemRepository).deleteById(anyString());

        idempotencyService.fail(key1);
        idempotencyService.fail(key2);
        idempotencyService.fail(key3);

        verify(idempotentItemRepository, times(1)).deleteById(key1);
        verify(idempotentItemRepository, times(1)).deleteById(key2);
        verify(idempotentItemRepository, times(1)).deleteById(key3);
        verify(idempotentItemRepository, times(3)).deleteById(anyString());
    }


    @Test
    void testCreateAndComplete_fullWorkflow() {
        String key = "workflow-key";
        Instant expiresAt = Instant.now().plusSeconds(3600);
        String responseJson = "{\"result\":\"completed\"}";
        Integer responseStatus = 201;

        // Create
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        doNothing().when(idempotentItemRepository).insert(eq(key), eq(IdempotencyStatus.PROCESSING), eq(expiresAt));

        idempotencyService.create(key, expiresAt);

        verify(idempotentItemRepository).insert(key, IdempotencyStatus.PROCESSING, expiresAt);

        // Complete
        IdempotentItem item = new IdempotentItem();
        item.setId(key);
        item.setStatus(IdempotencyStatus.PROCESSING);

        when(idempotentItemRepository.findById(key)).thenReturn(Optional.of(item));
        when(idempotentItemRepository.save(any(IdempotentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        idempotencyService.complete(key, responseJson, responseStatus);

        ArgumentCaptor<IdempotentItem> itemCaptor = ArgumentCaptor.forClass(IdempotentItem.class);
        verify(idempotentItemRepository).save(itemCaptor.capture());

        IdempotentItem completedItem = itemCaptor.getValue();
        assertThat(completedItem.getStatus(), is(IdempotencyStatus.COMPLETED));
        assertThat(completedItem.getResponseJson(), is(responseJson));
        assertThat(completedItem.getResponseStatus(), is(responseStatus));
    }

    @Test
    void testCreateAndFail_fullWorkflow() {
        String key = "fail-workflow-key";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        // Create
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        doNothing().when(idempotentItemRepository).insert(eq(key), eq(IdempotencyStatus.PROCESSING), eq(expiresAt));

        idempotencyService.create(key, expiresAt);

        verify(idempotentItemRepository).insert(key, IdempotencyStatus.PROCESSING, expiresAt);

        // Fail
        doNothing().when(idempotentItemRepository).deleteById(key);

        idempotencyService.fail(key);

        verify(idempotentItemRepository).deleteById(key);
    }
}
