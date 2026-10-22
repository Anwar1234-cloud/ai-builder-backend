
package com.aibuilder.deployment.railway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

class RailwayCliDomainServiceIntegrationTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void shouldReadExistingRailwayDomain() {

        RailwayCliDomainService service =
                new RailwayCliDomainService();

        String url = service.getExistingDomain(
                "8058aa2d-f9cc-4496-bd0c-7966f24b41b6",
                "bbcd97f2-ed19-4f39-ae20-3c5ca4329890",
                "7a98bcd2-572c-4b62-b959-3c2600cd55cb"
        );

        assertEquals(
                "https://ibnfirnas-backend-production.up.railway.app",
                url
        );
    }
}
