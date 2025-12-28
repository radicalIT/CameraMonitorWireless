package dev.yaky.usbcamviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ParadeView extends View {
    private Bitmap scopeBitmap;
    private int[] pixels;
    private final int SCOPE_W = 360;
    private final int SCOPE_H = 128;
    private final Rect srcRect = new Rect(0, 0, SCOPE_W, SCOPE_H);
    private final Rect dstRect = new Rect();
    private final Paint paint = new Paint();

    public ParadeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scopeBitmap = Bitmap.createBitmap(SCOPE_W, SCOPE_H, Bitmap.Config.ARGB_8888);
        pixels = new int[SCOPE_W * SCOPE_H];
    }

    public void updateData(Bitmap bitmap) {
        if (getVisibility() != View.VISIBLE || bitmap == null) return;

        Arrays.fill(pixels, 0x00000000);

        int imgW = bitmap.getWidth();
        int imgH = bitmap.getHeight();

        int stepX = 16; // Próbkowanie
        int stepY = 16;
        int colWidth = SCOPE_W / 3;
        int offsetG = SCOPE_W / 3;
        int offsetB = (SCOPE_W / 3) * 2;

        for (int y = 0; y < imgH; y += stepY) {
            for (int x = 0; x < imgW; x += stepX) {
                int pix = bitmap.getPixel(x, y);
                int r = (pix >> 16) & 0xFF;
                int g = (pix >> 8) & 0xFF;
                int b = pix & 0xFF;

                int plotX_local = (x * colWidth) / imgW;

                // Obliczanie pozycji Y (odwrócone)
                int pyR = SCOPE_H - 1 - (r * (SCOPE_H - 1) / 255);
                int pyG = SCOPE_H - 1 - (g * (SCOPE_H - 1) / 255);
                int pyB = SCOPE_H - 1 - (b * (SCOPE_H - 1) / 255);

                // Rysowanie punktów
                pixels[pyR * SCOPE_W + (plotX_local)] = 0xFFFF0000;
                pixels[pyG * SCOPE_W + (offsetG + plotX_local)] = 0xFF00FF00;
                pixels[pyB * SCOPE_W + (offsetB + plotX_local)] = 0xFF0000FF;
            }
        }

        scopeBitmap.setPixels(pixels, 0, SCOPE_W, 0, 0, SCOPE_W, SCOPE_H);
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getVisibility() != View.VISIBLE) return;

        // 2. Skalowanie z uwzględnieniem Paddingu!
        // Dzięki temu bitmapa nie wyjdzie poza obszar ramki i nie zasłoni rogów.
        dstRect.set(
                getPaddingLeft(),
                getPaddingTop(),
                getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom()
        );

        canvas.drawBitmap(scopeBitmap, srcRect, dstRect, paint);

        // Linie pomocnicze (trójpodział)
        paint.setColor(Color.LTGRAY); // Jasnoszary zamiast białego, mniej bije po oczach
        paint.setStrokeWidth(2);
        paint.setAlpha(100); // Lekko przezroczyste

        // Obliczamy szerokość roboczą
        float contentW = getWidth() - getPaddingLeft() - getPaddingRight();
        float startX = getPaddingLeft();
        float third = contentW / 3.0f;
        float h = getHeight() - getPaddingBottom();

        // Rysujemy linie wewnątrz paddingu
        canvas.drawLine(startX + third, getPaddingTop(), startX + third, h, paint);
        canvas.drawLine(startX + third * 2, getPaddingTop(), startX + third * 2, h, paint);

        // Reset pędzla
        paint.setAlpha(255);
    }
}