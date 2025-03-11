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
import me.pacphi.ai.resos.model.Order;
import me.pacphi.ai.resos.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resos")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * GET /orders : List orders
     * Retrieve a list of orders
     *
     * @param limit Number of records to return (max 100) (optional, default to 100)
     * @param skip Number of records to skip (optional, default to 0)
     * @param sort Sort field and direction (1 ascending, -1 descending) (optional)
     * @param customQuery Search expression for filtering results (optional)
     * @return List of orders (status code 200)
     */
    @Operation(
        operationId = "ordersGet",
        summary = "List orders",
        description = "Retrieve a list of orders",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of orders", content = {
                @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Order.class)))
            })
        },
        security = {
            @SecurityRequirement(name = "basicAuth")
        }
    )
    @GetMapping(
        value = "/orders",
        produces = { "application/json" }
    )
    public ResponseEntity<List<Order>> ordersGet(
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
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * GET /orders/{id} : Get order
     * Retrieve a specific order by ID
     *
     * @param id  (required)
     * @return Order details (status code 200)
     *         or Order not found (status code 404)
     */
    @Operation(
        operationId = "ordersIdGet",
        summary = "Get order",
        description = "Retrieve a specific order by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Order details", content = {
                @Content(mediaType = "application/json", schema = @Schema(implementation = Order.class))
            }),
            @ApiResponse(responseCode = "404", description = "Order not found")
        },
        security = {
            @SecurityRequirement(name = "basicAuth")
        }
    )
    @GetMapping(
        value = "/orders/{id}",
        produces = { "application/json" }
    )
    public ResponseEntity<Order> ordersIdGet(
        @Parameter(name = "id", description = "", required = true, in = ParameterIn.PATH)
        @PathVariable("id") String id
    ) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
