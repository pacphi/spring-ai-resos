package me.pacphi.ai.resos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import me.pacphi.ai.resos.jdbc.FeedbackEntity;
import me.pacphi.ai.resos.model.Feedback;
import me.pacphi.ai.resos.repository.FeedbackRepository;
import me.pacphi.ai.resos.repository.PageableFeedbackRepository;
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
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;
    private final PageableFeedbackRepository pageableFeedbackRepository;
    private final JdbcTemplate jdbcTemplate;
    

    public FeedbackController(
            FeedbackRepository feedbackRepository,
            PageableFeedbackRepository pageableFeedbackRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.feedbackRepository = feedbackRepository;
        this.pageableFeedbackRepository = pageableFeedbackRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Define the allowed fields.  Must match the FeedbackEntity field names.
    private static final List<String> ALLOWED_FIELDS = List.of(
            "booking_id",
            "rating",
            "comment_01",
            "createdAt",
            "customer_id",
            "isPublic"
    );

    /**
     * GET /feedback : List feedback
     * Retrieve a list of feedback
     *
     * @param limit Number of records to return (max 100) (optional, default to 100)
     * @param skip Number of records to skip (optional, default to 0)
     * @param sort Sort field and direction (1 ascending, -1 descending) (optional)
     * @param customQuery Search expression for filtering results (optional)
     * @return List of feedback (status code 200)
     */
    @Operation(
        operationId = "feedbackGet",
        summary = "List feedback",
        description = "Retrieve a list of feedback",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of feedback", content = {
                @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Feedback.class)))
            })
        },
        security = {
            @SecurityRequirement(name = "basicAuth")
        }
    )
    @GetMapping(
        value = "/feedback",
        produces = { "application/json" }
    )
    public ResponseEntity<List<Feedback>> feedbackGet(
        @Max(100)
        @Parameter(name = "limit", description = "Number of records to return (max 100)", in = ParameterIn.QUERY)
        @Valid @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,

        @Parameter(name = "skip", description = "Number of records to skip", in = ParameterIn.QUERY)
        @Valid @RequestParam(value = "skip", required = false, defaultValue = "0") Integer skip,

        @Parameter(name = "sort", description = "Sort field and direction (1 ascending, -1 descending)", in = ParameterIn.QUERY)
        @Valid @RequestParam(value = "sort", required = false) String sort,

        @Parameter(name = "customQuery", description = "Search expression for filtering results", in = ParameterIn.QUERY)
        @Valid @RequestParam(value = "customQuery", required = false) String customQuery
    ) {
        // Build Pageable
        Pageable pageable = ControllerAssistant.createPageable(limit, skip, sort);

        // Fetch Data based on customQuery
        Page<FeedbackEntity> feedbackEntitiesPage;
        if (customQuery != null && !customQuery.trim().isEmpty()) {
            try {
                String sanitizedQuery = ControllerAssistant.sanitizeCustomQuery(customQuery, ALLOWED_FIELDS);
                // Use the repository method with custom query
                // The findByCustomQuery method needs to use pageable even when getting a single item
                pageable = PageRequest.of(0, limit, pageable.getSort());  // Reset the page number for single item retrieval.

                feedbackEntitiesPage = pageableFeedbackRepository.findByCustomQuery(sanitizedQuery, pageable, jdbcTemplate);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }

        } else {
            // Use the repository method for retrieving all feedback with pagination and sorting
            feedbackEntitiesPage = pageableFeedbackRepository.findAll(pageable);
        }

        // Convert to DTOs
        List<Feedback> feedback = feedbackEntitiesPage.getContent().stream()
                .map(FeedbackEntity::toPojo)
                .collect(Collectors.toList());

        // Return the Result
        return new ResponseEntity<>(feedback, HttpStatus.OK);
    }

    /**
     * GET /feedback/{id} : Get feedback
     * Retrieve specific feedback by ID
     *
     * @param id  (required)
     * @return Feedback details (status code 200)
     *         or Feedback not found (status code 404)
     */
    @Operation(
        operationId = "feedbackIdGet",
        summary = "Get feedback",
        description = "Retrieve specific feedback by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Feedback details", content = {
                @Content(mediaType = "application/json", schema = @Schema(implementation = Feedback.class))
            }),
            @ApiResponse(responseCode = "404", description = "Feedback not found")
        },
        security = {
            @SecurityRequirement(name = "basicAuth")
        }
    )
    @GetMapping(
        value = "/feedback/{id}",
        produces = { "application/json" }
    )
    public ResponseEntity<Feedback> feedbackIdGet(
        @Parameter(name = "id", description = "", required = true, in = ParameterIn.PATH)
        @PathVariable("id") String id
    ) {
        try {
            UUID uuid = UUID.fromString(id);
            Optional<FeedbackEntity> optionalEntity = feedbackRepository.findById(uuid);
            return optionalEntity
                    .map(feedbackEntity -> ResponseEntity.ok(feedbackEntity.toPojo()))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }


}
