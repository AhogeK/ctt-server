package com.ahogek.cttserver.common.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PageQueryTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void default_values_are_valid() {
        PageQuery query = new PageQuery();

        Set<ConstraintViolation<PageQuery>> violations = validator.validate(query);

        assertThat(violations).isEmpty();
        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(20);
    }

    @Test
    void valid_pagination_values_accepted() {
        PageQuery query = new PageQuery();
        query.setPage(5);
        query.setSize(50);

        Set<ConstraintViolation<PageQuery>> violations = validator.validate(query);

        assertThat(violations).isEmpty();
    }

    @Test
    void page_zero_is_invalid() {
        PageQuery query = new PageQuery();
        query.setPage(0);

        Set<ConstraintViolation<PageQuery>> violations = validator.validate(query);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("Page number must be at least 1");
    }

    @Test
    void page_negative_is_invalid() {
        PageQuery query = new PageQuery();
        query.setPage(-1);

        Set<ConstraintViolation<PageQuery>> violations = validator.validate(query);

        assertThat(violations).hasSize(1);
    }

    @Test
    void size_zero_is_invalid() {
        PageQuery query = new PageQuery();
        query.setSize(0);

        Set<ConstraintViolation<PageQuery>> violations = validator.validate(query);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("Page size must be at least 1");
    }

    @Test
    void size_over_100_is_invalid() {
        PageQuery query = new PageQuery();
        query.setSize(101);

        Set<ConstraintViolation<PageQuery>> violations = validator.validate(query);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("Page size cannot exceed 100");
    }

    @Test
    void size_100_is_valid() {
        PageQuery query = new PageQuery();
        query.setSize(100);

        Set<ConstraintViolation<PageQuery>> violations = validator.validate(query);

        assertThat(violations).isEmpty();
    }

    @Test
    void null_page_is_invalid() {
        PageQuery query = new PageQuery();
        query.setPage(null);

        Set<ConstraintViolation<PageQuery>> violations = validator.validate(query);

        assertThat(violations).hasSize(1);
    }

    @Test
    void null_size_is_invalid() {
        PageQuery query = new PageQuery();
        query.setSize(null);

        Set<ConstraintViolation<PageQuery>> violations = validator.validate(query);

        assertThat(violations).hasSize(1);
    }

    @Test
    void offset_calculation_is_correct() {
        PageQuery query = new PageQuery();
        query.setPage(3);
        query.setSize(25);

        assertThat(query.getOffset()).isEqualTo(50); // (3-1) * 25 = 50
    }

    @Test
    void offset_for_first_page_is_zero() {
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setSize(20);

        assertThat(query.getOffset()).isZero();
    }
}
