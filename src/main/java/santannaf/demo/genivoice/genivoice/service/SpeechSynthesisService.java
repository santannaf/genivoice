package santannaf.demo.genivoice.genivoice.service;

import org.springframework.ai.openai.audio.speech.SpeechModel;
import org.springframework.stereotype.Service;

/**
 * Serviço que transforma a resposta em texto em áudio
 * utilizando o serviço de TTS da OpenAI.
 */
@Service
public class SpeechSynthesisService {

    private final SpeechModel model;

    public SpeechSynthesisService(SpeechModel model) {
        this.model = model;
    }

    public byte[] synthesize(String text) {
        return model.call(text);
    }
}
