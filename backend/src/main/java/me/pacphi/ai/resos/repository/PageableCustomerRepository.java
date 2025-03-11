package me.pacphi.ai.resos.repository;

import me.pacphi.ai.resos.jdbc.CustomerEntity;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PageableCustomerRepository extends PagingAndSortingRepository<CustomerEntity, UUID> {

    default Page<CustomerEntity> findByCustomQuery(@Param("customQuery") String customQuery, Pageable pageable, JdbcTemplate jdbcTemplate) {
        String baseQuery = "SELECT * FROM customer WHERE " + customQuery;
        String countQuery = "SELECT COUNT(*) FROM customer WHERE " + customQuery;

        // Add sorting
        if (pageable.getSort().isSorted()) {
            baseQuery += " ORDER BY ";
            baseQuery += pageable.getSort().stream()
                    .map(order -> order.getProperty() + " " + order.getDirection().name())
                    .reduce((s1, s2) -> s1 + ", " + s2)
                    .orElse("");
        }

        // Add pagination
        baseQuery += " LIMIT " + pageable.getPageSize() + " OFFSET " + pageable.getOffset();

        List<CustomerEntity> content = jdbcTemplate.query(baseQuery, (rs, rowNum) -> {
            CustomerEntity entity = new CustomerEntity();
            entity.setId(rs.getObject("id", UUID.class));
            entity.setName(rs.getString("name_01"));
            entity.setEmail(rs.getString("email"));
            entity.setPhone(rs.getString("phone"));
            entity.setCreatedAt(rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC));
            entity.setLastBookingAt(rs.getTimestamp("last_booking_at").toInstant().atOffset(java.time.ZoneOffset.UTC));
            entity.setBookingCount(rs.getInt("booking_count"));
            entity.setTotalSpent(rs.getFloat("total_spent"));
            return entity;
        });


        Long total = jdbcTemplate.queryForObject(countQuery, Long.class);

        return new PageImpl<CustomerEntity>(content, pageable, total == null ? 0 : total);
    }

}
