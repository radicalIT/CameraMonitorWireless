package dev.yaky.usbcamviewer;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.SoundEffectConstants;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.nio.ByteBuffer;
import java.util.List;

import android.view.MotionEvent; // Do obsługi dotyku
import android.view.ScaleGestureDetector; // Do wykrywania gestu "szczypania"
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    // Stałe klucze do SharedPreferences
    private static final String PREF_SHOW_LUT = "showLut";
    private static final String PREF_SHOW_ZEBRA = "showZebra";
    private static final String PREF_SHOW_FALSE_COLOR = "showFalseColor";
    private static final String PREF_SHOW_PEAKING = "showPeaking";
    private static final String PREF_IS_MIRRORED = "isMirrored";
    private static final String PREF_ZEBRA_THRESHOLD = "zebraThreshold";
    private static final String PREF_PEAKING_THRESHOLD = "peakingThreshold";
    private static final String PREF_PEAKING_COLOR_INDEX = "peakingColorIndex";
    private static final String PREF_PEAKING_SENS_PROGRESS = "peakingSensProgress";
    private static final String PREF_ANAMORPHIC_MODE = "anamorphicMode";
    private static final String PREF_GRID_VISIBLE = "gridVisible";
    private static final String PREF_SCOPE_STATE = "scopeState";
    private static final String PREF_SCOPES_SIZE_PROGRESS = "scopesSizeProgress";
    private static final String PREF_SCOPE_DELAY_MS = "scopeDelayMs";
    private static final String PREF_FULL_BRIGHTNESS = "fullBright";
    private static final String PREF_STREAM_URL = "streamUrl"; //

    // Stałe wartości opóźnień dla wykresów (w milisekundach)
    private static final int DELAY_REALTIME = 0;
    private static final int DELAY_10_FRAMES = 333;  // ok. 10 klatek przy 30fps
    private static final int DELAY_15_FRAMES = 500;  // ok. 15 klatek przy 30fps
    private static final int DELAY_30_FRAMES = 1000; // ok. 30 klatek przy 30fps

    // Stałe kolory przycisków
    private static final int COLOR_ICON_OFF = android.graphics.Color.parseColor("#DDDDDD");
    private static final int COLOR_ICON_ON  = android.graphics.Color.parseColor("#FF9900");

    // Executor do operacji w tle, np. zapisu plików
    //    private final java.util.concurrent.ExecutorService backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private MjpegPlayer mjpegPlayer;
    private EditText etStreamUrl;
//    private AudioMonitor audioMonitor;
    private android.opengl.GLSurfaceView mSurfaceView;
    private CameraRenderer mRenderer;
    private ScaleGestureDetector mScaleGestureDetector;
    private android.view.GestureDetector gestureDetector;
    private float mScaleFactor = 1.0f;
    private float mPosX = 0f;
    private float mPosY = 0f;
    private float mLastTouchX;
    private float mLastTouchY;
    private static final int INVALID_POINTER_ID = -1;
    private int mActivePointerId = INVALID_POINTER_ID;
    private View noSignalScreen;

    // Dodaj pola do klasy:
    private View uiContainer;
    private GridOverlayView gridOverlay;
    private float mAnamorphicScaleY = 1.0f;
    private int anamorphicMode = 0; // 0=OFF, 1=1.33x, 2=1.55x

    // --- ZMIENNE DO WYKRESÓW ---
    private static final int SCOPE_OFF = 0;
    private static final int SCOPE_HISTOGRAM = 1;
    private static final int SCOPE_PARADE = 2;

    private int currentScopeState = SCOPE_OFF; // Domyślnie wyłączone
    private long lastHistogramTime = 0;
    private HistogramView histogramView;
    private ParadeView paradeView;
    private FalseColorLegendView legendView;

    private HandlerThread processingThread;
    private Handler processingHandler;
    private boolean isProcessingFrame = false;
    private byte[] processingBuffer;
    private boolean requestSnapshot = false;

    private int scopeDelayMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Konfiguracja systemowa i uprawnienia
        setupWindow();
//        checkPermissions();

        setContentView(R.layout.activity_main);

        // 2. Inicjalizacja komponentów
        initViews();
        setupRenderer();
        setupGestures();

        // 3. Logika interakcji
        setupQuickActions();
        setupAdvancedPanel();

        // 4. Inicjalizacja sprzętu i ustawień
