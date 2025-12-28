package dev.yaky.usbcamviewer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

// Jeśli Glide dalej świeci na czerwono, sprawdź Krok 1 poniżej!
import com.bumptech.glide.Glide;

import java.util.List;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {

    private List<Uri> uris; // Zmienione z files na uris
    private Context context;
    private OnUriDeleteListener listener;

    public interface OnUriDeleteListener {
        void onDelete(Uri uri, int position);
    }

    public GalleryAdapter(Context context, List<Uri> uris, OnUriDeleteListener listener) {
        this.context = context;
        this.uris = uris;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // To jest ta brakująca metoda, której żądał błąd!
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Uri uri = uris.get(position);

        // Ładowanie obrazu przez Glide
        Glide.with(context)
                .load(uri)
                .centerCrop()
                .into(holder.imageView);

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(uri, position);
            }
        });

        holder.imageView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(context, FullscreenActivity.class);
            // ZMIANA: Wysyłamy całą listę oraz pozycję klikniętego elementu
            intent.putParcelableArrayListExtra("uriList", new java.util.ArrayList<>(this.uris));
            intent.putExtra("position", position);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        // Naprawione: zmienione z files.size() na uris.size()
        return uris != null ? uris.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        View btnDelete;

        public ViewHolder(View v) {
            super(v);
            imageView = v.findViewById(R.id.iv_thumbnail);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }
}