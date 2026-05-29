package com.ronkadosh.bubbleup.common.filtering;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class SpecificationBuilder {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Specification<T> build(FilterRequest filterRequest) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (SearchCriteria criteria : filterRequest.criteria()) {
                String field = criteria.field();
                Object value = criteria.value();
                Path<?> path = root.get(field);
                switch (criteria.operator()) {
                    case EQUALS -> predicates.add(cb.equal(path, value));
                    case NOT_EQUALS -> predicates.add(cb.notEqual(path, value));
                    case CONTAINS -> predicates.add(cb.like(path.as(String.class), "%" + value + "%"));
                    case STARTS_WITH -> predicates.add(cb.like(path.as(String.class), value + "%"));
                    case ENDS_WITH -> predicates.add(cb.like(path.as(String.class), "%" + value));
                    case GREATER_THAN -> predicates.add(cb.greaterThan(comparablePath(path), comparable(value)));
                    case LESS_THAN -> predicates.add(cb.lessThan(comparablePath(path), comparable(value)));
                    case GREATER_THAN_OR_EQUAL -> predicates.add(cb.greaterThanOrEqualTo(comparablePath(path), comparable(value)));
                    case LESS_THAN_OR_EQUAL -> predicates.add(cb.lessThanOrEqualTo(comparablePath(path), comparable(value)));
                    case IN -> {
                        if (!(value instanceof Collection<?> values)) {
                            throw new IllegalArgumentException(
                                    "IN operator requires a Collection value for field: " + field);
                        }
                        predicates.add(path.in(values));
                    }
                }
            }
            Predicate result = cb.conjunction();
            for (Predicate p : predicates) {
                result = cb.and(result, p);
            }
            return result;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Path<Comparable> comparablePath(Path<?> path) {
        return (Path<Comparable>) (Path) path;
    }

    private static Comparable<Object> comparable(Object value) {
        if (!(value instanceof Comparable<?>)) {
            throw new IllegalArgumentException(
                    "Comparison operator requires a Comparable value, got: " + value);
        }
        @SuppressWarnings("unchecked")
        Comparable<Object> c = (Comparable<Object>) value;
        return c;
    }
}
