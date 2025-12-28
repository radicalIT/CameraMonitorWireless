package dev.yaky.usbcamviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MjpegPlayer extends Thread {
    private final String streamUrl;
    private final FrameCallback callback;
    private boolean isRunning = true;

    public interface FrameCallback {
        void onFrame(Bitmap bitmap);
        void onError(String message); // Nowa metoda
    }
    public MjpegPlayer(String url, FrameCallback callback) {
        this.streamUrl = url;
        this.callback = callback;
    }

    public void stopPlayer() { isRunning = false; }

    @Override
    public void run() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(streamUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000); // 5 sekund na nawiązanie połączenia
            connection.setReadTimeout(5000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                callback.onError("Błąd HTTP: " + responseCode);
                return;
            }

            InputStream is = new BufferedInputStream(connection.getInputStream());
            MjpegInputStream mjpegStream = new MjpegInputStream(is);

            while (isRunning) {
                Bitmap bitmap = mjpegStream.readMjpegFrame();
                if (bitmap != null) {
                    callback.onFrame(bitmap);
                }
            }
        } catch (Exception e) {
            callback.onError(e.getMessage()); // Przesłanie błędu do UI
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // Klasa pomocnicza do wycinania JPEG z potoku danych
    private static class MjpegInputStream {
        private final InputStream in;
        public MjpegInputStream(InputStream in) { this.in = in; }

        public Bitmap readMjpegFrame() throws Exception {
            // Szukamy początku klatki (SOI - 0xFFD8)
            byte[] header = new byte[2];
            int bytesRead;
            while (true) {
                bytesRead = in.read();
                if (bytesRead == -1) return null;
                if (bytesRead == 0xFF) {
                    int secondByte = in.read();
                    if (secondByte == 0xD8) break;
                }
            }

            // Zbieramy dane do momentu znalezienia końca klatki (EOI - 0xFFD9)
            // lub do rozsądnego limitu (np. 512KB)
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            buffer.write(0xFF);
            buffer.write(0xD8);

            int prev = 0;
            int current;
            while ((current = in.read()) != -1) {
                buffer.write(current);
                if (prev == 0xFF && current == 0xD9) break;
                prev = current;
                if (buffer.size() > 512000) break; // Zabezpieczenie przed przepełnieniem
            }

            byte[] data = buffer.toByteArray();
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        }
    }
}