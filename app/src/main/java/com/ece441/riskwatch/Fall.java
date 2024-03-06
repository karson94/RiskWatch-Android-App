package com.ece441.riskwatch;

import java.io.Serializable;

public class Fall implements Serializable {

    public String fallID;
    public String time;
    public String date;
    public int heartRate;
    public int deltaHeartRate;
    public double impactSeverity;
    public String fallDirection;

    public Fall(String fallID, String time, String date, int heartRate, int deltaHeartRate, double impactSeverity, String fallDirection){
        this.time = time;
        this.date = date;
        this.heartRate = heartRate;
        this.deltaHeartRate = deltaHeartRate;
        this.impactSeverity = impactSeverity;
        this.fallDirection = fallDirection;
        this.fallID = fallID;
    }

    public String getfallID() {
        return fallID;
    }
    public String getTime() {
        return time;
    }

    public String getDate() {
        return date;
    }

    public String getFallDirection() {
        return fallDirection;
    }

    public int getHeartRate() {
        return heartRate;
    }

    public int getDeltaHeartRate() {
        return deltaHeartRate;
    }
    public double getImpactSeverity() {
        return impactSeverity;
    }
}
