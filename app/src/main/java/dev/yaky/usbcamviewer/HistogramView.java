package dev.yaky.usbcamviewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class HistogramView extends View {
    private int[] binsLuma = new int[256];
    private final Paint paintLuma = new Paint();
    private final Paint paintZone = new Paint();

    public HistogramView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Jeden pędzel dla jasności
        paintLuma.setColor(0xDDFFFFFF);
        paintLuma.setStrokeWidth(3f);
        paintLuma.setStyle(Paint.Style.STROKE);
        paintLuma.setAntiAlias(true);

        paintZone.setStyle(Paint.Style.FILL);
    }

    public void updateLumaData(int[] luma) {
        if (getVisibility() != View.VISIBLE) return;
        this.binsLuma = luma;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getVisibility() != View.VISIBLE) return;

        float contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float contentHeight = getHeight() - getPaddingTop() - getPaddingBottom();

        if (contentWidth <= 0 || contentHeight <= 0) return;

        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());

        // Strefy ostrzegawcze
        float stepX = contentWidth / 256.0f;
        paintZone.setColor(0x30CCCCCC);
        canvas.drawRect(0, 0, stepX * 12, contentHeight, paintZone);
        canvas.drawRect(stepX * (256 - 12), 0, contentWidth, contentHeight, paintZone);

        // Normalizacja (szukanie najwyższego słupka)
        int max = 1;
        for (int i = 0; i < 256; i++) {
            if (binsLuma[i] > max) max = binsLuma[i];
        }

        // Rysowanie jednej, ciągłej linii jasności
        for (int i = 0; i < 255; i++) {
            float x1 = i * stepX;
            float x2 = (i + 1) * stepX;

            float y1 = contentHeight - ((float)binsLuma[i] / max * contentHeight);
            float y2 = contentHeight - ((float)binsLuma[i+1] / max * contentHeight);

            canvas.drawLine(x1, y1, x2, y2, paintLuma);
        }

        canvas.restore();
    }
}