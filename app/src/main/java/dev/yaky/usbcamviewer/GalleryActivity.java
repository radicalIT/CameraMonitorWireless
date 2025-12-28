package dev.yaky.usbcamviewer;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class GalleryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private List<Uri> uriList = new ArrayList<>(); // To jest POLE KLASY
    private GalleryAdapter adapter;
    private List<File> photoFiles;
    private View layoutEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_gallery);

        // BEZPIECZNE UKRYWANIE PASKA (AndroidX)
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.statusBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }

        getWindow().setStatusBarColor(android.graphics.Color.BLACK);

        layoutEmpty = findViewById(R.id.layout_empty);

        recyclerView = findViewById(R.id.rv_gallery);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3)); // 3 kolumny

        findViewById(R.id.btn_back_to_camera).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ładujemy pliki za każdym razem, gdy galeria staje się widoczna
        loadFiles();
    }

    private void checkEmptyState() {
        if (uriList == null || uriList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void loadFiles() {
        uriList.clear();

        ContentResolver resolver = getContentResolver();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        // Definiujemy czego szukamy
        String[] projection = new String[] {
                android.provider.MediaStore.Images.Media._ID,
                android.provider.MediaStore.Images.Media.DISPLAY_NAME
        };

        // Filtrujemy tylko Twój folder "USBCamera"
        String selection = android.provider.MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
        String[] selectionArgs = new String[] {"%Pictures/MJPEGCamera%"};
        String sortOrder = android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                do {
                    long id = cursor.getLong(idColumn);
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);

                    uriList.add(contentUri); // Dodajemy do pola klasy
                } while (cursor.moveToNext());
            }
        }

        checkEmptyState();

        // Aktualizujemy adapter (teraz przyjmuje listę Uri)
        adapter = new GalleryAdapter(this, uriList, (uri, position) -> {
            deleteImage(uri, position);
        });
        recyclerView.setAdapter(adapter);
    }

    private void deleteImage(android.net.Uri uri, int position) {
        try {
            int deletedRows = getContentResolver().delete(uri, null, null);

            // DODAJEMY WARUNEK BEZPIECZEŃSTWA:
            if (deletedRows > 0 && position < uriList.size()) {
                uriList.remove(position);
                adapter.notifyItemRemoved(position);
                adapter.notifyItemRangeChanged(position, uriList.size());

                checkEmptyState();
            }
        } catch (SecurityException securityException) {
            // Obsługa Android 10+ (RecoverableSecurityException)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.app.RecoverableSecurityException recoverableSecurityException;
                if (securityException instanceof android.app.RecoverableSecurityException) {
                    recoverableSecurityException = (android.app.RecoverableSecurityException) securityException;
                } else {
                    throw new RuntimeException(securityException.getMessage(), securityException);
                }

                // To wywoła systemowe okno "Czy pozwolić aplikacji na usunięcie tego zdjęcia?"
                try {
                    startIntentSenderForResult(recoverableSecurityException.getUserAction().getActionIntent().getIntentSender(),
                            101, null, 0, 0, 0);
                } catch (android.content.IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK) {
            // Użytkownik wyraził zgodę - odświeżamy listę
            loadFiles();
        }
    }
}