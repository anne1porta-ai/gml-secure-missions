package com.gml.shield.security;

import tools.jackson.databind.json.JsonMapper;
import com.gml.shield.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitFilterTest {

    private static final Instant START = Instant.parse("2026-09-18T12:00:00Z");

    @Test
    void repeatedLoginFromSameAddressIsRejected() throws Exception {
        MutableClock clock = new MutableClock(START);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(
                JsonMapper.builder().build(), 1, 60, 100, clock);

        Result first = execute(filter, "POST", LoginRateLimitFilter.LOGIN_PATH, "192.0.2.10");
        Result second = execute(filter, "POST", LoginRateLimitFilter.LOGIN_PATH, "192.0.2.10");

        assertThat(first.filterChainReached()).isTrue();
        assertThat(second.filterChainReached()).isFalse();
        assertThat(second.response().getStatus()).isEqualTo(429);
        assertThat(second.response().getHeader("Retry-After")).isNotBlank();
        assertThat(second.response().getContentAsString())
                .contains(LoginRateLimitFilter.RATE_LIMIT_CODE)
                .contains(LoginRateLimitFilter.RATE_LIMIT_MESSAGE);
    }

    @Test
    void rateLimitWindowResetsWithoutWaitingInRealTime() throws Exception {
        MutableClock clock = new MutableClock(START);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(
                JsonMapper.builder().build(), 1, 60, 100, clock);

        execute(filter, "POST", LoginRateLimitFilter.LOGIN_PATH, "192.0.2.10");
        Result blocked = execute(filter, "POST", LoginRateLimitFilter.LOGIN_PATH, "192.0.2.10");
        assertThat(blocked.filterChainReached()).isFalse();

        clock.advance(Duration.ofSeconds(60));

        Result afterWindow = execute(
                filter, "POST", LoginRateLimitFilter.LOGIN_PATH, "192.0.2.10");
        assertThat(afterWindow.filterChainReached()).isTrue();
    }

    @Test
    void loginBucketsAreIndependentPerAddress() throws Exception {
        MutableClock clock = new MutableClock(START);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(
                JsonMapper.builder().build(), 1, 60, 100, clock);

        Result first = execute(filter, "POST", LoginRateLimitFilter.LOGIN_PATH, "192.0.2.10");
        Result second = execute(filter, "POST", LoginRateLimitFilter.LOGIN_PATH, "192.0.2.11");

        assertThat(first.filterChainReached()).isTrue();
        assertThat(second.filterChainReached()).isTrue();
    }

    @Test
    void nonLoginRequestIsNotLimited() throws Exception {
        MutableClock clock = new MutableClock(START);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(
                JsonMapper.builder().build(), 1, 60, 100, clock);

        Result result = execute(filter, "GET", "/api/missions", "192.0.2.10");

        assertThat(result.filterChainReached()).isTrue();
    }

    private static Result execute(LoginRateLimitFilter filter,
                                  String method,
                                  String path,
                                  String remoteAddress) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean(false);

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> reached.set(true));
        return new Result(reached.get(), response);
    }

    private record Result(boolean filterChainReached, MockHttpServletResponse response) {
    }
}
