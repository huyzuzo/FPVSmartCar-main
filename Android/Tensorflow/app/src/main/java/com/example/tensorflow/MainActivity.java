package com.example.tensorflow;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Date;
import com.example.tensorflow.customview.OverlayView;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSTION_STORAGE = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private OverlayView trackingOverlay;
    private Detector detector;

    private ImageButton btnConnect;
    private EditText txtIPAddress;
    private WebView mWebView;
    private JoystickView jscontrolCar;
    private JoystickView jscontrolCamera;
    private LayoutInflater layoutInflater;
    private View viewLayout;
    private TextView txtPercent;
    private SeekBar sbSpeed;
    private AlertDialog.Builder builder;

    private Bitmap bitmap;

    private Client client;
    private Thread thread;
    private static boolean isConnecting = false;
    public static boolean computingDetection = false;

    public void init(){
        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        detector = new Detector(this, trackingOverlay);
        btnConnect = findViewById(R.id.btnConnect);
        txtIPAddress = findViewById(R.id.txtIPAddress);
        mWebView = findViewById(R.id.videoStream);
        mWebView.setBackgroundColor(0x00000000);
        jscontrolCar = findViewById(R.id.jstControlCar);
        jscontrolCamera = findViewById(R.id.jstControlCamera);
        layoutInflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        viewLayout = layoutInflater.inflate(R.layout.activity_dialog, findViewById(R.id.layout_dialog));
        txtPercent = viewLayout.findViewById(R.id.txtPercent);
        sbSpeed = viewLayout.findViewById(R.id.seekBarPercent);
        builder = new AlertDialog.Builder(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        detector.onPreviewSizeChosen();

        jscontrolCar.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                if(isConnecting) {
                    if(power >= 70) {
                        client.sendData1("MOTOR#" + (angle / 10));
                    }else if(angle == 0 && power == 0 && direction == 0){
                        client.sendData1("MOTOR#18");
                    }
                }
            }
        }, JoystickView.DEFAULT_LOOP_INTERVAL);

        jscontrolCamera.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                if(isConnecting) {
                    if(power >= 70) {
                        if (angle < -90) {
                            client.sendData2("SERVO#" + Math.abs(270 + angle));
                        } else {
                            client.sendData2("SERVO#" + Math.abs(angle - 90));
                        }
                    }else if(angle == 0 && power == 0 && direction == 0){
                        client.sendData1("SERVO#95");
                    }
                }
            }
        }, JoystickView.DEFAULT_LOOP_INTERVAL / 10);
    }

    public static File takeScreenShot(WebView view, String fileName){
        Date date = new Date();
        CharSequence format = DateFormat.format("yyyy-MM-dd_hh.mm.ss", date);
        try{
            String dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/SmartCar";
            File fileDir = new File(dirPath);
            if(!fileDir.exists()){
                boolean mkdir = fileDir.mkdir();
            }

            String path = dirPath + "/" + fileName + "-" + format + ".jpeg";

            view.setDrawingCacheEnabled(true);
            Bitmap _bitmap = Bitmap.createBitmap(view.getDrawingCache());
            if(computingDetection){
                view.setDrawingCacheEnabled(false);
            }

            File imageFile = new File(path);
            FileOutputStream fileOutputStream = new FileOutputStream(imageFile);
            _bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
            fileOutputStream.flush();
            fileOutputStream.close();
            return imageFile;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }

        return null;
    }

    public static void verityStoragePermission(Activity activity){
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if(permission != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(activity, PERMISSTION_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }
    }

    public void doConnecting(View v){
        final String HOST = txtIPAddress.getText().toString();
        final int PORT_SENDER = 3000;
        final int PORT_READER = 5000;
        final int VIDEO_PORT = 8000;


        if(!isConnecting){
            client = new Client(HOST, PORT_SENDER, PORT_READER, VIDEO_PORT);
            thread = new Thread(client);
            thread.start();
        }else{
            isConnecting = false;
        }
    }

    public void doCamera(View v){
        verityStoragePermission(this);
        if(takeScreenShot(mWebView, "smartcar")!=null){
            Toast.makeText(this, "Save Image", Toast.LENGTH_SHORT).show();
        }
    }

    public void showDialog(AlertDialog.Builder builder){
        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txtPercent.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        if(viewLayout.getParent() != null){
            ((ViewGroup)viewLayout.getParent()).removeView(viewLayout);
        }

        builder.setTitle("Maximun Speed")
                .setIcon(R.drawable.setting)
                .setView(viewLayout)
                .create()
                .show();
    }

    public void doSetting(View v) {
        showDialog(builder);
    }

    public class Client implements Runnable{
        private Socket sender1;
        private Socket sender2;
        private DataOutputStream dos1;
        private DataOutputStream dos2;
        private String host;
        private int port_sender;
        private int port_reader;
        private int video_port;
        private Handler handler = new Handler();

        public Client(final String HOST, final int PORT_SENDER, final int PORT_READER, final int VIDEO_PORT){
            this.host = HOST;
            this.port_sender = PORT_SENDER;
            this.port_reader = PORT_READER;
            this.video_port = VIDEO_PORT;
        }

        public void start(){
            try {
                sender1 = new Socket(host, port_sender);
                dos1 = new DataOutputStream(sender1.getOutputStream());
                sender2 = new Socket(host, port_reader);
                dos2 = new DataOutputStream(sender2.getOutputStream());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String url = "http://" + host + ":" + video_port + "/index.html";
                        mWebView.loadUrl(url);
                    }
                });
                isConnecting = true;
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        public void stop(){
            try {
                dos1.writeUTF("CLIENT#0");
                sender1.close();
                dos1.close();
                sender2.close();
                dos2.close();
                isConnecting = false;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        public void changeStatusConnect(boolean isConnect){
            if(isConnect){
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String msg = "Connecting to server!";
                        int background = R.drawable.btn_background1;
                        int icon = R.drawable.connect1;
                        mWebView.onResume();

                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        btnConnect.setBackground(getDrawable(background));
                        btnConnect.setImageDrawable(getDrawable(icon));
                    }
                });
            }else{
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String msg = "Connecting to server failed!";
                        int background = R.drawable.btn_background;
                        int icon = R.drawable.connect;

                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        btnConnect.setBackground(getDrawable(background));
                        btnConnect.setImageDrawable(getDrawable(icon));
                        mWebView.onPause();
                    }
                });
            }
        }

        public void sendData1(String message){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        dos1.writeUTF(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        public void sendData2(String message){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        dos2.writeUTF(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        @Override
        public void run() {
            start();
            changeStatusConnect(isConnecting);

            while(!Thread.currentThread().isInterrupted()){
                bitmap = null;

                if(!isConnecting) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if(!computingDetection) {
                    new Runnable() {
                        @Override
                        public void run() {
                            mWebView.setDrawingCacheEnabled(true);
                            bitmap = Bitmap.createBitmap(mWebView.getDrawingCache());
                            mWebView.setDrawingCacheEnabled(false);
                        }
                    }.run();
                }

                if(bitmap != null && !computingDetection) {
                    new Runnable(){
                        @Override
                        public void run() {
                            detector.processImage(bitmap);
                        }
                    }.run();
                }
            }

            stop();
            changeStatusConnect(isConnecting);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(isConnecting) {
            mWebView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(isConnecting) {
            mWebView.onResume();
        }
    }
}