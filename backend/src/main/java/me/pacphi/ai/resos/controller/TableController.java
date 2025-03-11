package me.pacphi.ai.resos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import me.pacphi.ai.resos.jdbc.AreaEntity;
import me.pacphi.ai.resos.jdbc.TableEntity;
import me.pacphi.ai.resos.model.Table;
import me.pacphi.ai.resos.repository.AreaRepository;
import me.pacphi.ai.resos.repository.TableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api/v1/resos")
public class TableController {

    private static Logger log = LoggerFactory.getLogger(TableController.class);

    private final TableRepository tableRepository;
    private final AreaRepository areaRepository;

    public TableController(TableRepository tableRepository, AreaRepository areaRepository) {
        this.tableRepository = tableRepository;
        this.areaRepository = areaRepository;
    }

    /**
     * GET /tables : List tables
     * Retrieve a list of tables
     *
     * @return List of tables (status code 200)
     */
    @Operation(
        operationId = "tablesGet",
        summary = "List tables",
        description = "Retrieve a list of tables",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of tables", content = {
                @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Table.class)))
            })
        },
        security = {
            @SecurityRequirement(name = "basicAuth")
        }
    )
    @GetMapping(
        value = "/tables",
        produces = { "application/json" }
    )
    public ResponseEntity<List<Table>> tablesGet() {
        List<Table> raw =
                StreamSupport
                        .stream(tableRepository.findAll().spliterator(), false)
                        .map(TableEntity::toPojo)
                        .toList();

        List<Table> result = raw.stream()
                .map(t -> {
                    if (t.getArea() == null || t.getArea().getId() == null) {
                        return t;
                    }
                    try {
                        Optional<AreaEntity> areaEntity = areaRepository.findById(UUID.fromString(t.getArea().getId()));
                        return areaEntity.map(ae -> {
                            t.setArea(ae.toPojo());
                            return t;
                        }).orElse(t);
                    } catch (IllegalArgumentException iae) {
                        log.warn("Invalid UUID for area id: " + t.getArea().getId() + " - ", iae);
                        return t;
                    }
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}