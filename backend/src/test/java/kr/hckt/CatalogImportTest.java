package kr.hckt;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

class CatalogImportTest {
    @Test void rejectsUnsafeOrMisleadingMetadata() throws Exception {
        var data = CatalogImport.bundled();
        assertEquals(33, data.path("rooms").size());
        for (String field : new String[]{"url", "remainingQuantity", "price", "externalId"}) {
            var changed = data.deepCopy();
            var room = (ObjectNode) changed.path("rooms").get(1);
            switch (field) {
                case "url" -> room.put("url", "https://example.com/fake");
                case "remainingQuantity" -> room.put(field, 1);
                case "price" -> ((ObjectNode) room.path("price")).put("amount", -1);
                case "externalId" -> room.put(field, data.path("rooms").get(0).path(field).asText());
            }
            assertThrows(IllegalArgumentException.class, () -> CatalogImport.validate(changed), field);
        }
        var unknown = data.deepCopy();
        ((ObjectNode) unknown.path("rooms").get(0).path("price")).putNull("amount");
        assertThrows(IllegalArgumentException.class, () -> CatalogImport.validate(unknown));
    }
}
