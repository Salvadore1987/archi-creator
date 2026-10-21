package uz.salvadore.hamkorbank.archi.bootstrap.web;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uz.salvadore.hamkorbank.archi.bootstrap.security.UiSettings;

/**
 * Настройка веб-слоя: фильтр корреляции журнала и разбор текущего редактора из аутентификации в аргумент обработчика.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UiSettings.class)
public class WebConfiguration implements WebMvcConfigurer {

    /** До цепочки безопасности: её отказы и журнал несут тот же {@code traceId}. */
    @Bean
    FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilter() {
        FilterRegistrationBean<RequestCorrelationFilter> registration =
                new FilterRegistrationBean<>(new RequestCorrelationFilter());
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
        return registration;
    }

    /** После цепочки безопасности: субъект в MDC известен только там. */
    @Bean
    FilterRegistrationBean<RequestUserFilter> requestUserFilter() {
        FilterRegistrationBean<RequestUserFilter> registration = new FilterRegistrationBean<>(new RequestUserFilter());
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentEditorResolver());
    }
}
