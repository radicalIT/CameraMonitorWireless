package dev.yaky.usbcamviewer; // Upewnij się, że pakiet jest zgodny z Twoim projektem

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private long startTime = System.currentTimeMillis();
    private final Context context;
    private int programId;
    private int textureId;
    private SurfaceTexture surfaceTexture;
    // private int mFilterMode = 0;
    // Dodaj flagi:
    public boolean showLut = false;
    public boolean showZebra = false;
    public float zebraThreshold = 0.95f;
    public boolean showPeaking = false;
    public float peakingThreshold = 0.05f;
    public float[] peakingColorRGB = {0.0f, 1.0f, 0.0f};
    public boolean showFalseColor = false;

    // Rozmiar obrazu (potrzebne do Peakingu)
    private float imageWidth = 1920f;
    private float imageHeight = 1080f;
    private int lutTextureId = -1; // ID naszej tekstury LUT

    public boolean isMirrored = false;
    private final float[] vPMatrix = new float[16]; // Macierz przekształceń

    // Bufor wierzchołków (kwadrat na cały ekran)
    private final FloatBuffer vertexBuffer;
    private final FloatBuffer texCoordBuffer;

    private static final float[] VERTEX_COORDS = {
            -1.0f, -1.0f,  // Lewy dół
            1.0f, -1.0f,  // Prawy dół
            -1.0f,  1.0f,  // Lewa góra
            1.0f,  1.0f   // Prawa góra
    };

    private static final float[] TEX_COORDS = {
            0.0f, 1.0f,  // Lewy dół (obrazu: lewa góra)
            1.0f, 1.0f,  // Prawy dół (obrazu: prawa góra)
            0.0f, 0.0f,  // Lewa góra (obrazu: lewy dół)
            1.0f, 0.0f   // Prawa góra (obrazu: prawy dół)
    };

    // Flaga informująca, że przyszła nowa klatka z kamery
    private boolean updateSurface = false;
    private GLSurfaceView glSurfaceView;

    public CameraRenderer(Context context, GLSurfaceView view) {
        this.context = context;
        this.glSurfaceView = view;

        vertexBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().put(VERTEX_COORDS);
        vertexBuffer.position(0);

        texCoordBuffer = ByteBuffer.allocateDirect(TEX_COORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().put(TEX_COORDS);
        texCoordBuffer.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 1. Wczytaj i skompiluj shadery
        String vertexCode = readShader(R.raw.vertex_shader);
        String fragmentCode = readShader(R.raw.fragment_shader);
        programId = createProgram(vertexCode, fragmentCode);

        // 2. Utwórz teksturę zewnętrzną (dla kamery)
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId); // ZMIANA Z OES NA 2D
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // 3. Utwórz SurfaceTexture, który przekażemy do kamery
        surfaceTexture = new SurfaceTexture(textureId);
        surfaceTexture.setOnFrameAvailableListener(this);

        lutTextureId = loadTexture(context, R.drawable.lut_square_64_512x512);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        imageWidth = width;
        imageHeight = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        synchronized (this) {
            if (updateSurface) {
                surfaceTexture.updateTexImage();
                updateSurface = false;
            }
        }

        // TŁO
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // ===============================================================
        // 1. RYSOWANIE KAMERY
        // ===============================================================
        GLES20.glUseProgram(programId);

        // --- FIX NA CZARNY EKRAN & MIRROR ---

        // 1. Pobierz bazową macierz z kamery (musi być!)
//        surfaceTexture.getTransformMatrix(vPMatrix);
        android.opengl.Matrix.setIdentityM(vPMatrix, 0);

        // 2. Jeśli Mirror jest włączony, modyfikujemy tę macierz
        if (isMirrored) {
            android.opengl.Matrix.translateM(vPMatrix, 0, 1.0f, 0.0f, 0.0f);
            android.opengl.Matrix.scaleM(vPMatrix, 0, -1.0f, 1.0f, 1.0f);
        }

        // 3. Wyślij do shadera
        int uMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, vPMatrix, 0);

        // ------------------------------------

        // Wierzchołki (Pozycja)
        int vPositionHandle = GLES20.glGetAttribLocation(programId, "vPosition");
        GLES20.glEnableVertexAttribArray(vPositionHandle);
        GLES20.glVertexAttribPointer(vPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        // Współrzędne tekstury
        int vTexCoordHandle = GLES20.glGetAttribLocation(programId, "vTexCoord");
        GLES20.glEnableVertexAttribArray(vTexCoordHandle);
        GLES20.glVertexAttribPointer(vTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        // Tekstura i Uniformy
        // --- RYSOWANIE STRUMIENIA (Jednostka 0) ---
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId); // To jest ID Twojego strumienia MJPEG
        int sTextureHandle = GLES20.glGetUniformLocation(programId, "sTexture");
        GLES20.glUniform1i(sTextureHandle, 0); // Informujemy shader: sTexture jest w jednostce 0

        // Flagi
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uEnableLUT"), showLut ? 1 : 0);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uEnableZebra"), showZebra ? 1 : 0);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uEnablePeaking"), showPeaking ? 1 : 0);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uEnableFalseColor"), showFalseColor ? 1 : 0);

        // Parametry
        GLES20.glUniform2f(GLES20.glGetUniformLocation(programId, "uTexelSize"), 1.0f / imageWidth, 1.0f / imageHeight);
        float timeSeconds = (System.currentTimeMillis() - startTime) / 1000.0f;
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uTime"), timeSeconds);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uZebraThreshold"), zebraThreshold);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uPeakingThreshold"), peakingThreshold);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(programId, "uPeakingColor"), peakingColorRGB[0], peakingColorRGB[1], peakingColorRGB[2]);

        // LUT
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId);
        int sLutTextureHandle = GLES20.glGetUniformLocation(programId, "sLutTexture");
        GLES20.glUniform1i(sLutTextureHandle, 1); // Informujemy shader: sLutTexture jest w jednostce 1

        // RYSUJEMY KAMERĘ (4 punkty)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // ===============================================================
        // WAŻNE: SPRZĄTANIE (FIX CRASHA)
        // ===============================================================
        // Musimy wyłączyć te atrybuty, bo Scope ich nie używa, a ma dużo więcej punktów!
        GLES20.glDisableVertexAttribArray(vPositionHandle);
        GLES20.glDisableVertexAttribArray(vTexCoordHandle);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (this) {
            updateSurface = true;
        }
        // Wymuś odświeżenie widoku
        glSurfaceView.requestRender();
    }

    // Metoda, którą pobierzemy SurfaceTexture w MainActivity, żeby dać go kamerze
    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

