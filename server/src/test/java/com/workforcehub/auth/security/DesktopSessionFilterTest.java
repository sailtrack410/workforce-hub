package com.workforcehub.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DesktopSessionFilterTest {

    @Test
    void desktopSessionShouldUseSevenDaysAndRenewOnEveryRequest() throws Exception {
        DesktopSessionFilter filter = new DesktopSessionFilter(604800);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Workforce-Client", "desktop");
        request.getSession(true).setMaxInactiveInterval(1);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(604800, request.getSession(false).getMaxInactiveInterval());
    }

    @Test
    void webSessionShouldKeepDefaultTimeout() throws Exception {
        DesktopSessionFilter filter = new DesktopSessionFilter(604800);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setMaxInactiveInterval(7200);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(7200, request.getSession(false).getMaxInactiveInterval());
        assertNull(request.getSession(false).getAttribute("desktop"));
    }
}
