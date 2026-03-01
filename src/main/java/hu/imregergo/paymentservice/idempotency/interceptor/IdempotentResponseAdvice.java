package hu.imregergo.paymentservice.idempotency.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.imregergo.paymentservice.idempotency.annotation.IdempotentEndpoint;
import hu.imregergo.paymentservice.idempotency.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@Component
@RequiredArgsConstructor
public class IdempotentResponseAdvice implements ResponseBodyAdvice<Object> {
    private final static Logger LOG = LoggerFactory.getLogger(IdempotentResponseAdvice.class);
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return returnType.getMethodAnnotation(IdempotentEndpoint.class) != null ||
                returnType.getDeclaringClass().getAnnotation(IdempotentEndpoint.class) != null;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        try {
            String idempotencyKey = request.getHeaders().getFirst(IdempotencyService.IDEMPOTENCY_KEY_HEADER);
            if(!StringUtils.hasText(idempotencyKey)) {
                return body;
            }

            String responseJson = objectMapper.writeValueAsString(body);

            try {
                Integer status = null;
                if (response instanceof ServletServerHttpResponse servletResp) {
                    status = servletResp.getServletResponse().getStatus();
                }
                idempotencyService.complete(idempotencyKey, responseJson, status);
            } catch (NoSuchMethodError | UnsupportedOperationException ex) {
                idempotencyService.complete(idempotencyKey, responseJson, null);
            }

        } catch (Exception e) {
            LOG.error("Failed to cache idempotent response", e);
        }
        return body;
    }
}
