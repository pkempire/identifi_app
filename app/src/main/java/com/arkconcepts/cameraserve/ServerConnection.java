package com.arkconcepts.cameraserve;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;

public class ServerConnection {

    String IP;
    int Port;
    Socket clientSocket;
    DataOutputStream outToServer;
    BufferedReader inFromServer;

    boolean isConnected(){
        return clientSocket.isConnected();
    }

    void close(){
        if (!(null == clientSocket) && clientSocket.isConnected()) {
            try{
                clientSocket.close();
            }
            catch(Exception e){

            }
        }
    }

    int Open(String ServerIPAddress, int ServerPort)
    {
        IP = ServerIPAddress;
        Port = ServerPort;

        Thread threadCommandConn = new Thread("LVB-socket-connector"){
            @Override
            public void run() {
                try {
                    if (!(null == clientSocket))
                        clientSocket.close();

                    clientSocket = new Socket(IP, Port);
                    if (clientSocket.isConnected()) {
                        Log.i("LVB", "connected to server");
                        outToServer = new DataOutputStream(clientSocket.getOutputStream());
                        inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    }
                    else
                        Log.e("LVB", "failed to connect to server");

                } catch (Exception e) {
                    Log.e("LVB", e.toString());
                }
            }
        };

        threadCommandConn.start();
        //wait for the socket to be opened before returning - if we return too quickly,
        // the caller might try to write to the socket before it's open

        try {
            Thread.sleep(200);
            if (clientSocket.isConnected()) {
                Log.i("LVB", "connected to server");
                return 0;
            }
            else{
                Log.i("LVB", "NOT connected to server");
                return -1;
            }

        }
        catch(Exception e){
            Log.e("LVB",e.toString());
        }
        return -1;
    };

    int Write(final String TextToSendToServer){
        try{

            Thread threadCommandConn = new Thread("LVB-socket-connector"){
                @Override
                public void run() {
                    try {
                        if (clientSocket.isConnected()) {
                            outToServer.writeBytes(TextToSendToServer+'\n');
                            Log.i("LVB", "SENT TO SERVER: " + TextToSendToServer);
                        }
                    } catch (Exception e) {
                        Log.e("LVB", e.toString());
                        try{
                            clientSocket.close();
                        }
                        catch(Exception e2){

                        }
                    }
                }
            };

            threadCommandConn.start();
            Thread.sleep(100);

            return 0;
        }
        catch(Exception e){
            System.out.println(e);
            return -1;
        }

    };

/*    need to figure out how to pass the results of the read across threads

    String Read(){

        try {
            Thread threadCommandConn = new Thread("LVB-socket-connector"){
                @Override
                public void run() {
                    try {
                        if (clientSocket.isConnected()) {
                            outToServer.writeBytes(TextToSendToServer+'\n');
                            Log.i("LVB", "SENT TO SERVER: " + TextToSendToServer);
                        }
                    } catch (Exception e) {
                        Log.e("LVB", e.toString());
                    }
                }
            };

            threadCommandConn.start();

            String TextReceivedFromServer = inFromServer.readLine();
            System.out.println("RECEIVED FROM SERVER: " + TextReceivedFromServer);
            return TextReceivedFromServer;
        }
        catch(Exception e){
            System.out.println(e);
            return "";
        }
    }

*/

    int Close()
    {
        try {
            clientSocket.close();
            return 0;
        }
        catch (Exception e) {
            System.out.println(e);
            return -1;
        }


    }

}
