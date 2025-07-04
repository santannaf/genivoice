package santannaf.demo.genivoice.genivoice.websocket;

import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import reactor.core.publisher.Mono;
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
@Component
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {
    private final TranscriptionService transcriptionService;
    private final ChatService chatService;
    private final SpeechSynthesisService speechService;

    private final ConcurrentHashMap<String, AudioStream> streams = new ConcurrentHashMap<>();

    public VoiceWebSocketHandler(
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

//    private void processAudio(WebSocketSession session, AudioStream stream, int version) {
//        String filePath = stream.filePath;
//        boolean executed = false;
//        long totalStart = System.nanoTime();
//
//        try {
//            stream.fileOutputStream.flush();
//            System.out.println("🛠️ Iniciando transcrição do arquivo: " + filePath);
//            long t1 = System.nanoTime();
//
//            String transcribed = transcriptionService.transcribe(filePath);
//
//            long t2 = System.nanoTime();
//
//            if (transcribed == null || transcribed.isBlank()) {
//                System.out.println("⚠️ Nada transcrito.");
//                return;
//            }
//
//            System.out.println("🎤 Usuário: " + transcribed);
//            System.out.println("⏱️ Tempo de transcrição: " + formatTime(t1, t2));
//
//            long t3 = System.nanoTime();
//            String response = chatService.generateResponse(transcribed);
//            long t4 = System.nanoTime();
//
//            System.out.println("🤖 IA: " + response);
//            System.out.println("⏱️ Tempo de geração de resposta (GPT): " + formatTime(t3, t4));
//

    /// /            long t5 = System.nanoTime();
    /// /            byte[] audio = speechService.synthesize(response);
    /// /            long t6 = System.nanoTime();
    /// /            System.out.println("⏱️ Tempo de síntese de voz (TTS): " + formatTime(t5, t6));
//
//            System.out.println("🗣️ Iniciando streaming de TTS...");
//            long ttsStart = System.currentTimeMillis();
//
//            Flux<byte[]> audioStream = speechService.streamSynthesize(response);
//            audioStream
//                    .map(BinaryMessage::new)
//                    .doOnNext(msg -> {
//                        try {
//                            session.sendMessage(msg);
//                        } catch (IOException e) {
//                            System.err.println("❌ Erro ao enviar parte do áudio.");
//                            e.printStackTrace();
//                        }
//                    })
//                    .doOnComplete(() -> {
//                        long ttsEnd = System.currentTimeMillis();
//                        System.out.println("✅ Fim do stream de áudio. Tempo de TTS: " + (ttsEnd - ttsStart) + " ms");
//                    })
//                    .subscribe();
//
//            synchronized (stream) {
//                if (version != stream.version) return;
//
//                this.streams.remove(session.getId(), stream);
//                stream.removed = true;
//            }
//
//            executed = true;
//            session.sendMessage(new BinaryMessage(audio));
//            System.out.println("📤 Resposta enviada com sucesso.");
//        } catch (Exception error) {
//            System.err.println("❌ Erro ao processar áudio.");
//            error.printStackTrace();
//        } finally {
//            try {
//                if (executed) {
//                    stream.fileOutputStream.close();
//                    Files.deleteIfExists(new File(filePath).toPath());
//                }
//            } catch (IOException ignored) {
//            }
//        }
//    }
    private void processAudio(WebSocketSession session, AudioStream stream, int version) {
        String filePath = stream.filePath;
        try {
            stream.fileOutputStream.flush();
            System.out.println("🛠️ Iniciando transcrição do arquivo: " + filePath);
            long t1 = System.nanoTime();

            Mono.fromCallable(() -> transcriptionService.transcribe(filePath))
                    .doOnNext(transcribed -> {
                        long t2 = System.nanoTime();
                        System.out.println("⏱️Tempo de transcrição: " + formatTime(t1, t2));

                        if (transcribed == null || transcribed.isBlank()) {
                            System.out.println("⚠️Nada transcrito.");
                            throw new RuntimeException("Transcrição vazia");
                        }

                        System.out.println("🎤Usuário: " + transcribed);
                    })
                    .flatMap(transcribed -> Mono.fromCallable(() -> chatService.generateResponse(transcribed))
                            .doOnNext(response -> {
                                long t4 = System.nanoTime();
                                System.out.println("🤖IA: " + response);
                                System.out.println("⏱️Tempo de geração de resposta (GPT): " + formatTime(t1, t4));
                            }))
                    .flatMapMany(response -> {
                        System.out.println("🗣️ Iniciando streaming de TTS...");
                        long ttsStart = System.currentTimeMillis();

                        return speechService.streamSynthesize(response)
                                .map(BinaryMessage::new)
                                .doOnNext(msg -> {
                                    try {
                                        session.sendMessage(msg);
                                    } catch (IOException e) {
                                        System.err.println("❌Erro ao enviar parte do áudio.");
                                        e.printStackTrace();
                                    }
                                })
                                .doOnComplete(() -> {
                                    long ttsEnd = System.currentTimeMillis();
                                    System.out.println("✅ Fim do stream de áudio. Tempo de TTS: " + (ttsEnd - ttsStart) + " ms");
                                });
                    })
                    .doOnComplete(() -> {
                        synchronized (stream) {
                            if (version != stream.version) return;
                            streams.remove(session.getId(), stream);
                            stream.removed = true;
                        }
                        System.out.println("📤 Resposta enviada com sucesso.");
                    })
                    .doFinally(signalType -> {
                        try {
                            stream.fileOutputStream.close();
                            Files.deleteIfExists(new File(filePath).toPath());
                        } catch (IOException ignored) {
                        }
                    })
                    .subscribe();

        } catch (Exception error) {
            System.err.println("❌ Erro ao processar áudio.");
            error.printStackTrace();
        }
    }

    private String formatTime(long start, long end) {
        long millis = (end - start) / 1_000_000;
        return millis + " ms";
    }
}
