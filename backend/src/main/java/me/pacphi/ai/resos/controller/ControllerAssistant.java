package me.pacphi.ai.resos.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

class ControllerAssistant {

    // Binary operators (require a value)
    private static final List<String> BINARY_OPERATORS = List.of(
            "=",
            "!=",
            ">",
            ">=",
            "<",
            "<=",
            "LIKE"
    );

    // Unary operators (no value needed)
    private static final List<String> UNARY_OPERATORS = List.of(
            "IS NOT NULL",  // Must be before IS NULL for regex matching
            "IS NULL"
    );

    static Pageable createPageable(Integer limit, Integer skip, String sort) {
        // Calculate page number
        int page = skip / limit;

        // Default to unsorted
        Sort sortBy = Sort.unsorted();
        if (sort != null && !sort.isEmpty()) {
            String[] parts = sort.split(":"); // split on colon, e.g., name:asc
            if (parts.length == 2) {
                String field = parts[0];
                String direction = parts[1].toLowerCase();

                if ("asc".equals(direction)) {
                    sortBy = Sort.by(field).ascending();
                } else if ("desc".equals(direction)) {
                    sortBy = Sort.by(field).descending();
                } else {
                    throw new IllegalArgumentException("Invalid sort direction: " + direction);
                }
            } else {
                throw new IllegalArgumentException("Invalid sort format: " + sort);
            }
        }

        return PageRequest.of(page, limit, sortBy);
    }

    static String sanitizeCustomQuery(String customQuery, List<String> allowedFields) {
        if (customQuery.length() > 500) {
            throw new IllegalArgumentException("Custom query is too long.");
        }

        // Field pattern
        String fieldPattern = "(" + String.join("|", allowedFields) + ")";

        // Binary condition: field OPERATOR value (e.g., rating >= 3)
        String binaryOperatorPattern = "(" + String.join("|", BINARY_OPERATORS) + ")";
        String valuePattern = "('.*?'|\\d+|NULL)";
        String binaryCondition = fieldPattern + "\\s*" + binaryOperatorPattern + "\\s*" + valuePattern;

        // Unary condition: field IS NULL or field IS NOT NULL
        String unaryOperatorPattern = "(" + String.join("|", UNARY_OPERATORS) + ")";
        String unaryCondition = fieldPattern + "\\s+" + unaryOperatorPattern;

        // A condition can be either binary or unary
        String conditionPattern = "(" + binaryCondition + "|" + unaryCondition + ")";

        // Allow single condition OR two conditions joined by AND/OR
        var sanityCheckRegex =
                "^\\s*" + conditionPattern +
                "(\\s+(AND|OR)\\s+" + conditionPattern + ")?\\s*$";

        if (!customQuery.matches(sanityCheckRegex)) {
            throw new IllegalArgumentException("Invalid custom query: " + customQuery);
        }

        if (!customQuery.matches("^[a-zA-Z0-9\\s><=\\.\\'\\%_\\-]+$")) {
            throw new IllegalArgumentException("Custom query contains invalid characters.");
        }

        return customQuery;
    }
}
