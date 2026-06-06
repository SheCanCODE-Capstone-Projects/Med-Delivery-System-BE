package com.meddelivery.repository;

import com.meddelivery.model.LoginEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {
    Page<LoginEvent> findAllByOrderByTimestampDesc(Pageable pageable);
    Page<LoginEvent> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);
    long countBySuccessFalseAndTimestampAfter(LocalDateTime since);
    long countBySuccessTrueAndTimestampAfter(LocalDateTime since);

    @Query("SELECT e.ipAddress, COUNT(e) AS cnt FROM LoginEvent e " +
           "WHERE e.success = false AND e.timestamp >= :since " +
           "GROUP BY e.ipAddress HAVING COUNT(e) >= :threshold " +
           "ORDER BY cnt DESC")
    List<Object[]> findSuspiciousIps(@Param("since") LocalDateTime since, @Param("threshold") long threshold);
}
