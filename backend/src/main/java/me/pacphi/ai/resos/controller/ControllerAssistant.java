package me.pacphi.ai.resos.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

class ControllerAssistant {

    // Define allowed operators. Add more as needed.
    private static final List<String> ALLOWED_OPERATORS = List.of(
            "=",
            "!=",
            ">",
            ">=",
            "<",
            "<=",
            "LIKE",
            "IS NULL",
            "IS NOT NULL"
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

        var sanityCheckRegex =
                "^\\s*(" +
                        String.join("|", allowedFields) +
                        ")\\s*(" +
                        String.join("|", ALLOWED_OPERATORS) +
                        ")\\s*('.*?'|\\d+|NULL)\\s*(AND|OR)?\\s*(" +
                        String.join("|", allowedFields) + ")\\s*(" +
                        String.join("|", ALLOWED_OPERATORS) +
                        ")\\s*('.*?'|\\d+|NULL)\\s*$";

        if (!customQuery.matches(sanityCheckRegex)) {
            throw new IllegalArgumentException("Invalid custom query: " + customQuery);
        }

        if (!customQuery.matches("^[a-zA-Z0-9\\s><=\\.\\'\\%_\\-]+$")) {
            throw new IllegalArgumentException("Custom query contains invalid characters.");
        }

        return customQuery;
    }
}
