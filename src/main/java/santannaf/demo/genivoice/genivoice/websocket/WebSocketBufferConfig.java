//package santannaf.demo.genivoice.genivoice.websocket;
//
//import jakarta.websocket.server.ServerEndpointConfig;
//import org.apache.tomcat.websocket.server.WsServerContainer;
//import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class WebSocketBufferConfig {
//
//    @Bean
//    public TomcatContextCustomizer customizeWebSocketBuffer() {
//        return context -> context.addApplicationListener("org.apache.tomcat.websocket.server.WsContextListener");
//    }
//
//    @Bean
//    public ServerEndpointConfig.Configurator customConfigurator() {
//        return new ServerEndpointConfig.Configurator() {
//        };
//    }
//
//    @Bean
//    public TomcatContextCustomizer increaseTomcatWebSocketBuffer() {
//        return context -> context.addServletContainerInitializer((c, ctx) -> {
//            Object obj = ctx.getAttribute("javax.websocket.server.ServerContainer");
//            if (obj instanceof WsServerContainer container) {
//                container.setDefaultMaxBinaryMessageBufferSize(256 * 1024); // 256 KB
//                container.setDefaultMaxTextMessageBufferSize(64 * 1024);    // opcional
//            }
//        }, null);
//    }
//}
//
