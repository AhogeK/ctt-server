package com.ahogek.cttserver.common.config.logging;

import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;

/**
 * JSON encoder that applies the same masking rules as {@link MaskingMessageConverter}.
 *
 * <p>The production profile logs JSON, where the pattern based converter never runs: {@link
 * JsonEncoder} writes event fields directly instead of walking a pattern. This encoder masks the
 * formatted message and the string values of key/value pairs, then delegates to the standard
 * encoder, so the third line of defense covers production output too.
 *
 * <p>An event needs masking when its formatted text or one of its key/value values changes. Such an
 * event is re-published with the masked formatted text as {@code message}, with {@code arguments}
 * removed (an argument cannot be told apart from data, and the formatted text already contains it)
 * and with masked key/value values. Events that need no masking are delegated untouched, so their
 * JSON schema is unchanged; a masked event differs only by the absent {@code arguments} field and
 * by carrying rendered text instead of the template.
 *
 * <p>Residual gap: MDC properties and throwable messages are forwarded unchanged.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-08
 */
public class MaskingJsonEncoder extends JsonEncoder {

    @Override
    public byte[] encode(ILoggingEvent event) {
        String formatted = event.getFormattedMessage();
        String maskedFormatted = MaskingMessageConverter.mask(formatted);
        boolean textMasked = !same(formatted, maskedFormatted);
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        List<KeyValuePair> maskedPairs = maskKeyValuePairs(pairs);

        if (!textMasked && maskedPairs == pairs) {
            return super.encode(event);
        }

        return super.encode(new MaskedEvent(event, textMasked, maskedFormatted, maskedPairs));
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    /**
     * Masks string values of key/value pairs, which {@link JsonEncoder} serialises as its own field
     * and a pattern based converter never sees (for example {@code query_string}).
     *
     * @param pairs key/value pairs of the event, may be {@code null}
     * @return the supplied list when nothing changed, otherwise a masked copy
     */
    private static List<KeyValuePair> maskKeyValuePairs(List<KeyValuePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return pairs;
        }

        List<KeyValuePair> masked = null;
        for (int i = 0; i < pairs.size(); i++) {
            KeyValuePair pair = pairs.get(i);
            if (pair == null || !(pair.value instanceof CharSequence text)) {
                if (masked != null) {
                    masked.add(pair);
                }
                continue;
            }
            String value = text.toString();
            String replacement = MaskingMessageConverter.mask(value);
            if (same(value, replacement)) {
                if (masked != null) {
                    masked.add(pair);
                }
                continue;
            }
            if (masked == null) {
                masked = new ArrayList<>(pairs.subList(0, i));
            }
            masked.add(new KeyValuePair(pair.key, replacement));
        }

        return masked == null ? pairs : Collections.unmodifiableList(masked);
    }

    /** Read only view of an event whose text and key/value fields have been masked. */
    private static final class MaskedEvent implements ILoggingEvent {

        private final ILoggingEvent delegate;
        private final boolean textMasked;
        private final String maskedFormattedMessage;
        private final List<KeyValuePair> keyValuePairs;

        private MaskedEvent(
                ILoggingEvent delegate,
                boolean textMasked,
                String maskedFormattedMessage,
                List<KeyValuePair> keyValuePairs) {
            this.delegate = delegate;
            this.textMasked = textMasked;
            this.maskedFormattedMessage = maskedFormattedMessage;
            this.keyValuePairs = keyValuePairs;
        }

        @Override
        public String getThreadName() {
            return delegate.getThreadName();
        }

        @Override
        public Level getLevel() {
            return delegate.getLevel();
        }

        @Override
        public String getMessage() {
            return textMasked ? maskedFormattedMessage : delegate.getMessage();
        }

        @Override
        public Object[] getArgumentArray() {
            return textMasked ? null : delegate.getArgumentArray();
        }

        @Override
        public String getFormattedMessage() {
            return textMasked ? maskedFormattedMessage : delegate.getFormattedMessage();
        }

        @Override
        public String getLoggerName() {
            return delegate.getLoggerName();
        }

        @Override
        public LoggerContextVO getLoggerContextVO() {
            return delegate.getLoggerContextVO();
        }

        @Override
        public IThrowableProxy getThrowableProxy() {
            return delegate.getThrowableProxy();
        }

        @Override
        public StackTraceElement[] getCallerData() {
            return delegate.getCallerData();
        }

        @Override
        public boolean hasCallerData() {
            return delegate.hasCallerData();
        }

        @Override
        public List<Marker> getMarkerList() {
            return delegate.getMarkerList();
        }

        @Override
        public Map<String, String> getMDCPropertyMap() {
            return delegate.getMDCPropertyMap();
        }

        @Override
        public Map<String, String> getMdc() {
            return delegate.getMdc();
        }

        @Override
        public long getTimeStamp() {
            return delegate.getTimeStamp();
        }

        @Override
        public int getNanoseconds() {
            return delegate.getNanoseconds();
        }

        @Override
        public long getSequenceNumber() {
            return delegate.getSequenceNumber();
        }

        @Override
        public List<KeyValuePair> getKeyValuePairs() {
            return keyValuePairs;
        }

        @Override
        public void prepareForDeferredProcessing() {
            delegate.prepareForDeferredProcessing();
        }
    }
}