//    public void setFilterMode(int mode) {
//        this.mFilterMode = mode;
//    }

    // --- Pomocnicze funkcje (ładowanie shaderów) ---
    private String readShader(int resId) {
        StringBuilder str = new StringBuilder();
        try (InputStream is = context.getResources().openRawResource(resId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                str.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return str.toString();
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        return program;
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        // --- DIAGNOSTYKA BŁĘDÓW ---
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader);
            // TO JEST KLUCZOWE - ZOBACZYSZ TO W LOGACH NA CZERWONO:
            Log.e("OpenGL", "BŁĄD KOMPILACJI SHADERA (" + (type == GLES20.GL_VERTEX_SHADER ? "VERTEX" : "FRAGMENT") + "): " + error);
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private int loadTexture(Context context, int resourceId) {
        final int[] textureHandle = new int[1];
        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0) {
            final android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inScaled = false; // Nie skaluj obrazka! Musi być 512x512
            final android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeResource(context.getResources(), resourceId, options);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            // Ważne dla LUT-ów (Clamp to Edge zapobiega dziwnym kolorom na krawędziach)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
        }
        return textureHandle[0];
    }

    public void clearFrame() {
        glSurfaceView.queueEvent(() -> {
            // Tworzymy małą czarną bitmapę 1x1
            Bitmap black = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            black.eraseColor(android.graphics.Color.BLACK);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, black, 0);

            black.recycle();
            glSurfaceView.requestRender();
        });
    }
    public void updateBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;

        glSurfaceView.queueEvent(() -> {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

            // Używamy texSubImage2D jeśli tekstura już istnieje - jest szybsze
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

            glSurfaceView.requestRender();
        });
    }
}