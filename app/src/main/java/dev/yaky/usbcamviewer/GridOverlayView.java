package dev.yaky.usbcamviewer; // Upewnij się, że pakiet jest poprawny

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class GridOverlayView extends View {

    private Paint paint;

    public GridOverlayView(Context context) {
        super(context);
        init();
    }

    public GridOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(150); // Lekka przezroczystość
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Rysujemy tylko jeśli widok jest widoczny (logika View.GONE załatwia resztę, ale dla pewności)
        int width = getWidth();
        int height = getHeight();

        // Linie pionowe (1/3 i 2/3)
        canvas.drawLine(width / 3f, 0, width / 3f, height, paint);
        canvas.drawLine(2 * width / 3f, 0, 2 * width / 3f, height, paint);

        // Linie poziome (1/3 i 2/3)
        canvas.drawLine(0, height / 3f, width, height / 3f, paint);
        canvas.drawLine(0, 2 * height / 3f, width, 2 * height / 3f, paint);
    }
}