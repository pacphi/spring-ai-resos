package me.pacphi.ai.resos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import me.pacphi.ai.resos.jdbc.CustomerEntity;
import me.pacphi.ai.resos.model.Customer;
import me.pacphi.ai.resos.repository.CustomerRepository;
import me.pacphi.ai.resos.repository.PageableCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/resos")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final PageableCustomerRepository pageableCustomerRepository;
    private final JdbcTemplate jdbcTemplate;

    public CustomerController(
            CustomerRepository customerRepository,
            PageableCustomerRepository pageableCustomerRepository,
            JdbcTemplate jdbcTemplate) {
        this.customerRepository = customerRepository;
        this.pageableCustomerRepository = pageableCustomerRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Define the allowed fields. Must match actual database column names (snake_case).
    private static final List<String> ALLOWED_FIELDS = List.of(
            "name_01",
            "email",
            "phone",
            "created_at",
            "last_booking_at",
            "booking_count",
            "total_spent",
            "metadata"
    );

    @GetMapping("/customers")
    public ResponseEntity<List<Customer>> customersGet(
            @Max(100)
            @Parameter(name = "limit", description = "Number of records to return (max 100)", in = ParameterIn.QUERY)
            @Valid @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,

            @Parameter(name = "skip", description = "Number of records to skip", in = ParameterIn.QUERY)
            @Valid @RequestParam(value = "skip", required = false, defaultValue = "0") Integer skip,

            @Parameter(name = "sort", description = "Sort field and direction (field:direction, e.g., name:asc, createdAt:desc)", in = ParameterIn.QUERY)
            @Valid @RequestParam(value = "sort", required = false) String sort,

            @Parameter(name = "customQuery", description = "Search expression for filtering results", in = ParameterIn.QUERY)
            @Valid @RequestParam(value = "customQuery", required = false) String customQuery
    ) {
        // Build Pageable
        Pageable pageable = ControllerAssistant.createPageable(limit, skip, sort);

        // Fetch Data based on customQuery
        Page<CustomerEntity> customerEntitiesPage;
        if (customQuery != null && !customQuery.trim().isEmpty()) {
            try {
                String sanitizedQuery = ControllerAssistant.sanitizeCustomQuery(customQuery, ALLOWED_FIELDS);
                // Use the repository method with custom query
                // The findByCustomQuery method needs to use pageable even when getting a single item
                pageable = PageRequest.of(0, limit, pageable.getSort());  // Reset the page number for single item retrieval.

                customerEntitiesPage = pageableCustomerRepository.findByCustomQuery(sanitizedQuery, pageable, jdbcTemplate);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }

        } else {
            // Use the repository method for retrieving all customers with pagination and sorting
            customerEntitiesPage = pageableCustomerRepository.findAll(pageable);
        }

        // Convert to DTOs
        List<Customer> customers = customerEntitiesPage.getContent().stream()
                .map(CustomerEntity::toPojo)
                .collect(Collectors.toList());

        // Return the Result
        return new ResponseEntity<>(customers, HttpStatus.OK);
    }

    /**
     * GET /customers/{id} : Get customer
     * Retrieve a specific customer by ID
     *
     * @param id  (required)
     * @return Customer details (status code 200)
     *         or Customer not found (status code 404)
     */
    @Operation(
            operationId = "customersIdGet",
            summary = "Get customer",
            description = "Retrieve a specific customer by ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Customer details", content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = Customer.class))
                    }),
                    @ApiResponse(responseCode = "404", description = "Customer not found")
            },
            security = {
                    @SecurityRequirement(name = "basicAuth")
            }
    )
    @GetMapping(
            value = "/customers/{id}",
            produces = { "application/json" }
    )
    public ResponseEntity<Customer> customersIdGet(
            @Parameter(name = "id", description = "", required = true, in = ParameterIn.PATH)
            @PathVariable("id") String id
    ) {
        try {
            UUID uuid = UUID.fromString(id);
            Optional<CustomerEntity> optionalEntity = customerRepository.findById(uuid);
            return optionalEntity
                    .map(customerEntity -> ResponseEntity.ok(customerEntity.toPojo()))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

}