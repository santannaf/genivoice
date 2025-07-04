package santannaf.demo.genivoice.genivoice.websocket;

import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * Configura o WebSocket para receber conexões no endpoint "/ws/voice".
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoiceWebSocketHandler voiceHandler;

    public WebSocketConfig(VoiceWebSocketHandler voiceHandler) {
        this.voiceHandler = voiceHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceHandler, "/ws/voice")
                .setAllowedOrigins("*");
    }

//    private WebSocketHandler decorateHandler(WebSocketHandler handler) {
//        return new WebSocketHandlerDecorator(handler) {
//            @Override
//            public void afterConnectionEstablished(@Nonnull WebSocketSession session) throws Exception {
//                // Aplica buffer maior à sessão
//                var decoratedSession = new ConcurrentWebSocketSessionDecorator(
//                        session, 30_000, 256 * 1024); // 256 KB buffer
//                super.afterConnectionEstablished(decoratedSession);
//            }
//        };
//    }
}
