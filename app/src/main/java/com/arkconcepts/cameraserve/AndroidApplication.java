package com.arkconcepts.cameraserve;

import android.app.Application;

public class AndroidApplication extends Application {
    private static AndroidApplication instance;
    public String IP = "192.168.0.14";
    public int commandPort = 8080;
    public int audioPort = 4010;

    public static AndroidApplication getInstance(){
        return instance;
    }

    @Override
    public void onCreate(){
        super.onCreate();
        instance = this;

    }

}
