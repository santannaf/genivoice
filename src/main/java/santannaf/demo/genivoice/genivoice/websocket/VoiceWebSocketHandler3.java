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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Objects;
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

public class VoiceWebSocketHandler3 extends AbstractWebSocketHandler {

    private final TranscriptionService transcriptionService;
    private final ChatService chatService;
    private final SpeechSynthesisService speechService;

    private final ConcurrentHashMap<String, FileOutputStream> audioBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AudioStream> streams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> audioFilePaths = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> processingFlags = new ConcurrentHashMap<>();

    public VoiceWebSocketHandler3(
            TranscriptionService transcriptionService,
            ChatService chatService,
            SpeechSynthesisService speechService) {
        this.transcriptionService = transcriptionService;
        this.chatService = chatService;
        this.speechService = speechService;
    }

    private static class AudioStream {
        public FileOutputStream fileOutputStream;
        public String filePath;
        public int version;
        public boolean removed;
    }

    private AudioStream startAudioStream() throws FileNotFoundException {
        String filePath = "audio_" + UUID.randomUUID() + ".webm";
        FileOutputStream fos = new FileOutputStream(filePath);
        AudioStream stream = new AudioStream();
        stream.fileOutputStream = fos;
        stream.filePath = filePath;
        stream.version = 0;
        stream.removed = false;
        return stream;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("🔌 WebSocket conectado: " + session.getId());
        try {
            createNewAudioBuffer(session.getId());
        } catch (IOException e) {
            System.err.println("❌ Erro ao criar arquivo inicial de áudio.");
            e.printStackTrace();
        }
    }

    @Override
    protected void handleBinaryMessage(@Nonnull WebSocketSession session, @Nonnull BinaryMessage message) {
        String sessionId = session.getId();

        try {
            AudioStream stream = streams.get(sessionId);
            if (stream != null) {
                ByteBuffer buffer = message.getPayload();
                stream.fileOutputStream.write(buffer.array());
                synchronized (stream) {
                    stream.version++;
                }
            }
        } catch (Exception error) {
            System.err.println("❌ Erro ao gravar áudio recebido.");
            error.printStackTrace();
        }
    }

//    @Override
//    protected void handleBinaryMessage(@Nonnull WebSocketSession session, @Nonnull BinaryMessage message) {
//        String sessionId = session.getId();
//
//        // Ignora mensagens binárias se estiver processando
//        if (Boolean.TRUE.equals(processingFlags.get(sessionId))) {
//            System.out.println("⏸️ Ignorando chunk após END (sessão " + sessionId + ")");
//            return;
//        }
//
//        // Cria novo buffer se ainda não houver (após processar END)
//        if (!audioBuffers.containsKey(sessionId)) {
//            System.out.println("📦 Criando novo buffer após resposta da IA.");
//            try {
//                createNewAudioBuffer(sessionId);
//            } catch (IOException e) {
//                System.err.println("❌ Erro ao criar novo buffer.");
//                e.printStackTrace();
//                return;
//            }
//        }
//
//        try {
//            FileOutputStream fos = audioBuffers.get(sessionId);
//            if (fos != null) {
//                fos.write(message.getPayload().array());
//            } else {
//                System.err.println("⚠️ Buffer não encontrado para sessão " + sessionId);
//            }
//        } catch (IOException e) {
//            System.err.println("❌ Erro ao gravar áudio: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }

//    @Override
//    protected void handleTextMessage(@Nonnull WebSocketSession session, TextMessage message) {
//        if ("END".equals(message.getPayload())) {
//            System.out.println("📩 Recebido END da sessão: " + session.getId());
//            if (Boolean.TRUE.equals(processingFlags.get(session.getId()))) {
//                System.out.println("⏳ Processamento já em andamento. Ignorando.");
//                return;
//            }
//
//            processingFlags.put(session.getId(), true);
//            Thread.ofVirtual().start(() -> processAudio(session));
//        }
//    }

