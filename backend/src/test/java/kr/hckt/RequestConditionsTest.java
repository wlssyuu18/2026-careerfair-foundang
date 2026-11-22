package kr.hckt;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestConditionsTest {
    private Task.Conditions schedule(String start, String end) {
        return new Task.Conditions("온라인 시험", "2099-11-27", start, end, "서울", 10000, List.of("웹캠"), List.of("노트북"), List.of());
    }
    @Test void reservationAddsPreparationAndRoundsOutward() {
        for (var times : List.of(
            List.of("12:30", "14:50", "12:00", "15:00"),
            List.of("12:30", "14:20", "12:00", "14:30"),
            List.of("12:45", "14:30", "12:00", "14:30"),
            List.of("12:00", "14:00", "11:30", "14:00"))) {
            var original = schedule(times.get(0), times.get(1));
            var result = RequestConditions.reservation(original);
            assertEquals(times.get(2), result.start());
            assertEquals(times.get(3), result.end());
            assertTrue(result.notes().getFirst().contains(original.start() + "~" + original.end()));
            assertEquals(original.equipment(), result.equipment());
            assertEquals(original.budget(), result.budget());
        }
    }
    @Test void missingOrInvalidTimesAreLeftForCorrection() {
        for (var times : List.of(List.of("12:30", ""), List.of("", "14:20"), List.of("bad", "14:20"), List.of("14:20", "12:30"))) {
            var original = schedule(times.get(0), times.get(1));
            assertSame(original, RequestConditions.reservation(original));
        }
    }
    @Test void midnightDoesNotWrapToAnotherDaySilently() {
        assertEquals("", RequestConditions.reservation(schedule("00:20", "01:00")).start());
        assertEquals("", RequestConditions.reservation(schedule("22:00", "23:50")).end());
        assertEquals("00:00", RequestConditions.reservation(schedule("00:30", "01:00")).start());
    }
}
