package kg.megalab.pivnitsabackend.service;

import kg.megalab.pivnitsabackend.dto.table.*;
import kg.megalab.pivnitsabackend.entity.ClubTable;
import kg.megalab.pivnitsabackend.exception.hall.HallNotFoundException;
import kg.megalab.pivnitsabackend.exception.tables.TableHasBookingReservationException;
import kg.megalab.pivnitsabackend.exception.tables.TableNotFoundException;
import kg.megalab.pivnitsabackend.exception.tables.TableNumberAlreadyExistException;
import kg.megalab.pivnitsabackend.repository.BookingRepository;
import kg.megalab.pivnitsabackend.repository.ClubTableRepository;
import kg.megalab.pivnitsabackend.repository.HallRepository;
import kg.megalab.pivnitsabackend.repository.BookingRepository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClubTableService {

    private final ClubTableRepository clubTableRepository;
    private final HallRepository hallRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public TableResponse createTable(CreateTableRequest request) {
        if (clubTableRepository.findByTableNumber(request.tableNumber()).isPresent()) {
            throw new TableNumberAlreadyExistException("Такой стол уже существует");
        }

        if (hallRepository.findById(request.hallId()).isEmpty()) {
            throw new HallNotFoundException("Такого зала не существует");
        }

        ClubTable clubTable = ClubTable.builder()
                .tableNumber(request.tableNumber())
                .capacity(request.capacity())
                .hallId(request.hallId())
                .positionX(request.positionX())         // новое: положение теперь можно задать при создании
                .positionY(request.positionY())
                .category(request.category())
                .depositAmount(request.depositAmount())
                .build();

        clubTable = clubTableRepository.save(clubTable);

        return toResponse(clubTable);
    }

    @Transactional
    public TableResponse updateTable(Long id, UpdateTableRequest request) {
        ClubTable clubTable = clubTableRepository.findById(id)
                .orElseThrow(() -> new TableNotFoundException("Столик не найден"));

        if (request.tableNumber() != null && !request.tableNumber().equals(clubTable.getTableNumber())) {
            clubTableRepository.findByTableNumber(request.tableNumber())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new TableNumberAlreadyExistException("Такой столик уже существует");
                    });
            clubTable.setTableNumber(request.tableNumber());
        }

        if (request.hallId() != null && !request.hallId().equals(clubTable.getHallId())) {
            if (hallRepository.findById(request.hallId()).isEmpty()) {
                throw new HallNotFoundException("Такого зала не существует");
            }
            clubTable.setHallId(request.hallId());
        }

        if (request.capacity() != null) {
            clubTable.setCapacity(request.capacity());
        }

        if (request.active() != null) {
            clubTable.setActive(request.active());
        }

        if (request.category() != null) {
            clubTable.setCategory(request.category());
        }

        if (request.positionX() != null) {
            clubTable.setPositionX(request.positionX());
        }

        if (request.positionY() != null) {
            clubTable.setPositionY(request.positionY());
        }

        if (request.depositAmount() != null) {
            clubTable.setDepositAmount(request.depositAmount());
        }

        clubTable = clubTableRepository.save(clubTable);

        return toResponse(clubTable);
    }

    public void deleteTable(Long id) {
        ClubTable clubTable = clubTableRepository.findById(id)
                .orElseThrow(() -> new TableNotFoundException("Столик не найден"));

        if (bookingRepository.existsActiveBookingByTableId(id)) {
            throw new TableHasBookingReservationException("Столик забронирован, нельзя удалить");
        }
        clubTableRepository.delete(clubTable);
    }

    public List<PublicTableResponse> getPublicTables(Long hallId) {
        List<ClubTable> clubTables = (hallId != null)
                ? clubTableRepository.findByHallId(hallId)
                : clubTableRepository.findAll();

        return clubTables.stream()
                .filter(ClubTable::isActive)
                .filter(table -> table.getPositionX() != null && table.getPositionY() != null)
                .map(clubTable -> new PublicTableResponse(
                        clubTable.getId(),
                        clubTable.getTableNumber(),
                        clubTable.getCapacity(),
                        clubTable.getPositionX(),
                        clubTable.getPositionY(),
                        clubTable.getHallId(),
                        clubTable.getCategory()
                )).toList();
    }

    private TableResponse toResponse(ClubTable clubTable) {
        return new TableResponse(
                clubTable.getId(),
                clubTable.getTableNumber(),
                clubTable.getCapacity(),
                clubTable.getHallId(),
                clubTable.isActive(),
                clubTable.getPositionX(),
                clubTable.getPositionY(),
                clubTable.getCategory(),
                clubTable.getDepositAmount()
        );
    }
}
