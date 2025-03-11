package me.pacphi.ai.resos.csv.impl;

import me.pacphi.ai.resos.csv.CsvEntityMapper;
import me.pacphi.ai.resos.csv.CsvMappingException;
import me.pacphi.ai.resos.csv.EntityMapper;
import me.pacphi.ai.resos.jdbc.CustomerEntity;
import me.pacphi.ai.resos.jdbc.FeedbackEntity;
import me.pacphi.ai.resos.repository.BookingRepository;
import me.pacphi.ai.resos.repository.CustomerRepository;
import me.pacphi.ai.resos.repository.FeedbackRepository;
import org.springframework.data.jdbc.core.mapping.AggregateReference;

import java.time.format.DateTimeParseException;

@CsvEntityMapper("feedback")
public class FeedbackMapper implements EntityMapper<FeedbackEntity> {

    private final FeedbackRepository feedbackRepository;
    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;

    public FeedbackMapper(
            FeedbackRepository feedbackRepository,
            CustomerRepository customerRepository,
            BookingRepository bookingRepository) {
        this.feedbackRepository = feedbackRepository;
        this.customerRepository = customerRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public FeedbackEntity mapFromCsv(String[] line) throws CsvMappingException {
        try {

            String customerName = line[0];

            // Try to find the customer by name
            var customer = customerRepository.findByName(customerName)
                    .orElseGet(() -> {
                        CustomerEntity newCustomer = new CustomerEntity();
                        newCustomer.setName(customerName);
                        return customerRepository.save(newCustomer);
                    });

            var entity = new FeedbackEntity();
            entity.setRating(Integer.parseInt(line[1]));
            entity.setComment(line[2]);
            entity.setCreatedAt(null);  // should be one day after booking
            entity.setIsPublic(Boolean.parseBoolean(line[3]));
            entity.setBookingId(null); // lookup based on guest details embedded in booking and date booking made

            // Create AggregateReference using the found Area's ID
            entity.setCustomer(AggregateReference.to(customer.getId()));

            return entity;
        } catch (DateTimeParseException | IllegalArgumentException | NullPointerException e) {
            throw new CsvMappingException("Failed to map table from CSV", e);
        }
    }

    @Override
    public Class<FeedbackEntity> getEntityClass() {
        return FeedbackEntity.class;
    }
}