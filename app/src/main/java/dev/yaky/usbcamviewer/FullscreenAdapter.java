package dev.yaky.usbcamviewer;

import android.net.Uri;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import java.util.List;

public class FullscreenAdapter extends RecyclerView.Adapter<FullscreenAdapter.ImageViewHolder> {

    private List<Uri> uris;
    private OnTapListener tapListener;

    public interface OnTapListener {
        void onLeftTap();
        void onCenterTap();
        void onRightTap();
    }

    public void setOnTapListener(OnTapListener listener) {
        this.tapListener = listener;
    }

    public FullscreenAdapter(List<Uri> uris) {
        this.uris = uris;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PhotoView photoView = new PhotoView(parent.getContext());
        photoView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // Pozwól PhotoView na pełną swobodę zoomu
        return new ImageViewHolder(photoView);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        Glide.with(holder.itemView.getContext())
                .load(uris.get(position))
                .into(holder.photoView);

        // Wykrywamy tylko tapnięcia. PhotoView ignoruje gesty zoomowania przy tym listenerze.
        holder.photoView.setOnPhotoTapListener((view, x, y) -> {
            if (tapListener != null) {
                if (x < 0.30f) tapListener.onLeftTap();
                else if (x > 0.70f) tapListener.onRightTap();
                else tapListener.onCenterTap();
            }
        });
    }

    @Override
    public int getItemCount() {
        return uris != null ? uris.size() : 0;
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        PhotoView photoView;
        ImageViewHolder(PhotoView itemView) {
            super(itemView);
            this.photoView = itemView;
        }
    }
}