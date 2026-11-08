package com.ahogek.cttserver.common.config;

import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.AdditionalPathsMapper;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.health.DataRedisHealthIndicator;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.List;
import java.util.Set;

/**
 * Adapts the readiness probe to the traffic-routing meaning of its dependencies.
 *
 * <p>Spring Boot's default aggregator reports a failed dependency as {@code DOWN}. A readiness
 * group maps {@code DOWN} or {@code UNKNOWN} to {@code OUT_OF_SERVICE} ("do not send traffic");
 * liveness keeps the default aggregation because it must not depend on the database or Redis.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-09
 */
@Configuration(proxyBeanMethods = false)
public class HealthProbeConfig {

    private static final String READINESS = "readiness";

    @Bean
    static HealthEndpointGroupsPostProcessor readinessStatusPostProcessor() {
        return ReadinessGroupsDecorator::new;
    }

    /**
     * Supplies the Redis contributor required by the readiness group.
     *
     * <p>Readiness names {@code redis}; a membership string without a registered contributor would
     * validate but never query Redis.
     */
    @Bean
    @ConditionalOnMissingBean(name = {"redisHealthIndicator", "redisHealthContributor"})
    HealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
        return new DataRedisHealthIndicator(connectionFactory);
    }

    private record ReadinessGroupsDecorator(HealthEndpointGroups delegate)
            implements HealthEndpointGroups, AdditionalPathsMapper {

        @Override
        public HealthEndpointGroup getPrimary() {
            return delegate.getPrimary();
        }

        @Override
        public Set<String> getNames() {
            return delegate.getNames();
        }

        @Override
        public List<String> getAdditionalPaths(
                EndpointId endpointId, WebServerNamespace namespace) {
            if (delegate instanceof AdditionalPathsMapper mapper) {
                return mapper.getAdditionalPaths(endpointId, namespace);
            }
            return List.of();
        }

        @Override
        public HealthEndpointGroup get(String name) {
            HealthEndpointGroup group = delegate.get(name);
            if (!READINESS.equals(name) || group == null) {
                return group;
            }
            return new OutOfServiceReadinessGroup(group);
        }
    }

    private record OutOfServiceReadinessGroup(HealthEndpointGroup delegate)
            implements HealthEndpointGroup {

        @Override
        public boolean isMember(String name) {
            return delegate.isMember(name);
        }

        @Override
        public boolean showComponents(SecurityContext securityContext) {
            return delegate.showComponents(securityContext);
        }

        @Override
        public boolean showDetails(SecurityContext securityContext) {
            return delegate.showDetails(securityContext);
        }

        @Override
        public StatusAggregator getStatusAggregator() {
            StatusAggregator aggregator = delegate.getStatusAggregator();
            return statuses -> {
                if (statuses.stream()
                        .anyMatch(
                                status ->
                                        Status.DOWN.equals(status)
                                                || Status.UNKNOWN.equals(status))) {
                    return Status.OUT_OF_SERVICE;
                }
                return aggregator.getAggregateStatus(statuses);
            };
        }

        @Override
        public HttpCodeStatusMapper getHttpCodeStatusMapper() {
            return delegate.getHttpCodeStatusMapper();
        }

        @Override
        public AdditionalHealthEndpointPath getAdditionalPath() {
            return delegate.getAdditionalPath();
        }
    }
}
