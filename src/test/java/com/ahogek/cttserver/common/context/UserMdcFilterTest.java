package com.ahogek.cttserver.common.context;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.user.enums.UserStatus;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("User MDC filter")
class UserMdcFilterTest {

    private static final UUID USER_ID = UUID.fromString("3f8b1c2d-4e5f-4a6b-8c9d-0e1f2a3b4c5d");
    private static final String EMAIL = "mdclog@example.com";

    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final UserMdcFilter filter = new UserMdcFilter(providerOf(currentUserProvider));

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void shouldPutTheUserId_whenTheRequestIsAuthenticated() throws Exception {
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(currentUser()));

        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                assertingMdc(USER_ID.toString()));

        assertThat(MDC.get(MdcKey.USER_ID)).isEqualTo(USER_ID.toString());
    }

    @Test
    void shouldKeepTheEmail_outOfTheLoggingContext_whenTheRequestIsAuthenticated()
            throws Exception {
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(currentUser()));
        AtomicReference<Map<String, String>> applied = new AtomicReference<>();

        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> applied.set(MDC.getCopyOfContextMap()));

        assertThat(applied.get()).containsEntry(MdcKey.USER_ID, USER_ID.toString());
        assertThat(applied.get().values()).noneMatch(value -> value.contains(EMAIL));
    }

    @Test
    void shouldLeaveTheContextUntouched_whenTheRequestIsAnonymous() throws Exception {
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

        filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), assertingMdcAbsent());

        assertThat(MDC.get(MdcKey.USER_ID)).isNull();
    }

    @Test
    void shouldWriteNothing_whenNoPrincipalSourceIsRegistered() throws Exception {
        UserMdcFilter withoutProvider = new UserMdcFilter(withoutPrincipalProvider());

        withoutProvider.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), assertingMdcAbsent());

        assertThat(MDC.get(MdcKey.USER_ID)).isNull();
    }

    private CurrentUser currentUser() {
        return new CurrentUser(
                USER_ID,
                EMAIL,
                UserStatus.ACTIVE,
                Set.of("SCOPE_READ"),
                CurrentUser.AuthenticationType.WEB_SESSION);
    }

    private static ObjectProvider<CurrentUserProvider> providerOf(
            CurrentUserProvider currentUserProvider) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("currentUserProvider", currentUserProvider);
        return beans.getBeanProvider(CurrentUserProvider.class);
    }

    private static ObjectProvider<CurrentUserProvider> withoutPrincipalProvider() {
        return new StaticListableBeanFactory().getBeanProvider(CurrentUserProvider.class);
    }

    private static FilterChain assertingMdc(String expected) {
        return (request, response) -> assertThat(MDC.get(MdcKey.USER_ID)).isEqualTo(expected);
    }

    private static FilterChain assertingMdcAbsent() {
        return (request, response) -> assertThat(MDC.get(MdcKey.USER_ID)).isNull();
    }
}
