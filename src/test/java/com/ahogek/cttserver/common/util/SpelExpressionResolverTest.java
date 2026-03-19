package com.ahogek.cttserver.common.util;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * SpelExpressionResolver utility tests.
 *
 * <p>Tests SpEL expression parsing for rate limiting and idempotency frameworks. Uses real Method
 * objects to verify parameter extraction.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpelExpressionResolver Utility Tests")
class SpelExpressionResolverTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private SpelExpressionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SpelExpressionResolver();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "  \t\n  "})
    @DisplayName("Should return null when expression is empty or blank")
    void shouldReturnNull_whenExpressionIsEmptyOrBlank(String expression) {
        String result = resolver.resolve(joinPoint, methodSignature, expression);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null when expression is null")
    void shouldReturnNull_whenExpressionIsNull() {
        String result = resolver.resolve(joinPoint, methodSignature, null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should resolve single string parameter")
    void shouldResolveSingleStringParameter() throws Exception {
        // Given - use real Method for parameter name discovery
        Method method = TestService.class.getMethod("singleParamMethod", String.class);
        given(methodSignature.getMethod()).willReturn(method);
        given(joinPoint.getArgs()).willReturn(new Object[]{"test@example.com"});

        // When
        String result = resolver.resolve(joinPoint, methodSignature, "#email");

        // Then
        assertThat(result).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should resolve integer parameter as string")
    void shouldResolveIntegerParameter() throws Exception {
        // Given
        Method method = TestService.class.getMethod("intParamMethod", Integer.class);
        given(methodSignature.getMethod()).willReturn(method);
        given(joinPoint.getArgs()).willReturn(new Object[]{42});

        // When
        String result = resolver.resolve(joinPoint, methodSignature, "#count");

        // Then
        assertThat(result).isEqualTo("42");
    }

    @Test
    @DisplayName("Should resolve null parameter value")
    void shouldResolveNullParameterValue() throws Exception {
        // Given
        Method method = TestService.class.getMethod("singleParamMethod", String.class);
        given(methodSignature.getMethod()).willReturn(method);
        given(joinPoint.getArgs()).willReturn(new Object[]{null});

        // When
        String result = resolver.resolve(joinPoint, methodSignature, "#email");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should resolve object property")
    void shouldResolveObjectProperty() throws Exception {
        // Given
        Method method = TestService.class.getMethod("objectParamMethod", UserDto.class);
        UserDto user = new UserDto("john", "john@example.com");
        given(methodSignature.getMethod()).willReturn(method);
        given(joinPoint.getArgs()).willReturn(new Object[]{user});

        // When
        String username = resolver.resolve(joinPoint, methodSignature, "#user.username");
        String email = resolver.resolve(joinPoint, methodSignature, "#user.email");

        // Then
        assertThat(username).isEqualTo("john");
        assertThat(email).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("Should return null when parameter name does not exist")
    void shouldReturnNull_whenParameterNameNotExist() throws Exception {
        // Given
        Method method = TestService.class.getMethod("singleParamMethod", String.class);
        given(methodSignature.getMethod()).willReturn(method);
        given(joinPoint.getArgs()).willReturn(new Object[]{"test@example.com"});

        // When
        String result = resolver.resolve(joinPoint, methodSignature, "#nonExistent");

        // Then
        assertThat(result).isNull();
    }

    // Test helper classes
    @SuppressWarnings("unused")
    public static class TestService {
        public void singleParamMethod(String email) {
        }

        public void intParamMethod(Integer count) {
        }

        public void objectParamMethod(UserDto user) {
        }
    }

    @SuppressWarnings("unused")
    public record UserDto(String username, String email) {
    }
}