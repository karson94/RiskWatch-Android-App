package com.ece441.riskwatch;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import android.content.ActivityNotFoundException;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;
import java.util.Locale;

public class FallItemAdapter extends RecyclerView.Adapter<MyViewHolder> {
    private final List<Fall> fallList;
    private final HomeActivity homeActivity;
    private final String googleMapsApiKey;

    public FallItemAdapter(List<Fall> fall, HomeActivity ha, String apiKey) {
        fallList = fall;
        homeActivity = ha;
        this.googleMapsApiKey = apiKey;
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

        // Load static map preview if coordinates and API key are valid
        if (googleMapsApiKey != null && !googleMapsApiKey.isEmpty() && 
            fall.getLatitude() != 0.0 && fall.getLongitude() != 0.0) { 
            
            String staticMapUrl = String.format(
                Locale.US,
                "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=240x240&scale=2&markers=color:red%%7C%f,%f&key=%s",
                fall.getLatitude(), fall.getLongitude(),
                fall.getLatitude(), fall.getLongitude(),
                googleMapsApiKey
            );

            holder.mapPreview.setVisibility(View.VISIBLE);
            Glide.with(homeActivity)
                .load(staticMapUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_map_placeholder)
                .error(R.drawable.ic_map_error)
                .into(holder.mapPreview);

            // Simple map click implementation
            holder.mapPreview.setOnClickListener(view -> {
                String uri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)", 
                                          fall.getLatitude(), fall.getLongitude(), 
                                          fall.getLatitude(), fall.getLongitude(), "Fall Location");
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                try {
                    homeActivity.startActivity(mapIntent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(homeActivity, "No map app found to handle location.", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            holder.mapPreview.setVisibility(View.GONE);
            holder.mapPreview.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return fallList.size();
    }
}

