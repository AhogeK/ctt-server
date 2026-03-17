package com.ahogek.cttserver.common.util;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Utility for resolving Spring Expression Language (SpEL) expressions.
 *
 * <p>Extracts dynamic values from method arguments using parameter names. Shared between rate
 * limiting and idempotency aspects to avoid code duplication.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Component
public class SpelExpressionResolver {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer =
            new DefaultParameterNameDiscoverer();

    /**
     * Resolves SpEL expression to extract dynamic value from method arguments.
     *
     * @param joinPoint the proceeding join point
     * @param signature the method signature
     * @param expression the SpEL expression
     * @return the resolved value as string, or null if expression is empty or null
     */
    public String resolve(
            ProceedingJoinPoint joinPoint, MethodSignature signature, String expression) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }

        String[] paramNames = nameDiscoverer.getParameterNames(signature.getMethod());
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        Object value = parser.parseExpression(expression).getValue(context);
        return value != null ? value.toString() : null;
    }
}
