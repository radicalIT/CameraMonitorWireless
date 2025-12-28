package dev.yaky.usbcamviewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class FalseColorLegendView extends View {

    private Paint paintSwatch;
    private Paint paintText;
    private RectF rectF = new RectF();

    private final float swatchWidth = 10f; // Stała szerokość paska koloru
    private final float spacing = 15f;    // Odstęp między paskiem a tekstem

    private final LegendItem[] items = {
            new LegendItem(0xFFFF0000, "100 - CLIP"),
            new LegendItem(0xFFFFFF00, "90 - HIGH"),
            new LegendItem(0xFFFFC0CB, "70 - SKIN"),
            new LegendItem(0xFF00FF00, "45 - MID"),
            new LegendItem(0xFF0000FF, "20 - SHAD"),
            new LegendItem(0xFFFF00FF, "0 - BLACK")
    };

    public FalseColorLegendView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintSwatch = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSwatch.setStyle(Paint.Style.FILL);

        paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(26f);
        paintText.setFakeBoldText(true);
        paintText.setShadowLayer(2f, 1f, 1f, 0xAA000000);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Obliczamy jak szeroki musi być widok
        float maxTextWidth = 0;
        for (LegendItem item : items) {
            float w = paintText.measureText(item.label);
            if (w > maxTextWidth) maxTextWidth = w;
        }

        // Całkowita szerokość = paddingi + pasek + odstęp + najdłuższy tekst
        int desiredWidth = (int) (getPaddingLeft() + swatchWidth + spacing + maxTextWidth + getPaddingRight());
        int desiredHeight = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(desiredWidth, desiredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float height = getHeight();
        float pLeft = getPaddingLeft();
        float pTop = getPaddingTop();
        float pBottom = getPaddingBottom();

        float availableHeight = height - pTop - pBottom;
        float stepHeight = availableHeight / items.length;

        for (int i = 0; i < items.length; i++) {
            // Proporcje pionowe
            float top = pTop + (i * stepHeight) + (stepHeight * 0.15f);
            float bottom = top + (stepHeight * 0.7f);

            // 1. Rysowanie paska koloru
            paintSwatch.setColor(items[i].color);
            rectF.set(pLeft, top, pLeft + swatchWidth, bottom);
            canvas.drawRoundRect(rectF, 4f, 4f, paintSwatch);

            // 2. Rysowanie etykiety
            String label = items[i].label;
            float textY = top + (bottom - top) / 2f - (paintText.descent() + paintText.ascent()) / 2f;
            float textX = pLeft + swatchWidth + spacing;

            canvas.drawText(label, textX, textY, paintText);
        }
    }

    private static class LegendItem {
        int color;
        String label;
        LegendItem(int color, String label) {
            this.color = color;
            this.label = label;
        }
    }
}