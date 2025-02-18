package com.ece441.riskwatch;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class MyViewHolder  extends RecyclerView.ViewHolder{

    public TextView fallEventTime;
    public TextView fallEventDate;
    public TextView fallEventDesc;
    public TextView fallEventHR;
    public TextView fallEventImpSev;
    public TextView fallEventFallDir;
    public TextView fallEventLocation;


    public MyViewHolder(@NonNull View itemView) {
        super(itemView);

        fallEventTime = itemView.findViewById(R.id.fallEventTime);
        fallEventDate = itemView.findViewById(R.id.fallEventDate);
        fallEventDesc = itemView.findViewById(R.id.fallEventDesc);
        fallEventImpSev = itemView.findViewById(R.id.fallEventImpSev);
        fallEventHR = itemView.findViewById(R.id.fallEventHR);
        fallEventFallDir = itemView.findViewById(R.id.fallEventFallDir);
        fallEventLocation = itemView.findViewById(R.id.fallEventLocation);

    }
}
