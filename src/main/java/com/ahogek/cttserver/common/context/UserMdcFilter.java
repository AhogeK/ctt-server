package com.ahogek.cttserver.common.context;

import com.ahogek.cttserver.auth.CurrentUserProvider;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds the authenticated user's identifier to the logging context.
 *
 * <p>Runs inside the security chain, after authentication has resolved the principal and before the
 * terms gate, so every log line written from that point on — service logs, the access line, the
 * audit record and a terms rejection — can be filtered by user. Only the identifier is added: an
 * email would put personal data into every log line and into any log store it is shipped to.
 *
 * <p><strong>Soft dependency:</strong> the principal source is looked up per request, because
 * servlet filters are part of every web slice — a slice that imports no authentication component
 * would otherwise fail to start over a logging concern. Without a provider the filter writes
 * nothing and the line stays {@code anonymous}.
 *
 * <p><strong>Lifecycle:</strong> this filter only adds the value. {@link
 * RequestContextInitializerFilter} owns the MDC lifetime and removes it when the request completes,
 * including on failure; removing it here would drop the value before the access log line is written
 * by {@link RequestLoggingFilter}, which is the line operators filter by.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-14
 */
@Component
public final class UserMdcFilter extends OncePerRequestFilter {

    private final ObjectProvider<CurrentUserProvider> currentUserProviders;

    public UserMdcFilter(ObjectProvider<CurrentUserProvider> currentUserProviders) {
        this.currentUserProviders = currentUserProviders;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {

        CurrentUserProvider provider = currentUserProviders.getIfAvailable();
        if (provider != null) {
            provider.getCurrentUser()
                    .ifPresent(user -> MDC.put(MdcKey.USER_ID, user.id().toString()));
        }

        chain.doFilter(request, response);
    }
}
