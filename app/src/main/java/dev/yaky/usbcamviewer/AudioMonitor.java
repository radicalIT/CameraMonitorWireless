package dev.yaky.usbcamviewer;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class AudioMonitor extends Thread {
    private boolean shouldRun = false;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private final Context context;

    // Flaga bezpieczeństwa - czy słuchawki są podpięte?
    private volatile boolean isHeadsetConnected = false;
    private final AudioManager audioManager;
    private final AudioDeviceCallback audioCallback;

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    public AudioMonitor(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // Definiujemy callback, który system wywoła, gdy coś podłączysz/odłączysz
        this.audioCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                checkHeadsetStatus();
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                checkHeadsetStatus();
            }
        };
    }

    // Metoda sprawdzająca, czy bezpieczne wyjście jest dostępne
    private void checkHeadsetStatus() {
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        boolean safeDeviceFound = false;

        for (AudioDeviceInfo device : devices) {
            int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || // Słuchawki BT (Muzyka)
                    type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||  // Słuchawki BT (Rozmowa)
                    type == AudioDeviceInfo.TYPE_USB_HEADSET ||    // Słuchawki USB
                    type == AudioDeviceInfo.TYPE_USB_DEVICE) {     // DAC USB

                safeDeviceFound = true;
                break;
            }
        }

        isHeadsetConnected = safeDeviceFound;
        Log.d("AudioMonitor", "Headset Connected: " + isHeadsetConnected);
    }

    public void startMonitoring() {
        if (shouldRun) return;
        shouldRun = true;

        // Rejestrujemy nasłuchiwanie zmian sprzętu (musi być na głównym wątku)
        new Handler(Looper.getMainLooper()).post(() ->
                audioManager.registerAudioDeviceCallback(audioCallback, null)
        );

        // Pierwsze sprawdzenie od razu
        checkHeadsetStatus();

        start();
    }

    public void stopMonitoring() {
        shouldRun = false;
        // Wyrejestrowanie callbacka
        new Handler(Looper.getMainLooper()).post(() ->
                audioManager.unregisterAudioDeviceCallback(audioCallback)
        );

        try {
            join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING);
        int trackBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, ENCODING);

        try {
            // --- SZUKANIE MIKROFONU USB (KAMERY) ---
            AudioDeviceInfo[] inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            AudioDeviceInfo usbMic = null;
            for (AudioDeviceInfo device : inputDevices) {
                if (device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        device.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    usbMic = device;
                    break;
                }
            }

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_IN,
                    ENCODING,
                    minBufferSize * 2
            );

            if (usbMic != null) {
                audioRecord.setPreferredDevice(usbMic);
            }

            audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    ENCODING, trackBufferSize * 2, AudioTrack.MODE_STREAM
            );

            audioRecord.startRecording();
            audioTrack.play();

            byte[] buffer = new byte[minBufferSize];

            // PĘTLA GŁÓWNA
            while (shouldRun) {
                // Zawsze czytamy z mikrofonu (żeby bufor się nie zapchał)
                int read = audioRecord.read(buffer, 0, buffer.length);

                if (read > 0) {
                    // Piszemy na głośnik TYLKO jeśli są słuchawki
                    if (isHeadsetConnected) {
                        // Opcjonalnie: można tu dodać regulację głośności (mnożenie próbek)
                        audioTrack.write(buffer, 0, read);
                    } else {
                        // Jeśli brak słuchawek - ignorujemy dane (cisza)
                        // Bufor po prostu zostanie nadpisany w następnym cyklu
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (audioRecord != null) {
                try { audioRecord.stop(); audioRecord.release(); } catch (Exception e) {}
            }
            if (audioTrack != null) {
                try { audioTrack.stop(); audioTrack.release(); } catch (Exception e) {}
            }
        }
    }
}