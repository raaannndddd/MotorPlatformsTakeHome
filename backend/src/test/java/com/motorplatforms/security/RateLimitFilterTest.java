package com.motorplatforms.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

  private final RateLimitFilter filter = new RateLimitFilter(2, new ObjectMapper());

  @Test
  void limitsEachIpOnPublicEndpoints() throws Exception {
    assertThat(call("/api/public/otp", "1.1.1.1")).isEqualTo(200);
    assertThat(call("/api/public/otp", "1.1.1.1")).isEqualTo(200);
    assertThat(call("/api/public/otp", "1.1.1.1")).isEqualTo(429);
    assertThat(call("/api/public/otp", "2.2.2.2")).isEqualTo(200);
  }

  @Test
  void ignoresAuthenticatedAdminEndpoints() throws Exception {
    for (int i = 0; i < 5; i++) {
      assertThat(call("/api/clients", "1.1.1.1")).isEqualTo(200);
    }
  }

  private int call(String path, String ip) throws Exception {
    var request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr(ip);
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response.getStatus();
  }
}
