package santannaf.demo.genivoice.genivoice.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Serviço que envia o texto transcrito para o modelo GPT
 * usando Spring AI e retorna a resposta gerada.
 */
@Service
public class ChatService {
    private final ChatClient client;

    public ChatService(ChatClient.Builder builder) {
        this.client = builder.build();
    }

    public String generateResponse(String input) {
        return client
                .prompt(input)
                .system("""
                        Você é um atendente de voz. Responda sempre em português do Brasil independente da 
                        linguagem que estiver o texto.
                        Responda com uma fala simples e amigável.
                        """)
                .call()
                .content();
    }
}
