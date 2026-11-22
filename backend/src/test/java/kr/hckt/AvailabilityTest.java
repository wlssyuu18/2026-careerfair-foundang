package kr.hckt;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class AvailabilityTest {
    @Test void onlyTheRequestedRoomDateAndWholeIntervalCanBeReportedAvailable() {
        var c = new Task.Conditions("면접","2099-11-27","13:00","15:00","서울",10000,List.of(),List.of(),List.of());
        var json = new ObjectMapper();
        String raw = "화상면접실① 2099-11-27 13:00~15:00 예약 가능";
        var args = new HashMap<String,Object>(Map.of("status","AVAILABLE","roomQuote","화상면접실①","dateQuote","2099-11-27","startQuote","13:00","endQuote","15:00","evidence","13:00~15:00 예약 가능"));
        assertEquals("AVAILABLE",TaskService.verifiedAvailability(c,"화상면접실①",raw,"",json.valueToTree(args)).status());
        args.put("evidence","예약 가능");
        assertThrows(IllegalArgumentException.class,()->TaskService.verifiedAvailability(c,"화상면접실①",raw,"",json.valueToTree(args)));
        args.put("evidence","13:00~15:00 예약 가능"); args.put("dateQuote","2099-11-28");
        assertThrows(IllegalArgumentException.class,()->TaskService.verifiedAvailability(c,"화상면접실①",raw,"",json.valueToTree(args)));
        args.put("dateQuote","2099-11-27");
        assertThrows(IllegalArgumentException.class,()->TaskService.verifiedAvailability(c,"화상면접실②",raw,"",json.valueToTree(args)));
        args.put("evidence","13:00~15:00 예약 불가");
        assertThrows(IllegalArgumentException.class,()->TaskService.verifiedAvailability(c,"화상면접실①",raw.replace("가능","불가"),"",json.valueToTree(args)));
        String slots = "화상면접실① 2099-11-27 13:00~14:00 예약 가능 14:00~15:00 예약 가능";
        args.put("evidence","13:00~14:00 예약 가능");
        args.put("slots",List.of(Map.of("start","13:00","end","14:00","evidence","13:00~14:00 예약 가능")));
        assertThrows(IllegalArgumentException.class,()->TaskService.verifiedAvailability(c,"화상면접실①",slots,"",json.valueToTree(args)));
        args.put("slots",List.of(Map.of("start","13:00","end","14:00","evidence","13:00~14:00 예약 가능"),Map.of("start","14:00","end","15:00","evidence","14:00~15:00 예약 가능")));
        assertEquals("AVAILABLE",TaskService.verifiedAvailability(c,"화상면접실①",slots,"",json.valueToTree(args)).status());
    }
}
