package uz.salvadore.hamkorbank.archi.bootstrap.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("запрос пишется одной записью INFO: метод, путь, статус, длительность — без тела и заголовков")
    void requestLine() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/models/import");
        request.setQueryString("strict=true");
        request.addHeader("Authorization", "Bearer secret-token");
        request.setContent("<model>confidential</model>".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RequestLoggingFilter().doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                ((HttpServletResponse) res).setStatus(409);
            }
        });
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).startsWith("http POST /api/v1/models/import 409 in ")
                    .doesNotContain("secret-token").doesNotContain("confidential").doesNotContain("strict");
        });
    }

    @Test
    @DisplayName("отказ ниже по цепочке тоже попадает в журнал запроса")
    void failureLogged() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/models");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new RequestLoggingFilter().doFilter(request, response, (req, res) -> {
            throw new IllegalStateException("boom");
        })).hasMessage("boom");
        assertThat(appender.list).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).startsWith("http GET /api/v1/models "));
    }
}
