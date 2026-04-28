package com.ahogek.cttserver.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing configuration enabling automatic audit field population.
 *
 * <p>This configuration enables JPA auditing which automatically populates {@code @CreatedBy},
 * {@code @LastModifiedBy}, {@code @CreatedDate}, and {@code @LastModifiedDate} fields on entity
 * classes.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 0.1.0
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
