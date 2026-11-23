package kr.hckt;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogSearch {
    record Entry(Task.Candidate candidate, String room, String channel, String policy, String notes) {}
    private final JdbcClient db;
    CatalogSearch(JdbcClient db) { this.db = db; }

    List<Entry> find(Task.Conditions conditions) {
        return find(conditions, Set.of());
    }
    List<Entry> find(Task.Conditions conditions, Set<String> excluded) {
        return find(conditions, excluded, false);
    }
    List<Entry> findBookable(Task.Conditions conditions) { return find(conditions, Set.of(), true); }
    private List<Entry> find(Task.Conditions conditions, Set<String> excluded, boolean bookableOnly) {
        Rules.conditions(conditions);
        long minutes = Duration.between(LocalTime.parse(conditions.start()), LocalTime.parse(conditions.end())).toMinutes();
        var params = new HashMap<String, Object>();
        params.put("minutes", minutes); params.put("budget", conditions.budget());
        params.put("rawRegion", conditions.region());
        params.put("region", "%" + regionKey(conditions.region()).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        var sql = new StringBuilder("""
            WITH matches AS (
              SELECT l.id, r.name, r.address, r.equipment, r.facilities, r.sector,
                r.interview_policy, l.booking_channel, l.booking_notes, l.eligibility,
                l.source_code, l.source_url, l.price_quote, l.price_amount AS base_price, l.fetched_at, s.name AS source_name,
                CASE WHEN l.price_amount=0 THEN 0
                  WHEN l.price_unit='HOUR' AND l.price_basis IN ('ROOM','PERSON')
                    THEN l.price_amount * ceil(:minutes / 60.0)
                  WHEN l.price_unit='BOOKING' AND l.price_basis='ROOM' AND :minutes <= l.minimum_minutes
                    THEN l.price_amount
                  ELSE NULL END AS estimated_price
              FROM resource r JOIN resource_listing l ON l.resource_id=r.id
                JOIN resource_source s ON s.code=l.source_code
              WHERE r.active AND l.active AND r.kind='SPACE' AND r.privacy<>'SHARED'
                AND (l.minimum_people IS NULL OR l.minimum_people<=1)
                AND (l.minimum_minutes IS NULL OR l.minimum_minutes<=:minutes)
                AND (replace(concat(
                  CASE r.sido WHEN '경상남도' THEN '경남' WHEN '경상북도' THEN '경북'
                    WHEN '전라남도' THEN '전남' WHEN '전라북도' THEN '전북'
                    WHEN '전북특별자치도' THEN '전북' WHEN '충청남도' THEN '충남'
                    WHEN '충청북도' THEN '충북'
                    ELSE regexp_replace(r.sido,'특별자치도|특별자치시|특별시|광역시|도','','g') END,
                  r.sigungu,r.eupmyeondong),' ','') LIKE :region
                  OR (l.source_code='demo' AND replace(:rawRegion,' ','') LIKE replace(concat(r.sido,r.sigungu),' ','') || '%'))
            )
            SELECT * FROM matches WHERE (base_price IS NULL OR base_price<=:budget)
              AND (estimated_price IS NULL OR estimated_price<=:budget)
            """);
        if (bookableOnly) sql.append(" AND source_code='demo' AND estimated_price IS NOT NULL");
        int field = 0;
        for (String need : Rules.missingEquipment(conditions.equipment(), List.of(), conditions.ownedEquipment())) {
            sql.append(" AND EXISTS (SELECT 1 FROM unnest(equipment || facilities) gear WHERE (");
            var clauses = new ArrayList<String>();
            for (String alias : Rules.equipmentAliases(need)) {
                String key = "gear" + field++;
                params.put(key, "%" + alias.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%");
                clauses.add("lower(gear) LIKE :" + key);
            }
            sql.append(String.join(" OR ", clauses)).append("))");
        }
        if (!excluded.isEmpty()) {
            sql.append(" AND id NOT IN (:excluded)");
            params.put("excluded", excluded.stream().map(UUID::fromString).toList());
        }
        sql.append(" ORDER BY (source_name='서울시 공공서비스예약 API') DESC,(source_name='가상 공간') DESC,(booking_channel='ONLINE') DESC,(interview_policy='EXPLICIT') DESC,estimated_price NULLS LAST,name,id LIMIT 5");
        return db.sql(sql.toString()).params(params).query((rs, row) -> {
            Number amount = (Number) rs.getObject("estimated_price");
            Integer price = amount == null ? null : amount.intValue();
            String name = rs.getString("name");
            String evidence = Objects.toString(rs.getString("price_quote"), "요금 확인 필요") + " · " + Objects.toString(rs.getString("booking_notes"), "");
            var candidate = new Task.Candidate(rs.getObject("id").toString(), name,
                (bookableOnly ? "공간 대여" : rs.getString("source_name")) + ("PUBLIC".equals(rs.getString("sector")) ? " · 공공" : " · 민간"),
                rs.getString("source_url"), bookableOnly ? rs.getString("address").replace(" · 가상 주소", "") : rs.getString("address"), price, strings(rs,"equipment"),
                bookableOnly ? "누구나 이용" : rs.getString("eligibility"), bookableOnly ? "이용 시간 기준 총요금" : evidence, rs.getTimestamp("fetched_at").toInstant());
            return new Entry(candidate, name.contains(" · ") ? name.substring(name.lastIndexOf(" · ") + 3) : name,
                rs.getString("booking_channel"), rs.getString("interview_policy"), rs.getString("booking_notes"));
        }).list();
    }
    static String regionKey(String region) {
        return region.replace("전북특별자치도","전북").replace("경상남도","경남").replace("경상북도","경북")
            .replace("전라남도","전남").replace("전라북도","전북").replace("충청남도","충남").replace("충청북도","충북")
            .replaceAll("특별자치도|특별자치시|특별시|광역시", "").replace("경기도","경기").replace("제주도","제주").replaceAll("\\s+", "");
    }
    private static List<String> strings(ResultSet rs, String field) throws SQLException {
        var value = rs.getArray(field);
        try { return List.of((String[]) value.getArray()); } finally { value.free(); }
    }
}
