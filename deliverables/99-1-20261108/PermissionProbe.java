import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.security.AdminPermissionFilter;
import com.workforcehub.auth.security.AdminUserDetails;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class PermissionProbe {
    public static void main(String[] args) throws Exception {
        var user = new AdminUserDetails(1L, "fixture", "unused", "fixture", "ACTIVE", List.of(), List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        new AdminPermissionFilter(new ObjectMapper()).doFilter(
            new MockHttpServletRequest("POST", "/api/admin/im/outbox-events/dispatch"), response,
            (request, result) -> invoked.set(true));
        System.out.println("input=POST /api/admin/im/outbox-events/dispatch; permissions=[]");
        System.out.println("filterChainInvoked=" + invoked.get() + "; mockResponseStatus=" + response.getStatus());
        System.out.println("body=" + response.getContentAsString());
        System.out.println("boundary=filter probe only; no HTTP business success claimed");
        SecurityContextHolder.clearContext();
    }
}
