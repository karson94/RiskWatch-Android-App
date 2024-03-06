package com.ece441.riskwatch;

import java.io.Serializable;

public class User implements Serializable {

    public String userName;

    public User(String userName){
        this.userName = userName;

    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
