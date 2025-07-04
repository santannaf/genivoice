package santannaf.demo.genivoice.genivoice.service;

import org.springframework.ai.openai.audio.speech.SpeechMessage;
import org.springframework.ai.openai.audio.speech.SpeechModel;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.StreamingSpeechModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Serviço que transforma a resposta em texto em áudio
 * utilizando o serviço de TTS da OpenAI.
 */
@Service
public class SpeechSynthesisService {

    private final SpeechModel model;
    private final StreamingSpeechModel streamingSpeechModel;

    public SpeechSynthesisService(SpeechModel model, StreamingSpeechModel streamingSpeechModel) {
        this.model = model;
        this.streamingSpeechModel = streamingSpeechModel;
    }

    public byte[] synthesize(String text) {
        var message = new SpeechMessage(text);
        SpeechPrompt speechPrompt = new SpeechPrompt(message);
        return model.call(speechPrompt).getResult().getOutput();
    }

    public Flux<byte[]> streamSynthesize(String text) {
        return streamingSpeechModel.stream(text);
    }
}