//        mUsbMonitor = new USBMonitor(this, mUsbMonitorOnDeviceConnectListener);
        processingThread = new HandlerThread("FrameProcessor");
        processingThread.start();
        processingHandler = new Handler(processingThread.getLooper());

        loadSettings();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // loadSettings samo wywoła startMjpegConnection z zapisanym adresem URL
        loadSettings();
    }

    @Override
    protected void onStop() {
        saveSettings(); // Zapisz przed wyjściem

        mjpegPlayer.stopPlayer();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mjpegPlayer.stopPlayer();
        super.onDestroy();
    }

    /**
     * Zapisuje aktualne ustawienia aplikacji w SharedPreferences.
     */
    private void saveSettings() {
        android.content.SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();

        // 0. Url
        editor.putString(PREF_STREAM_URL, etStreamUrl.getText().toString());
        editor.apply();

        // 1. Ustawienia renderera
        if (mRenderer != null) {
            editor.putBoolean(PREF_SHOW_LUT, mRenderer.showLut);
            editor.putBoolean(PREF_SHOW_ZEBRA, mRenderer.showZebra);
            editor.putBoolean(PREF_SHOW_FALSE_COLOR, mRenderer.showFalseColor);
            editor.putBoolean(PREF_SHOW_PEAKING, mRenderer.showPeaking);
            editor.putBoolean(PREF_IS_MIRRORED, mRenderer.isMirrored);
            editor.putFloat(PREF_ZEBRA_THRESHOLD, mRenderer.zebraThreshold);
            editor.putFloat(PREF_PEAKING_THRESHOLD, mRenderer.peakingThreshold);

            android.widget.RadioGroup rgPeak = findViewById(R.id.rg_peaking_color);
            int peakId = rgPeak.getCheckedRadioButtonId();
            int peakIndex = (peakId == R.id.rb_peak_red) ? 0 : (peakId == R.id.rb_peak_blue) ? 2 : 1; // 1 (Green) jako domyślny
            editor.putInt(PREF_PEAKING_COLOR_INDEX, peakIndex);
        }

        // 2. Czułość peakingu
        android.widget.SeekBar sbPeaking = findViewById(R.id.sb_peaking_sens);
        editor.putInt(PREF_PEAKING_SENS_PROGRESS, sbPeaking.getProgress());

        // 3. Tryb anamorficzny
        android.widget.RadioGroup rgAna = findViewById(R.id.rg_anamorphic);
        int selectedAnaId = rgAna.getCheckedRadioButtonId();
        int anaSaveMode = (selectedAnaId == R.id.rb_ana_133) ? 1 : (selectedAnaId == R.id.rb_ana_155) ? 2 : 0;
        editor.putInt(PREF_ANAMORPHIC_MODE, anaSaveMode);

        // 4. Interfejs użytkownika
        editor.putBoolean(PREF_GRID_VISIBLE, gridOverlay.getVisibility() == android.view.View.VISIBLE);
        editor.putInt(PREF_SCOPE_STATE, currentScopeState);

        android.widget.SeekBar sbSize = findViewById(R.id.sb_scopes_size);
        editor.putInt(PREF_SCOPES_SIZE_PROGRESS, sbSize.getProgress());

        // 5. Opóźnienie wykresów
        android.widget.RadioGroup rgSkip = findViewById(R.id.rg_scope_skip);
        int checkedId = rgSkip.getCheckedRadioButtonId();
        int delay = DELAY_REALTIME;
        if (checkedId == R.id.rb_skip_10) delay = DELAY_10_FRAMES;
        else if (checkedId == R.id.rb_skip_15) delay = DELAY_15_FRAMES;
        else if (checkedId == R.id.rb_skip_30) delay = DELAY_30_FRAMES;
        editor.putInt(PREF_SCOPE_DELAY_MS, delay);

        // 6. Jasność ekranu
        android.view.WindowManager.LayoutParams layout = getWindow().getAttributes();
        editor.putBoolean(PREF_FULL_BRIGHTNESS, layout.screenBrightness >= 1.0f);

        editor.apply();
    }

    /**
     * Wczytuje ustawienia aplikacji z SharedPreferences i aktualizuje UI.
     */
    private void loadSettings() {
        android.content.SharedPreferences pref = getPreferences(MODE_PRIVATE);

        // 0. Link
        String savedUrl = pref.getString(PREF_STREAM_URL, "http://10.42.0.1:8080/stream");
        etStreamUrl.setText(savedUrl);
        startMjpegConnection(savedUrl);

        // 1. Ustawienia renderera
        mRenderer.showLut = pref.getBoolean(PREF_SHOW_LUT, false);
        mRenderer.isMirrored = pref.getBoolean(PREF_IS_MIRRORED, false);
        mRenderer.showZebra = pref.getBoolean(PREF_SHOW_ZEBRA, false);
        mRenderer.showPeaking = pref.getBoolean(PREF_SHOW_PEAKING, false);
        mRenderer.showFalseColor = pref.getBoolean(PREF_SHOW_FALSE_COLOR, false);

        ((android.widget.Switch) findViewById(R.id.sw_lut_enable)).setChecked(mRenderer.showLut);
        ((android.widget.Switch) findViewById(R.id.sw_mirror)).setChecked(mRenderer.isMirrored);

        // 2. Peaking (kolor i czułość)
        int peakIndex = pref.getInt(PREF_PEAKING_COLOR_INDEX, 1); // 1 = Green (domyślnie)
        android.widget.RadioGroup rgPeakColor = findViewById(R.id.rg_peaking_color);
        if (peakIndex == 0) {
            mRenderer.peakingColorRGB = new float[]{1.0f, 0.0f, 0.0f};
            rgPeakColor.check(R.id.rb_peak_red);
        } else if (peakIndex == 2) {
            mRenderer.peakingColorRGB = new float[]{0.0f, 0.0f, 1.0f};
            rgPeakColor.check(R.id.rb_peak_blue);
        } else {
            mRenderer.peakingColorRGB = new float[]{0.0f, 1.0f, 0.0f};
            rgPeakColor.check(R.id.rb_peak_green);
        }

        int pSens = pref.getInt(PREF_PEAKING_SENS_PROGRESS, 50);
        mRenderer.peakingThreshold = 0.01f + ((100 - pSens) / 100.0f) * 0.20f;
        ((android.widget.SeekBar) findViewById(R.id.sb_peaking_sens)).setProgress(pSens);

        // 3. Zebra
        float zThresh = pref.getFloat(PREF_ZEBRA_THRESHOLD, 0.95f);
        mRenderer.zebraThreshold = zThresh;
        int zebraCheckId = (zThresh > 0.8f) ? R.id.rb_zebra_95 : R.id.rb_zebra_70;
        ((android.widget.RadioGroup) findViewById(R.id.rg_zebra_level)).check(zebraCheckId);

        // 4. Tryb anamorficzny
        anamorphicMode = pref.getInt(PREF_ANAMORPHIC_MODE, 0);
        android.widget.RadioGroup rgAna = findViewById(R.id.rg_anamorphic);
        if (anamorphicMode == 1) {
            mAnamorphicScaleY = 1.0f / 1.33f;
            rgAna.check(R.id.rb_ana_133);
        } else if (anamorphicMode == 2) {
            mAnamorphicScaleY = 1.0f / 1.55f;
            rgAna.check(R.id.rb_ana_155);
        } else {
            mAnamorphicScaleY = 1.0f;
            rgAna.check(R.id.rb_ana_off);
        }

        // 5. Wykresy i siatka
        currentScopeState = pref.getInt(PREF_SCOPE_STATE, 0);
        boolean gridVisible = pref.getBoolean(PREF_GRID_VISIBLE, false);
        gridOverlay.setVisibility(gridVisible ? android.view.View.VISIBLE : android.view.View.GONE);

        int sSize = pref.getInt(PREF_SCOPES_SIZE_PROGRESS, 100);
        ((android.widget.SeekBar) findViewById(R.id.sb_scopes_size)).setProgress(sSize);
        applyScopeSize(sSize);

        // 6. Opóźnienie wykresów
        scopeDelayMs = pref.getInt(PREF_SCOPE_DELAY_MS, DELAY_REALTIME);
        android.widget.RadioGroup rgSkipLoad = findViewById(R.id.rg_scope_skip);
        if (scopeDelayMs == DELAY_10_FRAMES) rgSkipLoad.check(R.id.rb_skip_10);
        else if (scopeDelayMs == DELAY_15_FRAMES) rgSkipLoad.check(R.id.rb_skip_15);
        else if (scopeDelayMs == DELAY_30_FRAMES) rgSkipLoad.check(R.id.rb_skip_30);
        else rgSkipLoad.check(R.id.rb_skip_1);

        // 7. Jasność ekranu
        android.view.WindowManager.LayoutParams layout = getWindow().getAttributes();
        boolean isMaxBrightness = pref.getBoolean(PREF_FULL_BRIGHTNESS, false);
        layout.screenBrightness = isMaxBrightness ? 1.0f : android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(layout);

        // 8. Zastosowanie zmian
        applyTransformation();
        updateUIColors();
    }



    private void saveImageToGallery(byte[] nv21, int width, int height) {
        // Logujemy próbę zapisu
        android.util.Log.d("SNAPSHOT", "Próba zapisu: " + width + "x" + height + " rozmiar danych: " + (nv21 != null ? nv21.length : "null"));

        if (nv21 == null || width <= 0 || height <= 0) {
            runOnUiThread(() -> android.widget.Toast.makeText(this, "Błąd: Brak danych obrazu", android.widget.Toast.LENGTH_SHORT).show());
            return;
        }

        try {
            // 1. Konwersja NV21 (YUV) do JPEG
            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                    nv21,
                    android.graphics.ImageFormat.NV21,
                    width,
                    height,
                    null
            );

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            // Próba kompresji
            boolean success = yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 95, out);

            if (!success) {
                android.util.Log.e("SNAPSHOT", "Kompresja YuvImage nieudana! Sprawdź wymiary.");
                runOnUiThread(() -> android.widget.Toast.makeText(this, "Błąd kompresji (złe wymiary?)", android.widget.Toast.LENGTH_SHORT).show());
                return;
            }

            byte[] imageBytes = out.toByteArray();

            // 2. Zapis do MediaStore
            android.content.ContentValues values = new android.content.ContentValues();
            String fileName = "USBCam_" + System.currentTimeMillis() + ".jpg";

            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/USBCamera");
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1); // Ważne dla Androida 10+

            android.content.ContentResolver resolver = getContentResolver();
            android.net.Uri uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                java.io.OutputStream outputStream = resolver.openOutputStream(uri);
                outputStream.write(imageBytes);
                outputStream.close();

                // Odznaczamy jako pending (plik gotowy)
                values.clear();
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);

                android.util.Log.d("SNAPSHOT", "Zapisano pomyślnie: " + uri.toString());

                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "Zapisano zdjęcie!", android.widget.Toast.LENGTH_SHORT).show();
                });
            } else {
                android.util.Log.e("SNAPSHOT", "Nie udało się utworzyć wpisu w MediaStore (URI is null)");
            }
        } catch (Exception e) {
            e.printStackTrace();
            android.util.Log.e("SNAPSHOT", "Wyjątek przy zapisie: " + e.getMessage());
            runOnUiThread(() ->
                    android.widget.Toast.makeText(this, "Błąd zapisu: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show()
            );
        }
    }
    private void handleSnapshot(java.nio.ByteBuffer frame, com.serenegiant.usb.UVCCamera camera) {
        // 1. Pobieramy wymiary zdjęcia
        com.serenegiant.usb.Size snapSize = camera.getPreviewSize();
        int w = (snapSize != null) ? snapSize.width : 1920;
        int h = (snapSize != null) ? snapSize.height : 1080;

        android.util.Log.d("SNAPSHOT", "Rozpoczynam zapis: " + w + "x" + h);

        // 2. Sprawdzamy czy bufor ma dane
        if (frame.capacity() < w * h * 1.5) { // 1.5 dla formatu NV21/YUV420
            android.util.Log.e("SNAPSHOT", "Bufor za mały!");
            return;
        }

        // 3. Kopiujemy dane (bezpiecznie)
        byte[] data = new byte[frame.capacity()];
        frame.clear();     // Reset pozycji przed czytaniem
        frame.get(data);   // Kopiowanie do tablicy
        frame.rewind();    // Cofnięcie pozycji, aby biblioteka kamery mogła dalej używać bufora

        // 4. Uruchamiamy zapis w tle (Twoja funkcja zapisu)
        new Thread(() -> {
            saveImageToGallery(data, w, h);
        }).start();
    }
    private void calculateLumaHistogramFromPixels(int[] pixels) {
        int[] lumaBins = new int[256];

        // Próbkowanie co 10 pikseli dla wydajności
        for (int i = 0; i < pixels.length; i += 10) {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // Obliczanie jasności (Luma)
            int luma = (int)(0.299f * r + 0.587f * g + 0.114f * b);
            lumaBins[luma]++;
        }

        runOnUiThread(() -> {
            if (histogramView != null) {
                // Przekazujemy tylko jedną tablicę
                histogramView.updateLumaData(lumaBins);
            }
        });
    }

    private void saveBitmapToGallery(Bitmap bitmap) {
        String fileName = "MJPEG_" + System.currentTimeMillis() + ".jpg";
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MJPEGCamera");

        android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            bitmap.recycle(); // Czyścimy kopię
            runOnUiThread(() -> android.widget.Toast.makeText(this, "Zdjęcie zapisane!", android.widget.Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }

        // 1. Obsługa zoomu (priorytet)
        mScaleGestureDetector.onTouchEvent(event);

        final int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final int pointerIndex = event.getActionIndex();
                final float x = event.getX(pointerIndex);
                final float y = event.getY(pointerIndex);

                // Zapamiętujemy, gdzie dotknęliśmy
                mLastTouchX = x;
                mLastTouchY = y;
                mActivePointerId = event.getPointerId(0);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = event.findPointerIndex(mActivePointerId);
                if (pointerIndex == -1) break;

                final float x = event.getX(pointerIndex);
                final float y = event.getY(pointerIndex);

                // Obliczamy różnicę ruchu
                final float dx = x - mLastTouchX;
                final float dy = y - mLastTouchY;

                // W onTouchEvent w case ACTION_MOVE:
                if (!mScaleGestureDetector.isInProgress() && mScaleFactor > 1.0f) {
                    mPosX += dx;
                    mPosY += dy;
                    // Tu usunąłem logikę "maxDx/clamp", żeby nie blokowała zoomu na krawędziach
//                    mSurfaceView.setTranslationX(mPosX);
//                    mSurfaceView.setTranslationY(mPosY);
                    applyTransformation();
                }

                mLastTouchX = x;
                mLastTouchY = y;
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                // Obsługa sytuacji, gdy podnosimy jeden z palców (żeby obraz nie skakał)
                final int pointerIndex = event.getActionIndex();
                final int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mLastTouchX = event.getX(newPointerIndex);
                    mLastTouchY = event.getY(newPointerIndex);
                    mActivePointerId = event.getPointerId(newPointerIndex);
                }
                break;
            }
        }
        return true;
    }

    // Klasa obsługująca gest przybliżania/oddalania z uwzględnieniem punktu skupienia (Focus Point)
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleChange = detector.getScaleFactor();
            float newScale = mScaleFactor * scaleChange;

            // Ograniczenia zoomu (1.0x - 5.0x)
            newScale = Math.max(1.0f, Math.min(newScale, 5.0f));

            // Obliczamy faktyczną zmianę skali po ograniczeniach
            // (ważne, gdybyśmy dotarli do granicy 5.0x lub 1.0x)
            float finalScaleRatio = newScale / mScaleFactor;

            // Aktualizujemy główny czynnik skali
            mScaleFactor = newScale;

            // --- MATEMATYKA MAGICZNA ---
            // Obliczamy środek gestu (gdzie są palce)
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            // Obliczamy odległość palców od środka ekranu
            float dx = focusX - (mSurfaceView.getWidth() / 2f);
            float dy = focusY - (mSurfaceView.getHeight() / 2f);

            // Przesuwamy obraz, aby skompensować "uciekanie" punktu pod palcami.
            // Wzór: Przesunięcie -= (PozycjaPalca - Środek - AktualnePrzesunięcie) * (ZmianaSkali - 1)
            mPosX -= (dx - mPosX) * (finalScaleRatio - 1);
            mPosY -= (dy - mPosY) * (finalScaleRatio - 1);

            // Aplikujemy zmiany
            applyTransformation();

            // Jeśli wróciliśmy do 1.0x, resetujemy pozycję idealnie do zera, żeby obraz był wycentrowany
            if (mScaleFactor <= 1.0f) {
                mPosX = 0f;
                mPosY = 0f;
                applyTransformation();
            }

            return true;
        }
    }


    private void setupWindow() {
        EdgeToEdge.enable(this);
        var flags = WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
        getWindow().setFlags(flags, flags);
    }