    @Override
    protected void handleTextMessage(@Nonnull WebSocketSession session, @Nonnull TextMessage message) throws FileNotFoundException {
        String text = message.getPayload();

        if (text.equalsIgnoreCase("start")) {
            AudioStream stream = streams.get(session.getId());

            if (Objects.isNull(stream)) {
                streams.put(session.getId(), startAudioStream());
            } else {
                synchronized (stream) {
                    if (stream.removed) {
                        streams.put(session.getId(), startAudioStream());
                    } else {
                        stream.version++;
                    }
                }
            }
        } else if (text.equalsIgnoreCase("end")) {
            AudioStream audioStream = streams.get(session.getId());

            if (Objects.nonNull(audioStream)) {
                Thread.ofVirtual().start(() -> processAudio(session, audioStream, audioStream.version));
            }
        }
    }

//    @Override
//    public void afterConnectionClosed(WebSocketSession session, @Nonnull CloseStatus status) {
//        System.out.println("🧹 Sessão encerrada: " + session.getId() + " com status: " + status);
//        cleanupSession(session.getId());
//    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @Nonnull CloseStatus status) {
        System.out.println("🧹 Sessão encerrada: " + session.getId() + " com status: " + status);
        String sessionId = session.getId();
        try {
            AudioStream stream = streams.get(sessionId);
            stream.fileOutputStream.close();
            Files.deleteIfExists(new File(stream.filePath).toPath());
        } catch (Exception ignored) {

        } finally {
            streams.remove(sessionId);
        }
    }

    private void processAudio(WebSocketSession session, AudioStream stream, int version) {
        String filePath = stream.filePath;
        boolean executed = false;

        try {
            stream.fileOutputStream.flush();
            System.out.println("🛠️ Iniciando transcrição do arquivo: " + filePath);

            String transcribed = transcriptionService.transcribe(filePath);

            if (transcribed == null || transcribed.isBlank()) {
                System.out.println("⚠️ Nada transcrito.");
                return;
            }

            System.out.println("🎤 Usuário: " + transcribed);

            String response = chatService.generateResponse(transcribed);

            System.out.println("🤖 IA: " + response);

            byte[] audio = speechService.synthesize(response);

            synchronized (stream) {
                if (version != stream.version) return;

                this.streams.remove(session.getId(), stream);
                stream.removed = true;
            }

            executed = true;
            session.sendMessage(new BinaryMessage(audio));
            System.out.println("📤 Resposta enviada com sucesso.");
        } catch (Exception error) {
            System.err.println("❌ Erro ao processar áudio.");
            error.printStackTrace();
        } finally {
            try {
                if (executed) {
                    stream.fileOutputStream.close();
                    Files.deleteIfExists(new File(filePath).toPath());
                }
            } catch (IOException ignored) {
            }
        }
    }

//    private void processAudio(WebSocketSession session) {
//        String sessionId = session.getId();
//        String filePath = audioFilePaths.get(sessionId);
//
//        try {
//            FileOutputStream fos = audioBuffers.remove(sessionId);
//            if (fos != null) fos.close();
//
//            System.out.println("🛠️ Iniciando transcrição do arquivo: " + filePath);
//
//            String transcribed = transcriptionService.transcribe(filePath);
//            if (transcribed == null || transcribed.isBlank()) {
//                System.out.println("⚠️ Nada transcrito.");
//                return;
//            }
//
//            System.out.println("🎤 Usuário: " + transcribed);
//
//            String response = chatService.generateResponse(transcribed);
//            System.out.println("🤖 IA: " + response);
//
//            byte[] audio = speechService.synthesize(response);
//
//            if (session.isOpen()) {
//                session.sendMessage(new BinaryMessage(audio));
//                System.out.println("📤 Resposta enviada com sucesso.");
//            }
//
//        } catch (Exception e) {
//            System.err.println("❌ Erro ao processar áudio.");
//            e.printStackTrace();
//        } finally {
//            try {
//                Files.deleteIfExists(new File(filePath).toPath());
//            } catch (IOException ignored) {
//            }
//
//            // Limpa somente depois de deletar
//            audioFilePaths.remove(sessionId);
//            processingFlags.remove(sessionId);
//        }
//    }

    private void createNewAudioBuffer(String sessionId) throws IOException {
        String filePath = "audio_" + UUID.randomUUID() + ".webm";
        FileOutputStream fos = new FileOutputStream(filePath);
        audioBuffers.put(sessionId, fos);
        audioFilePaths.put(sessionId, filePath);
        System.out.println("🆕 Novo buffer de áudio criado para sessão: " + sessionId);
    }

    private void cleanupSession(String sessionId) {
        try {
            FileOutputStream fos = audioBuffers.get(sessionId);
            if (fos != null) fos.close();

            String filePath = audioFilePaths.get(sessionId);
            if (filePath != null) Files.deleteIfExists(new File(filePath).toPath());
        } catch (IOException ignored) {
        }

        audioBuffers.remove(sessionId);
        audioFilePaths.remove(sessionId);
        processingFlags.remove(sessionId);
    }
}
