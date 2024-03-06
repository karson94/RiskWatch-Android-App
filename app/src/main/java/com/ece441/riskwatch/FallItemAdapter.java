package com.ece441.riskwatch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FallItemAdapter extends RecyclerView.Adapter<MyViewHolder> {

    private final List<Fall> fallList;
    private final HomeActivity homeActivity;


    public FallItemAdapter(List<Fall> fall, HomeActivity ha){
        fallList = fall;
        homeActivity = ha;

    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, int viewType) {

        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.fall_items, parent, false);

//        itemView.setOnLongClickListener(homeActivity);
//        itemView.setOnClickListener(homeActivity);

        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {

        Fall fall = fallList.get(position);

        holder.fallEventDate.setText(fall.getDate());
        holder.fallEventTime.setText(fall.getTime());
        holder.fallEventImpSev.setText(fall.getImpactSeverity() + "g");
        holder.fallEventHR.setText(fall.getHeartRate() + "bpm");
        holder.fallEventFallDir.setText(fall.getFallDirection());

        String severity;
        String deltaHR;

        if(fall.getImpactSeverity() < 0.5){
            severity = "Soft";
        } else if (fall.getImpactSeverity() < 1.5 && fall.getImpactSeverity() > 0.5) {
            severity = "Medium";
        }else{
            severity = "Hard";
        }

        if (fall.getHeartRate() < 70) {
            deltaHR = "Low";
        }else{
            deltaHR = "High";
        }


        holder.fallEventDesc.setText(severity + " Fall & " + deltaHR + " Heart Rate");

    }

    @Override
    public int getItemCount() {
        return fallList.size();
    }
}

