package kg.megalab.pivnitsabackend.service;

import kg.megalab.pivnitsabackend.dto.table.CreateTableRequest;
import kg.megalab.pivnitsabackend.dto.table.TableResponse;
import kg.megalab.pivnitsabackend.entity.ClubTable;
import kg.megalab.pivnitsabackend.entity.Hall;
import kg.megalab.pivnitsabackend.repository.BookingRepository;
import kg.megalab.pivnitsabackend.repository.ClubTableRepository;
import kg.megalab.pivnitsabackend.repository.HallRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubTableServiceTest {

    @Mock
    private ClubTableRepository clubTableRepository;
    @Mock
    private HallRepository hallRepository;
    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private ClubTableService clubTableService;

    @Test
    void shouldSavePositionCategoryAndDepositOnCreate() {
        CreateTableRequest request = new CreateTableRequest(
                "10", 4, 1L,
                new BigDecimal("40.50"), new BigDecimal("60.00"),
                "стол", new BigDecimal("10000")
        );
        when(clubTableRepository.findByTableNumber("10")).thenReturn(Optional.empty());
        when(hallRepository.findById(1L)).thenReturn(Optional.of(mock(Hall.class)));
        when(clubTableRepository.save(any(ClubTable.class))).thenAnswer(inv -> inv.getArgument(0)); // возвращаем то, что сохраняли

        TableResponse response = clubTableService.createTable(request);

        ArgumentCaptor<ClubTable> captor = ArgumentCaptor.forClass(ClubTable.class);
        verify(clubTableRepository).save(captor.capture());
        ClubTable saved = captor.getValue();                                                         // что реально ушло в репозиторий
        assertEquals(new BigDecimal("40.50"), saved.getPositionX());
        assertEquals(new BigDecimal("60.00"), saved.getPositionY());
        assertEquals("стол", saved.getCategory());
        assertEquals(new BigDecimal("10000"), saved.getDepositAmount());
        assertEquals(new BigDecimal("40.50"), response.positionX());
        assertEquals(new BigDecimal("60.00"), response.positionY());
    }

    @Test
    void shouldCreateTableWithoutPositionAsBefore() {
        CreateTableRequest request = new CreateTableRequest("11", 2, 1L, null, null, null, null);
        when(clubTableRepository.findByTableNumber("11")).thenReturn(Optional.empty());
        when(hallRepository.findById(1L)).thenReturn(Optional.of(mock(Hall.class)));
        when(clubTableRepository.save(any(ClubTable.class))).thenAnswer(inv -> inv.getArgument(0));

        TableResponse response = clubTableService.createTable(request);

        assertNull(response.positionX());   // старое поведение сохранено: без позиции стол просто создаётся
        assertNull(response.positionY());
        assertNull(response.depositAmount());
    }
}
