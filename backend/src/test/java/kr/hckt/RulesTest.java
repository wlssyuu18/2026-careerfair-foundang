package kr.hckt;

import org.junit.jupiter.api.Test;

class RulesTest {
    @Test void bookingBoundaries() { RulesSelfCheck.main(new String[0]); }
    @Test void onlyKnownSearchPostsAreAllowed() {
        org.junit.jupiter.api.Assertions.assertTrue(Rules.searchPost("https://www.eshare.go.kr/UserPortal/adv/resources/AdvEsSearchResourcesList.do"));
        org.junit.jupiter.api.Assertions.assertTrue(Rules.searchPost("https://www.eshare.go.kr/UserPortal/comm/zip/selectsigunguList.do"));
        org.junit.jupiter.api.Assertions.assertTrue(Rules.searchPost("https://yeyak.seoul.go.kr/web/reservation/selectListReservCalAjax.do"));
        org.junit.jupiter.api.Assertions.assertFalse(Rules.searchPost("https://www.eshare.go.kr/UserPortal/reservation/submit.do"));
        org.junit.jupiter.api.Assertions.assertFalse(Rules.searchPost("https://yeyak.seoul.go.kr/web/reservation/insertReserv.do"));
        org.junit.jupiter.api.Assertions.assertFalse(Rules.searchPost("https://www.eshare.go.kr/web/search/selectPageListTotalSearch.do"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Rules.searchPost("https://example.com/search.do"));
    }
}
