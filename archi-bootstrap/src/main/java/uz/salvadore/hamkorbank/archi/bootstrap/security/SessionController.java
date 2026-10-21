package uz.salvadore.hamkorbank.archi.bootstrap.security;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.bootstrap.security.dto.SessionDtos.Me;
import uz.salvadore.hamkorbank.archi.bootstrap.security.dto.SessionDtos.UiConfig;

/**
 * Кто вошёл и как входить. Инфраструктура, а не сценарий модели: интерфейс строит
 * себя по роли и узнаёт адрес входа раньше, чем у него появится токен.
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private final UiSettings settings;

    public SessionController(UiSettings settings) {
        this.settings = settings;
    }

    /** Кто вошёл: subject, отображаемое имя из токена и роли продукта. Без входа сюда не дойти — {@code 401}. */
    @GetMapping("/me")
    public Me me(Authentication authentication) {
        return SessionDtoMapper.me(authentication);
    }

    /** Открыт без токена: по нему фронтенд узнаёт, куда вести на вход. */
    @GetMapping("/ui-config")
    public UiConfig uiConfig() {
        return SessionDtoMapper.uiConfig(settings.oidc());
    }
}
