package dev.yaky.usbcamviewer;

import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.util.List;

public class FullscreenActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private List<Uri> uriList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);

        // --- 1. CAŁKOWITE UKRYCIE PASKA (STATUS BAR) ---
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.statusBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        // --- 2. INICJALIZACJA I BLOKADA SWIPE ---
        viewPager = findViewById(R.id.viewPager);
        // TO JEST NAJWAŻNIEJSZA LINIA - WYŁĄCZA PRZESUWANIE NA SZTYWNO
        viewPager.setUserInputEnabled(false);

        uriList = getIntent().getParcelableArrayListExtra("uriList");
        int position = getIntent().getIntExtra("position", 0);

        if (uriList != null) {
            FullscreenAdapter adapter = new FullscreenAdapter(uriList);

            // Obsługa stref kliknięć (L/Ś/P)
            adapter.setOnTapListener(new FullscreenAdapter.OnTapListener() {
                @Override
                public void onLeftTap() {
                    int current = viewPager.getCurrentItem();
                    if (current > 0) viewPager.setCurrentItem(current - 1, true);
                }

                @Override
                public void onRightTap() {
                    int current = viewPager.getCurrentItem();
                    if (current < uriList.size() - 1) viewPager.setCurrentItem(current + 1, true);
                }

                @Override
                public void onCenterTap() {
                    finish(); // Środek = wyjście
                }
            });

            viewPager.setAdapter(adapter);
            viewPager.setCurrentItem(position, false);
        }

        findViewById(R.id.btn_delete_fullscreen).setOnClickListener(v -> deleteCurrentPhoto());
    }

    private void deleteCurrentPhoto() {
        int currentPos = viewPager.getCurrentItem();
        if (uriList != null && currentPos < uriList.size()) {
            Uri currentUri = uriList.get(currentPos);
            getContentResolver().delete(currentUri, null, null);
            uriList.remove(currentPos);
            if (uriList.isEmpty()) {
                finish();
            } else {
                viewPager.getAdapter().notifyItemRemoved(currentPos);
            }
        }
    }
}