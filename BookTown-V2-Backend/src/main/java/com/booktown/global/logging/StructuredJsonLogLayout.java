package com.booktown.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class StructuredJsonLogLayout extends LayoutBase<ILoggingEvent> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        payload.put("level", event.getLevel().toString());
        payload.put("logger", event.getLoggerName());
        payload.put("thread", event.getThreadName());
        payload.put("trace_id", event.getMDCPropertyMap().get(TraceIdFilter.TRACE_ID_MDC_KEY));
        payload.put("message", SensitiveDataMasker.mask(event.getFormattedMessage()));
        payload.put("exception", throwableClass(event.getThrowableProxy()));

        try {
            return OBJECT_MAPPER.writeValueAsString(payload) + System.lineSeparator();
        } catch (JsonProcessingException e) {
            return "{\"level\":\"ERROR\",\"message\":\"failed to render structured log\"}" + System.lineSeparator();
        }
    }

    private String throwableClass(IThrowableProxy throwableProxy) {
        return throwableProxy == null ? null : throwableProxy.getClassName();
    }
}
