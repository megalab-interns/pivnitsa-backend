package kg.megalab.pivnitsabackend.service;

import kg.megalab.pivnitsabackend.dto.table.TableStatusResponse;
import kg.megalab.pivnitsabackend.entity.ClubTable;
import kg.megalab.pivnitsabackend.entity.TableStatus;
import kg.megalab.pivnitsabackend.repository.ClubTableRepository;
import kg.megalab.pivnitsabackend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TableStatusService {
    private final ClubTableRepository clubTableRepository;
    private final BookingRepository bookingRepository;
    private final TableUnavailabilityChecker unavailabilityChecker;

    public List<TableStatusResponse> getTableStatuses(LocalDate date, Integer guestsCount, Long hallId) {
        OffsetDateTime[] range = unavailabilityChecker.toDayRange(date);
        OffsetDateTime startOfDay = range[0];
        OffsetDateTime endOfDay = range[1];

        List<ClubTable> tables = (hallId != null)
                ? clubTableRepository.findByHallIdAndActiveTrue(hallId)
                : clubTableRepository.findByActiveTrue();

        List<Long> bookedTableIds = bookingRepository.findClubTableIdsWithActiveBookingOnDate(startOfDay, endOfDay);
        Set<Long> unavailableTableIds = unavailabilityChecker.findUnavailableTableIds(startOfDay, endOfDay);

        return tables.stream()
                .map(table -> buildStatusResponse(table, bookedTableIds, unavailableTableIds, guestsCount))
                .toList();
    }

    private TableStatusResponse buildStatusResponse(
            ClubTable table,
            List<Long> bookedTableIds,
            Set<Long> unavailableTableIds,
            Integer guestsCount
    ) {
        TableStatus status;

        if (unavailableTableIds.contains(table.getId())) {
            status = TableStatus.UNAVAILABLE;
        } else if (bookedTableIds.contains(table.getId())) {
            status = TableStatus.BOOKED;
        } else {
            status = TableStatus.FREE;
        }

        boolean fitsCapacity = table.getCapacity() >= guestsCount;

        return new TableStatusResponse(
                table.getId(),
                table.getTableNumber(),
                table.getCapacity(),
                status,
                fitsCapacity,
                table.getPositionX(),
                table.getPositionY(),
                table.getHallId(),
                table.getCategory(),
                table.getDepositAmount()
        );
    }
}