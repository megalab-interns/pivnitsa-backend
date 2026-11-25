package kg.megalab.pivnitsabackend.repository;

import kg.megalab.pivnitsabackend.entity.TableUnavailabilityPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TableUnavailabilityPeriodRepository extends JpaRepository<TableUnavailabilityPeriod, Long> {

    @Query("""
                        SELECT tup.clubTableId 
                        FROM TableUnavailabilityPeriod tup
                        WHERE tup.startsAt <= :endOfDay
                        AND tup.endsAt >= :startOfDay
            """)
    List<Long> findClubTableIdsWithUnavailabilityPeriod(@Param("startOfDay") OffsetDateTime startOfDay, @Param("endOfDay") OffsetDateTime endOfDay);

    @Query("""
            SELECT COUNT(tup) > 0
            FROM TableUnavailabilityPeriod tup
            WHERE tup.clubTableId = :tableId
              AND tup.startsAt <= :endOfDay
              AND tup.endsAt >= :startOfDay
            """)
    boolean existsUnavailabilityPeriodForTable(
            @Param("tableId") Long tableId,
            @Param("startOfDay") OffsetDateTime startOfDay,
            @Param("endOfDay") OffsetDateTime endOfDay
    );

    void deleteByClubTableId(Long clubTableId);
}