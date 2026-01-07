package me.pacphi.ai.resos.mcp;

import me.pacphi.ai.resos.api.DefaultApi;
import me.pacphi.ai.resos.model.Customer;
import me.pacphi.ai.resos.model.Feedback;
import me.pacphi.ai.resos.model.OpeningHours;
import me.pacphi.ai.resos.model.Table;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResOsService {

	private final DefaultApi apiClient;

	public ResOsService(DefaultApi apiClient) {
		this.apiClient = apiClient;
	}

	@Tool(description = "Fetch details about all the tables in a restaurant")
	public List<Table> getTables() {
		return apiClient.tablesGet().getBody();
	}

	@Tool(description = "Fetch the next two weeks of opening hours for registered restaurants")
	public List<OpeningHours> getOpeningHours() {
		return apiClient.openingHoursGet().getBody();
	}

	@Tool(description = "Fetch opening hours for a registered restaurant and day")
	public OpeningHours getOpeningHoursById(@ToolParam(description = "A unique identifier used to return a specific restaurant and day opening hours record") String id) {
		return apiClient.openingHoursIdGet(id).getBody();
	}

	@Tool(description = "Fetch a list of customer records")
	public List<Customer> getCustomers(
			@ToolParam(required = false, description= "Number of customer records to return, up to a maximum of 100") Integer limit,
			@ToolParam(required = false, description= "Number of customer records to skip") Integer skip,
			@ToolParam(required = false, description= "Sort field and direction (field:direction, e.g., name_01:asc, created_at:desc") String sort,
			@ToolParam(required = false, description= "Search expression for filtering results. Must use format: field OPERATOR value [AND|OR field OPERATOR value]. Allowed fields: name_01, email, phone, created_at, last_booking_at, booking_count, total_spent, metadata. Allowed operators: =, !=, >, >=, <, <=, LIKE, IS NULL, IS NOT NULL. Values must be quoted strings, numbers, or NULL. Example: booking_count >= 5 AND total_spent > 100. Do NOT use SQL functions.") String customQuery) {
		return apiClient.customersGet(limit, skip, sort, customQuery).getBody();
	}

	@Tool(description = "Fetch a specific customer record")
	public Customer getCustomerById(@ToolParam(description = "A unique identifier used to return a specific customer record") String id) {
		return apiClient.customersIdGet(id).getBody();
	}

	@Tool(description = "Fetch a list of feedback records")
	public List<Feedback> getFeedback(
			@ToolParam(required = false, description= "Number of feedback records to return, up to a maximum of 100") Integer limit,
			@ToolParam(required = false, description= "Number of feedback records to skip") Integer skip,
			@ToolParam(required = false, description= "Sort field and direction (field:direction, e.g., rating:asc, created_at:desc") String sort,
			@ToolParam(required = false, description= "Search expression for filtering results. Must use format: field OPERATOR value [AND|OR field OPERATOR value]. Allowed fields: booking_id, rating, comment_01, created_at, customer_id, is_public. Allowed operators: =, !=, >, >=, <, <=, LIKE, IS NULL, IS NOT NULL. Values must be quoted strings, numbers, or NULL. Example: rating >= 3 AND is_public = 'true'. Do NOT use SQL functions like NOW() or INTERVAL.") String customQuery) {
		return apiClient.feedbackGet(limit, skip, sort, customQuery).getBody();
	}

	@Tool(description = "Fetch a specific feedback record")
	public Feedback getFeedbackById(@ToolParam(description = "A unique identifier used to return a specific feedback record") String id) {
		return apiClient.feedbackIdGet(id).getBody();
	}

}