//    private void checkPermissions() {
//        String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
//        boolean needRequest = false;
//        for (String p : permissions) {
//            if (ContextCompat.checkSelfPermission(this, p) == PERMISSION_DENIED) needRequest = true;
//        }
//
//        if (needRequest) {
//            ActivityCompat.requestPermissions(this, permissions, 0);
//        }
//    }

    private void initViews() {
        mSurfaceView = findViewById(R.id.camera_surface_view);
        uiContainer = findViewById(R.id.ui_container);
        gridOverlay = findViewById(R.id.grid_overlay);
        legendView = findViewById(R.id.legend_false_color);
        noSignalScreen = findViewById(R.id.layout_no_signal);
        histogramView = findViewById(R.id.histogram_view);
        paradeView = findViewById(R.id.parade_view);
    }

    private void setupRenderer() {
        mSurfaceView.setEGLContextClientVersion(2);
        mRenderer = new CameraRenderer(this, mSurfaceView);
        mSurfaceView.setRenderer(mRenderer);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                View panel = findViewById(R.id.panel_advanced);
                if (panel != null && panel.getVisibility() == View.VISIBLE) {
                    panel.setVisibility(View.GONE);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                boolean isVisible = uiContainer.getVisibility() == View.VISIBLE;
                uiContainer.animate()
                        .alpha(isVisible ? 0f : 1f)
                        .setDuration(300)
                        .withStartAction(() -> { if (!isVisible) uiContainer.setVisibility(View.VISIBLE); })
                        .withEndAction(() -> { if (isVisible) uiContainer.setVisibility(View.GONE); });
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) { return true; }
        });

        mScaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
    }

    private void setupQuickActions() {
        // Grid
        ImageButton btnGrid = findViewById(R.id.btn_quick_grid);
        btnGrid.setOnClickListener(v -> {
            boolean show = gridOverlay.getVisibility() != View.VISIBLE;
            gridOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
            updateToggleButton(btnGrid, show);
        });

        // Zebra
        ImageButton btnZebra = findViewById(R.id.btn_quick_zebra);
        ImageButton btnFc = findViewById(R.id.btn_quick_fc);

        btnZebra.setOnClickListener(v -> {
            mRenderer.showZebra = !mRenderer.showZebra;
            if (mRenderer.showZebra) mRenderer.showFalseColor = false;

            updateToggleButton(btnZebra, mRenderer.showZebra);
            updateToggleButton(btnFc, mRenderer.showFalseColor);
            legendView.setVisibility(View.GONE);
        });

        // False Color
        btnFc.setOnClickListener(v -> {
            mRenderer.showFalseColor = !mRenderer.showFalseColor;
            if (mRenderer.showFalseColor) mRenderer.showZebra = false;

            updateToggleButton(btnFc, mRenderer.showFalseColor);
            updateToggleButton(btnZebra, mRenderer.showZebra);
            legendView.setVisibility(mRenderer.showFalseColor ? View.VISIBLE : View.GONE);
        });

        // Peaking
        ImageButton btnPeaking = findViewById(R.id.btn_quick_peaking);
        btnPeaking.setOnClickListener(v -> {
            mRenderer.showPeaking = !mRenderer.showPeaking;
            updateToggleButton(btnPeaking, mRenderer.showPeaking);
        });

        // Audio
//        ImageButton btnAudio = findViewById(R.id.btn_quick_audio);
//        btnAudio.setOnClickListener(v -> {
//            if (audioMonitor == null) {
//                audioMonitor = new AudioMonitor(this);
//                audioMonitor.startMonitoring();
//                updateToggleButton(btnAudio, true);
//            } else {
//                audioMonitor.stopMonitoring();
//                audioMonitor = null;
//                updateToggleButton(btnAudio, false);
//            }
//        });

        // Scopes (Histogram / Parade)
        ImageButton btnScope = findViewById(R.id.btn_quick_scope);
        btnScope.setOnClickListener(v -> {
            currentScopeState = (currentScopeState + 1) % 3; // Cykl 0,1,2

            histogramView.setVisibility(currentScopeState == SCOPE_HISTOGRAM ? View.VISIBLE : View.GONE);
            paradeView.setVisibility(currentScopeState == SCOPE_PARADE ? View.VISIBLE : View.GONE);

            updateToggleButton(btnScope, currentScopeState != SCOPE_OFF);
        });

        // Brightness Max Toggle
        ImageButton btnBright = findViewById(R.id.btn_quick_bright);
        btnBright.setOnClickListener(v -> {
            var params = getWindow().getAttributes();
            boolean isMax = params.screenBrightness > 0.99f;
            params.screenBrightness = isMax ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : 1.0f;
            getWindow().setAttributes(params);
            updateToggleButton(btnBright, !isMax);
        });

        // Galeria i Snapshot
        findViewById(R.id.btn_snapshot).setOnClickListener(v -> {
            requestSnapshot = true;
            v.playSoundEffect(SoundEffectConstants.CLICK);
        });

        findViewById(R.id.btn_gallery).setOnClickListener(v ->
                startActivity(new Intent(this, GalleryActivity.class)));
    }

    private void setupAdvancedPanel() {
        View panel = findViewById(R.id.panel_advanced);
        findViewById(R.id.btn_open_settings).setOnClickListener(v -> panel.setVisibility(View.VISIBLE));
        findViewById(R.id.btn_close_panel).setOnClickListener(v -> panel.setVisibility(View.GONE));

        etStreamUrl = findViewById(R.id.et_stream_url);

        findViewById(R.id.btn_update_stream).setOnClickListener(v -> {
            String newUrl = etStreamUrl.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                startMjpegConnection(newUrl);
                saveSettings(); // Zapisujemy od razu po aktualizacji
                android.widget.Toast.makeText(this, "Connecting to new URL...", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        // Switches
        Switch swLut = findViewById(R.id.sw_lut_enable);
        swLut.setChecked(mRenderer.showLut);
        swLut.setOnCheckedChangeListener((b, checked) -> mRenderer.showLut = checked);

        ((Switch) findViewById(R.id.sw_mirror)).setOnCheckedChangeListener((b, checked) -> mRenderer.isMirrored = checked);

        // Zebra & Peaking Groups
        ((RadioGroup) findViewById(R.id.rg_zebra_level)).setOnCheckedChangeListener((g, id) -> {
            mRenderer.zebraThreshold = (id == R.id.rb_zebra_95) ? 0.95f : 0.70f;
        });

        ((RadioGroup) findViewById(R.id.rg_peaking_color)).setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.rb_peak_red) mRenderer.peakingColorRGB = new float[]{1f, 0f, 0f};
            else if (id == R.id.rb_peak_green) mRenderer.peakingColorRGB = new float[]{0f, 1f, 0f};
            else if (id == R.id.rb_peak_blue) mRenderer.peakingColorRGB = new float[]{0f, 0f, 1f};
        });

        // SeekBars
        setupSeekBars();

        // Anamorphic & Scopes FPS
        ((RadioGroup) findViewById(R.id.rg_anamorphic)).setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.rb_ana_off) mAnamorphicScaleY = 1.0f;
            else if (id == R.id.rb_ana_133) mAnamorphicScaleY = 1.0f / 1.33f;
            else if (id == R.id.rb_ana_155) mAnamorphicScaleY = 1.0f / 1.55f;
            applyTransformation();
        });

        ((RadioGroup) findViewById(R.id.rg_scope_skip)).setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.rb_skip_10) scopeDelayMs = 333;
            else if (id == R.id.rb_skip_15) scopeDelayMs = 500;
            else if (id == R.id.rb_skip_30) scopeDelayMs = 1000;
            else scopeDelayMs = 0;
        });
    }

    private void setupSeekBars() {
        ((SeekBar) findViewById(R.id.sb_peaking_sens)).setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float threshold = 0.01f + ((100 - progress) / 100.0f) * 0.20f;
                mRenderer.peakingThreshold = threshold;
            }
        });

        ((SeekBar) findViewById(R.id.sb_scopes_size)).setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100 + progress, getResources().getDisplayMetrics());
                updateViewLayout(histogramView, sizePx * 2, sizePx);
                updateViewLayout(paradeView, sizePx * 2, sizePx);
            }
        });
    }

