package com.ece441.riskwatch;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class FallItemAdapter extends RecyclerView.Adapter<MyViewHolder> {
    private final List<Fall> fallList;
    private final HomeActivity homeActivity;

    public FallItemAdapter(List<Fall> fall, HomeActivity ha) {
        fallList = fall;
        homeActivity = ha;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.fall_items, parent, false);
        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        Fall fall = fallList.get(position);
        
        // Set basic fall information
        holder.fallEventDate.setText(fall.getDate());
        holder.fallEventTime.setText(fall.getTime());
        holder.fallEventImpSev.setText(String.format("%.1fg", fall.getImpactSeverity()));
        holder.fallEventHR.setText(String.format("%dbpm", fall.getHeartRate()));
        holder.fallEventFallDir.setText(fall.getFallDirection());
        holder.fallEventLocation.setText(fall.getAddress());

        // Determine fall severity and heart rate status
        String severity = fall.getImpactSeverity() <= 0.5 ? "Soft" :
                         fall.getImpactSeverity() <= 2 ? "Medium" : "Hard";
        String deltaHR = fall.getDeltaHeartRate() < 0 ? "Low" : "High";
        holder.fallEventDesc.setText(String.format("%s Fall & %s HR", severity, deltaHR));

        // Load static map preview
        String staticMapUrl = String.format(
            "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=240x240&scale=2&markers=color:red%%7C%f,%f&key=%s",
            fall.getLatitude(), fall.getLongitude(),
            fall.getLatitude(), fall.getLongitude(),
            BuildConfig.MAPS_API_KEY
        );

        Glide.with(homeActivity)
            .load(staticMapUrl)
            .centerCrop()
            .into(holder.mapPreview);

        // Simple map click implementation
        holder.mapPreview.setOnClickListener(view -> {
            String uri = "google.navigation:q=" + fall.getLatitude() + "," + fall.getLongitude();
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            mapIntent.setPackage("com.google.android.apps.maps");
            homeActivity.startActivity(mapIntent);
        });
    }

    @Override
    public int getItemCount() {
        return fallList.size();
    }
}

