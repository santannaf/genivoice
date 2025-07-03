package santannaf.demo.genivoice.genivoice.service;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço que envia o áudio para a API Whisper da OpenAI
 * e retorna o texto transcrito da fala.
 */
@Service
public class TranscriptionService {

    private final String apiKey = System.getenv("OPENAI_API_KEY");

    public String transcribe(String filePath) {
        try {
            File file = new File(filePath);
            String boundary = "----JavaBoundary";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(buildMultipart(file, boundary))
                    .build();

            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return new JSONObject(response.body()).getString("text");
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static HttpRequest.BodyPublisher buildMultipart(File file, String boundary) throws IOException {
        List<byte[]> parts = new ArrayList<>();

        parts.add(("--" + boundary + "\r\n").getBytes());
        parts.add(("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n").getBytes());
        parts.add("Content-Type: audio/webm\r\n\r\n".getBytes());
        parts.add(Files.readAllBytes(file.toPath()));
        parts.add("\r\n".getBytes());

        parts.add(("--" + boundary + "\r\n").getBytes());
        parts.add("Content-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-1\r\n".getBytes());
        parts.add(("--" + boundary + "--\r\n").getBytes());

        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }
}

