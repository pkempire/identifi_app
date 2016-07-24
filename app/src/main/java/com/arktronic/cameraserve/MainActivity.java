package com.arktronic.cameraserve;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceHolder holder;
    private Camera camera;
    private boolean previewRunning = false;
    private int camId = 0;
    private ByteArrayOutputStream previewStream = new ByteArrayOutputStream();
    private MjpegServer mjpegServer = new MjpegServer();
    private Thread serverThread = new Thread(mjpegServer);
    private int rotationSteps = 0;

    private static HashMap<Integer, List<Camera.Size>> cameraSizes = new HashMap<>();
    private static ReentrantReadWriteLock frameLock = new ReentrantReadWriteLock();
    private static byte[] jpegFrame;

    public static byte[] getJpegFrame() {
        try {
            frameLock.readLock().lock();
            return jpegFrame;
        } finally {
            frameLock.readLock().unlock();
        }
    }

    public static HashMap<Integer, List<Camera.Size>> getCameraSizes() {
        return cameraSizes;
    }

    private static void setJpegFrame(ByteArrayOutputStream stream) {
        try {
            frameLock.writeLock().lock();
            jpegFrame = stream.toByteArray();
        } finally {
            frameLock.writeLock().unlock();
        }
    }

    private void cacheResolutions() {
        int cams = Camera.getNumberOfCameras();
        for(int i = 0; i < cams; i++) {
            Camera cam = Camera.open(i);
            Camera.Parameters params = cam.getParameters();
            cameraSizes.put(i, params.getSupportedPreviewSizes());
            cam.release();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cacheResolutions();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int cams = Camera.getNumberOfCameras();
                camId++;
                if (camId > cams - 1) camId = 0;
                if (previewRunning) stopPreview();
                if (camera != null) camera.release();
                camera = null;

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                preferences.edit().putString("cam", String.valueOf(camId)).apply();

                openCamAndPreview();

                Toast.makeText(MainActivity.this, "Cam " + (camId + 1),
                        Toast.LENGTH_SHORT).show();
            }
        });

        SurfaceView cameraView = (SurfaceView) findViewById(R.id.surfaceView);
        holder = cameraView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        loadPreferences();

        serverThread.start();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadPreferences();

        openCamAndPreview();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        this.finish();
        System.exit(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        startPreview();
    }

    private void openCamAndPreview() {
        try {
            if (camera == null) camera = Camera.open(camId);
            startPreview();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        camId = Integer.parseInt(preferences.getString("cam", "0"));
        Integer rotDegrees = Integer.parseInt(preferences.getString("rotation", "0"));
        rotationSteps = rotDegrees / 90;
        Integer port = Integer.parseInt(preferences.getString("port", "8080"));
        MjpegServer.setPort(port);
    }

    private void startPreview() {
        if (previewRunning) stopPreview();

        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

        if(display.getRotation() == Surface.ROTATION_0)
        {
            camera.setDisplayOrientation(90);
        }
        else if(display.getRotation() == Surface.ROTATION_270)
        {
            camera.setDisplayOrientation(180);
        }
        else {
            camera.setDisplayOrientation(0);
        }

        Camera.Parameters p = camera.getParameters();
//        Camera.Size size = p.getSupportedPreviewSizes().get(0);
//        //p.setPreviewSize(size.width, size.height);
//        p.setPreviewSize(640, 480);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String res = preferences.getString("resolution", "640x480");
        String[] resParts = res.split("x");
        if (resParts.length != 2) resParts = "640x480".split("x");
        p.setPreviewSize(Integer.parseInt(resParts[0]), Integer.parseInt(resParts[1]));

        camera.setParameters(p);

        try {
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        camera.startPreview();

        holder.addCallback(this);

        previewRunning = true;
    }

    private void stopPreview() {
        if (!previewRunning) return;

        holder.removeCallback(this);
        camera.stopPreview();
        camera.setPreviewCallback(null);

        previewRunning = false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        stopPreview();
        if (camera != null) camera.release();
        camera = null;

        openCamAndPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        openCamAndPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        stopPreview();
        if (camera != null) camera.release();
        camera = null;
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        previewStream.reset();
        Camera.Parameters p = camera.getParameters();

        int previewHeight = p.getPreviewSize().height,
            previewWidth = p.getPreviewSize().width;

//        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
//        Bitmap scaled = Bitmap.createScaledBitmap(bmp, previewHeight, previewWidth, true);
//        int w = scaled.getWidth();
//        int h = scaled.getHeight();
//        Matrix mtx = new Matrix();
//        mtx.postRotate(90);
//        // Rotating Bitmap
//        bmp = Bitmap.createBitmap(scaled, 0, 0, w, h, mtx, true);
//        bmp.compress(Bitmap.CompressFormat.JPEG, 100, previewStream);

        for (int i = 0; i < rotationSteps; i++) {
            bytes = rotateYUV420Degree90(bytes, previewWidth, previewHeight);

            int tmp = previewHeight;
            previewHeight = previewWidth;
            previewWidth = tmp;
        }

//        int format = p.getPreviewFormat();
//        new YuvImage(bytes, format, p.getPreviewSize().width, p.getPreviewSize().height, null)
//                .compressToJpeg(new Rect(0, 0, p.getPreviewSize().width, p.getPreviewSize().height),
//                        100, previewStream);

        int format = p.getPreviewFormat();
        new YuvImage(bytes, format, previewWidth, previewHeight, null)
                .compressToJpeg(new Rect(0, 0, previewWidth, previewHeight),
                        100, previewStream);


//        int format = p.getPreviewFormat();
//        new YuvImage(bytes, format, previewWidth, previewHeight, null)
//                .compressToJpeg(new Rect(0, 0, previewWidth, previewHeight),
//                        100, previewStream);
//
//        Bitmap bmp = BitmapFactory.decodeByteArray(previewStream.toByteArray(), 0, previewStream.size());
//        Bitmap scaled = Bitmap.createScaledBitmap(bmp, previewHeight, previewWidth, true);
//        int w = scaled.getWidth();
//        int h = scaled.getHeight();
//        Matrix mtx = new Matrix();
//        mtx.postRotate(90);
//        // Rotating Bitmap
//        bmp = Bitmap.createBitmap(scaled, 0, 0, w, h, mtx, true);
//        previewStream.reset();
//        bmp.compress(Bitmap.CompressFormat.JPEG, 100, previewStream);

        setJpegFrame(previewStream);
    }

    private byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight)
    {
        byte [] yuv = new byte[imageWidth*imageHeight*3/2];
        // Rotate the Y luma
        int i = 0;
        for(int x = 0;x < imageWidth;x++)
        {
            for(int y = imageHeight-1;y >= 0;y--)
            {
                yuv[i] = data[y*imageWidth+x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth*imageHeight*3/2-1;
        for(int x = imageWidth-1;x > 0;x=x-2)
        {
            for(int y = 0;y < imageHeight/2;y++)
            {
                yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+x];
                i--;
                yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+(x-1)];
                i--;
            }
        }
        return yuv;
    }
}