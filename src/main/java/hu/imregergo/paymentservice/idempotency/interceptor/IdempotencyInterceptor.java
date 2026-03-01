package hu.imregergo.paymentservice.idempotency.interceptor;

import hu.imregergo.paymentservice.idempotency.annotation.IdempotentEndpoint;
import hu.imregergo.paymentservice.idempotency.entity.IdempotencyStatus;
import hu.imregergo.paymentservice.idempotency.exception.IdempotentItemExists;
import hu.imregergo.paymentservice.idempotency.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.OutputStream;
import java.time.Instant;
import java.time.OffsetDateTime;

import static hu.imregergo.paymentservice.idempotency.service.IdempotencyService.IDEMPOTENCY_KEY_HEADER;

@Component
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    private final IdempotencyService idempotencyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        IdempotentEndpoint annotation = getAnnotation(handler);
        if (annotation == null) return true;

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Missing or empty X-Idempotency-Key header\"}");
            return false;
        }

        int expireSeconds = annotation.expireSeconds();
        Instant expiresAt = Instant.now().plusSeconds(expireSeconds);

        try{
            idempotencyService.create(idempotencyKey, expiresAt);
            return true;
        } catch (IdempotentItemExists e) {
            if(e.getStatus() == IdempotencyStatus.PROCESSING) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("{\"error\": \"Duplicate idempotency key\"}");
                return false;
            }
            response.setStatus(e.getResponseStatus() != null ? e.getResponseStatus() : HttpServletResponse.SC_OK);
            response.getWriter().write(e.getResponseJson());

            return false;
        }
    }

    private IdempotentEndpoint getAnnotation(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }

        IdempotentEndpoint methodAnnotation = handlerMethod.getMethodAnnotation(IdempotentEndpoint.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return handlerMethod.getBeanType().getAnnotation(IdempotentEndpoint.class);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        IdempotentEndpoint annotation = getAnnotation(handler);
        if (annotation == null) return;

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        if (ex != null || response.getStatus() >= 400) {
            idempotencyService.fail(idempotencyKey);
        }
    }

}