//package santannaf.demo.genivoice.genivoice.websocket;
//
//import org.apache.tomcat.websocket.server.WsServerContainer;
//import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class WebSocketConfigSupport {
//
//    /**
//     * Aumenta os buffers padrão para mensagens WebSocket recebidas (binário e texto).
//     */
//    @Bean
//    public TomcatContextCustomizer tomcatWebSocketBufferCustomizer() {
//        return context -> context.addServletContainerInitializer((c, ctx) -> {
//            Object serverContainer = ctx.getAttribute("javax.websocket.server.ServerContainer");
//            if (serverContainer instanceof WsServerContainer container) {
//                container.setDefaultMaxBinaryMessageBufferSize(256 * 1024); // 256 KB
//                container.setDefaultMaxTextMessageBufferSize(64 * 1024);    // opcional
//                System.out.println("🛠️ Buffer de WebSocket ajustado para até 256KB binários");
//            }
//        }, null);
//    }
//}
//
