package me.pacphi.ai.resos.repository;

import me.pacphi.ai.resos.jdbc.FeedbackEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PageableFeedbackRepository extends PagingAndSortingRepository<FeedbackEntity, UUID> {

    default Page<FeedbackEntity> findByCustomQuery(@Param("customQuery") String customQuery, Pageable pageable, JdbcTemplate jdbcTemplate) {
        String baseQuery = "SELECT * FROM feedback WHERE " + customQuery;
        String countQuery = "SELECT COUNT(*) FROM feedback WHERE " + customQuery;

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

        List<FeedbackEntity> content = jdbcTemplate.query(baseQuery, (rs, rowNum) -> {
            FeedbackEntity entity = new FeedbackEntity();
            entity.setId(rs.getObject("id", UUID.class));
            entity.setRating(rs.getInt("rating"));
            entity.setComment(rs.getString("comment"));
            entity.setIsPublic(rs.getBoolean("is_public"));
            entity.setCreatedAt(rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC));
            entity.setBookingId(rs.getString("booking_id"));
            entity.setCustomer(AggregateReference.to(rs.getObject("customer_id", UUID.class)));
            return entity;
        });


        Long total = jdbcTemplate.queryForObject(countQuery, Long.class);

        return new PageImpl<FeedbackEntity>(content, pageable, total == null ? 0 : total);
    }

}