// --- HELPERY ---

    private void updateToggleButton(ImageButton btn, boolean active) {
        btn.setImageTintList(ColorStateList.valueOf(active ? COLOR_ICON_ON : COLOR_ICON_OFF));
    }

    private void updateViewLayout(View v, int width, int height) {
        v.getLayoutParams().width = width;
        v.getLayoutParams().height = height;
        v.requestLayout();
    }
    /**
     * Aktualizuje kolory ikon na pasku narzędzi w zależności od aktywowanych funkcji.
     */
    private void updateUIColors() {
        ImageButton btnZebra = findViewById(R.id.btn_quick_zebra);
        ImageButton btnFc = findViewById(R.id.btn_quick_fc);
        ImageButton btnPeaking = findViewById(R.id.btn_quick_peaking);
        ImageButton btnGrid = findViewById(R.id.btn_quick_grid);
        ImageButton btnBright = findViewById(R.id.btn_quick_bright);
//        ImageButton btnAudio = findViewById(R.id.btn_quick_audio);
        ImageButton btnScope = findViewById(R.id.btn_quick_scope);

        // 1. Stany z rendera
        updateToggleButton(btnZebra, mRenderer.showZebra);
        updateToggleButton(btnFc, mRenderer.showFalseColor);
        updateToggleButton(btnPeaking, mRenderer.showPeaking);

        // 2. Stan siatki (na podstawie widoczności View)
        updateToggleButton(btnGrid, gridOverlay.getVisibility() == View.VISIBLE);

        // 3. Stan jasności ekranu
        boolean isMaxBrightness = getWindow().getAttributes().screenBrightness >= 1.0f;
        updateToggleButton(btnBright, isMaxBrightness);

        // 4. Stan audio (jeśli masz przycisk audio)
//        updateToggleButton(btnAudio, audioMonitor != null);

        // 5. Stan Scopes (Histogram/Parade)
        updateToggleButton(btnScope, currentScopeState != SCOPE_OFF);
    }
    private void applyTransformation() {
        mSurfaceView.setScaleX(mScaleFactor);
        // Łączymy skale: zoom * anamorfika
        mSurfaceView.setScaleY(mScaleFactor * mAnamorphicScaleY);
        mSurfaceView.setTranslationX(mPosX);
        mSurfaceView.setTranslationY(mPosY);
    }
    private void applyScopeSize(int progress) {
        // Obliczamy wysokość w pikselach na podstawie dp (podstawa 100dp + postęp suwaka)
        int newHeightPx = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                100 + progress,
                getResources().getDisplayMetrics());

        // Szerokość ustawiamy na 2x wysokość (proporcja 2:1)
        int newWidthPx = newHeightPx * 2;

        // Aktualizacja Histogramu
        if (histogramView != null) {
            android.view.ViewGroup.LayoutParams lp = histogramView.getLayoutParams();
            lp.height = newHeightPx;
            lp.width = newWidthPx;
            histogramView.setLayoutParams(lp);
        }

        // Aktualizacja Parady
        if (paradeView != null) {
            android.view.ViewGroup.LayoutParams lp = paradeView.getLayoutParams();
            lp.height = newHeightPx;
            lp.width = newWidthPx;
            paradeView.setLayoutParams(lp);
        }
    }


    private final USBMonitor.OnDeviceConnectListener mUsbMonitorOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {

        @Override
        public void onAttach(UsbDevice device) {
        }

        @Override
        public void onDettach(UsbDevice device) {
        }

        @Override
        public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            UVCCamera camera = new UVCCamera();
            camera.open(ctrlBlock);

            var previewSize = camera.getSupportedSizeList().get(0);

            try {
                camera.setPreviewSize(previewSize.width, previewSize.height, UVCCamera.FRAME_FORMAT_MJPEG);
            } catch (final IllegalArgumentException e) {
                try {
                    camera.setPreviewSize(
                            previewSize.width,
                            previewSize.height,
                            30, // Min FPS: 30
                            30, // Max FPS: 30
                            UVCCamera.FRAME_FORMAT_MJPEG,
                            0.5f
                    );
                } catch (final IllegalArgumentException e1) {
                    camera.destroy();
                    return;
                }
            }

            // --- POPRAWKA CZARNEGO EKRANU ---
            // Czekamy chwilę na SurfaceTexture (max 2 sekundy)
            android.graphics.SurfaceTexture st = null;
            int attempts = 0;
            while (st == null && attempts < 20) {
                if (mRenderer != null) {
                    st = mRenderer.getSurfaceTexture();
                }
                if (st == null) {
                    try {
                        Thread.sleep(100); // Czekaj 100ms
                    } catch (InterruptedException e) { e.printStackTrace(); }
                }
                attempts++;
            }

            processingThread = new HandlerThread("FrameProcessor");
            processingThread.start();
            processingHandler = new Handler(processingThread.getLooper());

            if (st != null) {
                camera.setPreviewTexture(st);
                camera.startPreview();
                camera.setFrameCallback(frame -> {

                    // --- SNAPSHOT (Priorytet) ---
                    if (requestSnapshot) {
                        requestSnapshot = false;
                        // ... (Twój kod snapshotu jest OK, bo tam robisz kopię danych)
                        // Pamiętaj tylko o try-catch i logowaniu
                        handleSnapshot(frame, camera); // Wydziel to do metody dla czytelności
                        return; // Jak robimy zdjęcie, nie robimy histogramu w tej klatce
                    }

                    if (currentScopeState == SCOPE_OFF) return;

                    // --- ANALIZA (HISTOGRAM / PARADA) ---
                    long now = System.currentTimeMillis();
                    if (now - lastHistogramTime < scopeDelayMs) return; // Limit FPS

                    // Sprawdzamy, czy poprzednia analiza się zakończyła.
                    // Jeśli wątek ciągle mieli poprzednią klatkę, pomijamy tę (drop frame).
                    // To eliminuje "allocate new frame" warning.
                    if (isProcessingFrame) return;

                    lastHistogramTime = now;
                    isProcessingFrame = true; // Blokujemy flagę

                    // 1. Kopiujemy dane, póki mamy do nich dostęp (bezpieczeństwo!)
                    int len = frame.capacity();
                    if (processingBuffer == null || processingBuffer.length != len) {
                        processingBuffer = new byte[len];
                    }
                    frame.clear(); // Reset pozycji
                    frame.get(processingBuffer); // Kopiowanie do tablicy Java (szybkie)
                    frame.rewind(); // Przewijamy dla biblioteki

                    // 2. Zlecamy pracę stałemu wątkowi
                    final int stateCopy = currentScopeState; // Kopia stanu dla wątku

                    // Ważne: Sprawdź null (w przypadku zamknięcia apki)
                    if (processingHandler != null) {
//                        processingHandler.post(() -> {
//                            try {
//                                if (stateCopy == SCOPE_HISTOGRAM) {
//                                    calculateHistogram(java.nio.ByteBuffer.wrap(processingBuffer)); // Przerób metodę, by brała byte[]
//                                } else if (stateCopy == SCOPE_PARADE) {
//                                    // Update parady
//                                    if (paradeView != null) {
//                                        // Uwaga: updateData musi obsługiwać byte[] a nie ByteBuffer
//                                        paradeView.updateData(java.nio.ByteBuffer.wrap(processingBuffer), 1920, 1080);
//                                    }
//                                }
//                            } finally {
//                                // Zwalniamy blokadę, jesteśmy gotowi na nową klatkę
//                                isProcessingFrame = false;
//                            }
//                        });
                    } else {
                        isProcessingFrame = false;
                    }

                }, com.serenegiant.usb.UVCCamera.PIXEL_FORMAT_YUV420SP);

//                mCamera = camera;

                // UKRYJ EKRAN "NO SIGNAL"
                runOnUiThread(() -> {
                    if (noSignalScreen != null) {
                        noSignalScreen.setVisibility(android.view.View.GONE);
                    }
                });

                // Fix dla audio
//                if (audioMonitor == null) {
                    // Pamiętaj o przekazaniu MainActivity.this!
                    // audioMonitor = new AudioMonitor(MainActivity.this);
                    // (zakomentowane, bo włączasz to teraz z przycisku w menu)
//                }
            } else {
                // Log błędu - OpenGL nie wstał
                android.util.Log.e("CameraApp", "SurfaceTexture is null after waiting!");
            }
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
//            mCamera.stopPreview();
//            mCamera.close();
//            mCamera = null;
//            if (audioMonitor != null) {
//                audioMonitor.stopMonitoring();
//                audioMonitor = null;
//            }
            // POKAŻ EKRAN "NO SIGNAL"
            runOnUiThread(() -> {
                if (noSignalScreen != null) {
                    noSignalScreen.setVisibility(android.view.View.VISIBLE);
                }
            });
            if (processingThread != null) {
                processingThread.quitSafely();
                processingThread = null;
                processingHandler = null;
            }
        }

        @Override
        public void onCancel(UsbDevice device) {
        }
    };

    private void startMjpegConnection(String url) {
        // 1. Natychmiastowy reset wizualny
        if (mRenderer != null) {
            mRenderer.clearFrame(); // Czyścimy teksturę OpenGL
        }

        runOnUiThread(() -> {
            if (noSignalScreen != null) {
                noSignalScreen.setVisibility(View.VISIBLE); // Pokazujemy komunikat o braku sygnału
            }
        });

        if (mjpegPlayer != null) {
            mjpegPlayer.stopPlayer();
        }

        // 2. Nowy callback obsługujący błędy
        mjpegPlayer = new MjpegPlayer(url, new MjpegPlayer.FrameCallback() {
            @Override
            public void onFrame(Bitmap bitmap) {
                if (mRenderer != null) {
                    mRenderer.updateBitmap(bitmap);
                }
                processFrameData(bitmap);

                runOnUiThread(() -> {
                    if (noSignalScreen.getVisibility() == View.VISIBLE) {
                        noSignalScreen.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    // Wyświetlenie błędu użytkownikowi
                    android.widget.Toast.makeText(MainActivity.this,
                            "Błąd strumienia: " + message, Toast.LENGTH_LONG).show();

                    if (noSignalScreen != null) {
                        noSignalScreen.setVisibility(View.VISIBLE);
                    }

                    // Opcjonalnie: wyczyść wykresy, skoro nie ma sygnału
                    if (histogramView != null) histogramView.updateLumaData(new int[256]);
                });
            }
        });

        mjpegPlayer.start();
    }

    private void processFrameData(Bitmap bitmap) {
        // 1. OBSŁUGA SNAPSHOTU
        if (requestSnapshot) {
            requestSnapshot = false;
            // Robimy kopię, bo oryginał zostanie zrecyklowany przez renderer
            Bitmap snap = bitmap.copy(bitmap.getConfig(), false);
            new Thread(() -> saveBitmapToGallery(snap)).start();
        }

        // 2. OBSŁUGA WYKRESÓW (Histogram / Parada)
        if (currentScopeState != SCOPE_OFF && !isProcessingFrame && processingHandler != null) {
            long now = System.currentTimeMillis();
            if (now - lastHistogramTime >= scopeDelayMs) {
                isProcessingFrame = true;
                lastHistogramTime = now;

                // Pobieramy piksele do tablicy
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                int[] allPixels = new int[w * h];
                bitmap.getPixels(allPixels, 0, w, 0, 0, w, h);

                processingHandler.post(() -> {
                    try {
                        if (currentScopeState == SCOPE_HISTOGRAM) {
                            calculateLumaHistogramFromPixels(allPixels);
                        } else if (currentScopeState == SCOPE_PARADE) {
                            if (paradeView != null) {
                                paradeView.updateData(bitmap);
                            }
                        }
                    } finally {
                        isProcessingFrame = false;
                    }
                });
            }
        }
    }

    // Interfejs pomocniczy, aby nie implementować pustych metod SeekBar co chwilę
    private abstract static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}