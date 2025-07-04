package santannaf.demo.genivoice.genivoice.websocket;

import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import santannaf.demo.genivoice.genivoice.service.ChatService;
import santannaf.demo.genivoice.genivoice.service.SpeechSynthesisService;
import santannaf.demo.genivoice.genivoice.service.TranscriptionService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler do WebSocket que simula uma ligação:
 * - Recebe pacotes de áudio via mensagens binárias
 * - Ao receber a mensagem "END", processa:
 * → transcrição (Whisper)
 * → resposta da IA (GPT)
 * → geração de voz (TTS)
 * → envia resposta em áudio ao cliente
 */
//@Component
public class VoiceWebSocketHandler2 extends AbstractWebSocketHandler {

    private final TranscriptionService transcriptionService;
    private final ChatService chatService;
    private final SpeechSynthesisService speechService;

    // Buffer de arquivos por sessão
    private final ConcurrentHashMap<String, FileOutputStream> audioBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> audioFilePaths = new ConcurrentHashMap<>();

    public VoiceWebSocketHandler2(
            TranscriptionService transcriptionService,
            ChatService chatService,
            SpeechSynthesisService speechService) {
        this.transcriptionService = transcriptionService;
        this.chatService = chatService;
        this.speechService = speechService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            System.out.println("🔌 WebSocket conectado: " + session.getId());
            String filePath = "audio_" + UUID.randomUUID() + ".webm";
            FileOutputStream fos = new FileOutputStream(filePath);
            audioBuffers.put(session.getId(), fos);
            audioFilePaths.put(session.getId(), filePath);
        } catch (IOException e) {
            System.err.println("Erro ao criar arquivo temporário de áudio.");
            e.printStackTrace();
        }
    }

    @Override
    protected void handleTextMessage(@Nonnull WebSocketSession session, TextMessage message) throws Exception {
        System.out.println("Mensagem do Front: " + message.getPayload());

        if (message.getPayload().equals("END")) {
            Thread.ofVirtual().start(() -> processAudio(session));
        }
    }

    @Override
    protected void handleBinaryMessage(@Nonnull WebSocketSession session, @Nonnull BinaryMessage message) {
        try {
            FileOutputStream fos = audioBuffers.get(session.getId());
            if (fos != null) {
                fos.write(message.getPayload().array());
            } else {
                System.err.println("⚠️ Buffer não encontrado para sessão " + session.getId());
            }
        } catch (Exception e) {
            System.err.println("❌ Erro ao processar mensagem binária: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @Nonnull CloseStatus status) throws Exception {
        // Limpeza de buffers se conexão for encerrada antes do "END"
        System.out.println("🧹 Sessão encerrada: " + session.getId() + " com status: " + status);
        String sessionId = session.getId();
        try {
            FileOutputStream fos = audioBuffers.get(sessionId);
            if (fos != null) fos.close();
            String filePath = audioFilePaths.get(sessionId);
            if (filePath != null) Files.deleteIfExists(new File(filePath).toPath());
        } catch (IOException ignored) {
        }
        audioBuffers.remove(sessionId);
        audioFilePaths.remove(sessionId);
    }

    private void processAudio(WebSocketSession session) {
        System.out.println("Process audio ... ");
        String sessionId = session.getId();
        String filePath = audioFilePaths.get(sessionId);

        try {
            FileOutputStream fos = audioBuffers.get(sessionId);
            if (fos != null) fos.close();

            // Transcreve
            String transcribed = transcriptionService.transcribe(filePath);
            if (transcribed.isBlank()) return;

            System.out.println("🎤 Usuário: " + transcribed);

            // Responde
            String response = chatService.generateResponse(transcribed);
            System.out.println("🤖 IA: " + response);

            // Gera voz
            byte[] audio = speechService.synthesize(response);

            // Envia resposta de voz
            session.sendMessage(new BinaryMessage(audio));
        } catch (Exception e) {
            System.err.println("Erro ao processar áudio.");
            e.printStackTrace();
        } finally {
            try {
                audioBuffers.remove(sessionId);
                audioFilePaths.remove(sessionId);
                Files.deleteIfExists(new File(filePath).toPath());

                // Reabre buffer para próximo turno
                String novo = "audio_" + UUID.randomUUID() + ".webm";
                FileOutputStream novoBuffer = new FileOutputStream(novo);
                audioBuffers.put(sessionId, novoBuffer);
                audioFilePaths.put(sessionId, novo);
            } catch (IOException ignored) {
            }
        }
    }
}
