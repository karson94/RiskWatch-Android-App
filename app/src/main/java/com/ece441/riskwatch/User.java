package com.ece441.riskwatch;

import java.io.Serializable;
import com.google.firebase.database.*;

public class User implements Serializable {

    public String userName;
    private String amazonUserId;
    private String amazonAccessToken;
    private boolean isAmazonLinked;

    public User(String userName){
        this.userName = userName;
        this.isAmazonLinked = false;
    }
    
    public User(String userName, String amazonUserId, String amazonAccessToken) {
        this.userName = userName;
        this.amazonUserId = amazonUserId;
        this.amazonAccessToken = amazonAccessToken;
        this.isAmazonLinked = true;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public String getAmazonUserId() {
        return amazonUserId;
    }
    
    public void setAmazonUserId(String amazonUserId) {
        this.amazonUserId = amazonUserId;
    }
    
    public String getAmazonAccessToken() {
        return amazonAccessToken;
    }
    
    public void setAmazonAccessToken(String amazonAccessToken) {
        this.amazonAccessToken = amazonAccessToken;
    }
    
    public boolean isAmazonLinked() {
        return isAmazonLinked;
    }
    
    public void setAmazonLinked(boolean amazonLinked) {
        isAmazonLinked = amazonLinked;
    }
}
