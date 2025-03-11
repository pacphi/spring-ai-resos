package me.pacphi.ai.resos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import me.pacphi.ai.resos.jdbc.OpeningHoursEntity;
import me.pacphi.ai.resos.model.OpeningHours;
import me.pacphi.ai.resos.repository.OpeningHoursRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api/v1/resos")
public class OpeningHoursController {

    private final OpeningHoursRepository openingHoursRepository;

    public OpeningHoursController(OpeningHoursRepository openingHoursRepository) {
        this.openingHoursRepository = openingHoursRepository;
    }

    /**
     * GET /opening-hours : List opening hours
     * Retrieve a list of opening hours
     *
     * @return List of opening hours (status code 200)
     */
    @Operation(
        operationId = "openingHoursGet",
        summary = "List opening hours",
        description = "Retrieve a list of opening hours",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of opening hours", content = {
                @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = OpeningHours.class)))
            })
        },
        security = {
            @SecurityRequirement(name = "basicAuth")
        }
    )
    @GetMapping(
        value = "/opening-hours",
        produces = { "application/json" }
    )
    public ResponseEntity<List<OpeningHours>> openingHoursGet() {
        List<OpeningHours> result =
                StreamSupport
                        .stream(openingHoursRepository.findAll().spliterator(), false)
                        .map(OpeningHoursEntity::toPojo)
                        .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /opening-hours/{id} : Get opening hours
     * Retrieve specific opening hours by ID
     *
     * @param id  (required)
     * @return Opening hours details (status code 200)
     *         or Opening hours not found (status code 404)
     */
    @Operation(
        operationId = "openingHoursIdGet",
        summary = "Get opening hours",
        description = "Retrieve specific opening hours by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Opening hours details", content = {
                @Content(mediaType = "application/json", schema = @Schema(implementation = OpeningHours.class))
            }),
            @ApiResponse(responseCode = "404", description = "Opening hours not found")
        },
        security = {
            @SecurityRequirement(name = "basicAuth")
        }
    )
    @GetMapping(
        value = "/opening-hours/{id}",
        produces = { "application/json" }
    )
    public ResponseEntity<OpeningHours> openingHoursIdGet(
        @Parameter(name = "id", description = "", required = true, in = ParameterIn.PATH)
        @PathVariable("id") String id
    ) {
        try {
            UUID uuid = UUID.fromString(id);
            Optional<OpeningHoursEntity> optionalEntity = openingHoursRepository.findById(uuid);
            return optionalEntity
                    .map(openingHoursEntity -> ResponseEntity.ok(openingHoursEntity.toPojo()))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}
