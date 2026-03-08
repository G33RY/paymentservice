package hu.imregergo.paymentservice.common.exception;

import hu.imregergo.paymentservice.account.exception.AccountNotFoundException;
import hu.imregergo.paymentservice.transfer.exception.ExchangeRateApiException;
import hu.imregergo.paymentservice.transfer.exception.NotEnoughBalanceException;
import hu.imregergo.paymentservice.transfer.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            OptimisticLockingFailureException.class
    })
    public ApiError handlePessimisticLockException() {
        return new ApiError("Conflict occurred while processing the request. Please try again.");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiError handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        LOG.error("Data integrity violation", ex.getMostSpecificCause());
        return new ApiError("Data integrity violation");
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiError handleGenericException(Exception ex) {
        LOG.error("Unexpected error", ex);
        return new ApiError("An unexpected error occurred");
    }

    @ExceptionHandler({AccountNotFoundException.class, NotEnoughBalanceException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(ExchangeRateApiException.class)
    public ResponseEntity<ApiError> handleExchangeRateApiException(ExchangeRateApiException ex) {
        return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiError(ex.getMessage()));
    }
}
