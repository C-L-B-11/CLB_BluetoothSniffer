package com.example.clb_blutoothsniffer;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.io.File;

import androidx.core.app.ActivityCompat;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.ui.AppBarConfiguration;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;


    private MainActivity.Mode mode = MainActivity.Mode.Off;
    DateFormat df = new SimpleDateFormat("dd:MM:yyyy_HH:mm");

    Button startBT;
    Button haltestelleBT;
    Button stopBT;

    EditText lineET;
    EditText stopET;
    EditText weatherET;
    EditText commentET;
    EditText idET;
    EditText directionET;
    EditText nFahrgaste;
    EditText nGerate;
    TextView exception;
    TextView list;
    private JSONObject fileText;
    private JSONObject thisStation;
    private String filename;
    private android.bluetooth.le.BluetoothLeScanner scanner;
    android.bluetooth.le.ScanFilter filters;
    android.bluetooth.le.ScanSettings settings;
    private MainActivity.ScanCallback scb;

    private HashSet<String> hash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        startBT = findViewById(R.id.startMeasurementBT);
        haltestelleBT = findViewById(R.id.ArrivedAtStopBT);
        stopBT = findViewById(R.id.stopMeasurementBT);
        lineET = findViewById(R.id.lineET);
        stopET = findViewById(R.id.stopET);
        weatherET = findViewById(R.id.weatherET);
        commentET = findViewById(R.id.commentET);
        idET = findViewById(R.id.idET);
        directionET = findViewById(R.id.directionET);
        nFahrgaste = findViewById(R.id.nFahrgasteET);
        nGerate = findViewById(R.id.nGerateET);
        exception = findViewById(R.id.exceptionTV);
        list = findViewById(R.id.listTV);

        startBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Start();
            }
        });
        haltestelleBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Haltestelle();
            }
        });
        stopBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Stop();
            }
        });

    }




    enum Mode {
        Off,
        On
    }

    private void Start() {
        mode = MainActivity.Mode.On;
        startBT.setEnabled(false);
        stopBT.setEnabled(true);
        haltestelleBT.setEnabled(true);
        nFahrgaste.setText("0");
        nFahrgaste.setEnabled(true);
        nGerate.setText("0");
        hash=new HashSet<String>();
        fileText = new JSONObject();
        thisStation = new JSONObject();
        filename = "SemProMess" + df.format(Calendar.getInstance().getTime());
        try {

            fileText.put("Zeit", df.format(Calendar.getInstance().getTime()));
            fileText.put("Linie", lineET.getText());
            fileText.put("StartHaltestelle", stopET.getText());
            fileText.put("Richtung", directionET.getText());
            fileText.put("Id", idET.getText());
            fileText.put("Wetter", weatherET.getText());
            fileText.put("Kommentar", commentET.getText());

        } catch (Exception ex) {
            exception.setText(ex.toString());
        }

        android.content.Context ctx = getBaseContext();
        findeBL(ctx);


    }
    private void Haltestelle() {
        try {
            hash.clear();
            list.setText("");
            nGerate.setText("0");
            thisStation.put("Fahrgaste", nFahrgaste.getText());
            thisStation.put("Zeit", df.format(Calendar.getInstance().getTime()));
            fileText.accumulate("Haltestellen", thisStation);

            thisStation = new JSONObject();
        } catch (Exception ex) {
            exception.setText(ex.toString());
        }
    }
    @SuppressLint("MissingPermission")
    private void Stop() {
        mode = MainActivity.Mode.Off;

        startBT.setEnabled(true);
        stopBT.setEnabled(false);
        haltestelleBT.setEnabled(false);
        //nFahrgaste.setText("0");
        nFahrgaste.setEnabled(false);


        generateNoteOnSD();


        scanner.stopScan(scb);
        scanner = null;
        filters = null;
        settings = null;
    }
    private void findeBL(android.content.Context ctx) {
        BluetoothManager bm = null;
        BluetoothAdapter ba = null;
        boolean fail = false;
        try {

            bm = (BluetoothManager) ctx.getSystemService(BLUETOOTH_SERVICE);
            ba = bm.getAdapter();
        } catch (Exception ex) {
            exception.setText(ex.toString());
            fail = true;
        }
        if (bm == null || ba == null)
            fail = true;
        if (!fail) {
            //ba.getBluetoothLeScanner().startScan();
            exception.setText("I'm in!!");
            scanner = ba.getBluetoothLeScanner();
            settings = new android.bluetooth.le.ScanSettings.Builder()
                    .build();
            filters = new ScanFilter.Builder().build();

            scb = new MainActivity.ScanCallback(ctx);
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH},
                        1);
                return;

            }
            exception.setText("startScan");
            startScan();
            scanner.startScan(Collections.singletonList(filters), settings, scb);
        } else {
            exception.setText("huh?");
        }
    }
    @SuppressLint("MissingPermission")
    private void startScan(){
        scanner.startScan(Collections.singletonList(filters), settings, scb);
    }
    private void onLeScanResult(BluetoothDevice btDevice, byte[] bytes, String name) {
        if(name!="") {
            //exception.setText(name);
        }


        try{
            if(!hash.contains(btDevice.getAddress()))
            {
                hash.add(btDevice.getAddress());

                String Text=list.getText().toString() + btDevice.getAddress()+"\n";


                list.setText(Text);

                String Text2=nGerate.getText().toString();
                int i = Integer.parseInt(Text2)+1;
                String s = Integer.toString(i);
                nGerate.setText(s);


                thisStation.accumulate("MACs",btDevice.getAddress());
            }
        }
        catch (Exception ex){
            exception.setText(ex.toString());
        }

    }
    private void onLeScanResults(java.util.List<ScanResult> results,android.content.Context ctx) {
        /*exception.setText("results in!");
        try{
            JSONObject jo = new JSONObject();
            jo.put("Zeit",df.format(Calendar.getInstance().getTime()));
            for (ScanResult sr : results){
                jo.accumulate("MACs",sr.getDevice().getAddress());
            }
            thisStation.accumulate("Readings",jo);
        }
        catch (Exception ex){
            exception.setText(ex.toString());
        }
        if(Mode.On==mode)
        {
            findeBL(ctx);
        }
        nGerate.setText(results.size());
        */

    }
    public class ScanCallback extends android.bluetooth.le.ScanCallback {
        android.content.Context ctx;
        public ScanCallback(android.content.Context ctx){
            this.ctx = ctx;
        }

        @Override
        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
            if (android.os.Build.VERSION.SDK_INT >= 21) {

                if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {

                    onLeScanResult(result.getDevice(), result.getScanRecord().getBytes(), "");
                    return;
                }
                onLeScanResult(result.getDevice(), result.getScanRecord().getBytes(), result.getDevice().getName());
            }
        }

        @Override
        public void onBatchScanResults(java.util.List<ScanResult> results) {
            onLeScanResults(results, ctx);

        }

        @Override
        public void onScanFailed(int errorCode) {
            //exception.setText("Scan Failed:" + errorCode);
        }
    };



    public void generateNoteOnSD( ) {

            if (ActivityCompat.checkSelfPermission(scb.ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(scb.ctx, Manifest.permission.MANAGE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        2);
                return;
            }
        createFile();

    }

    private void createFile() {
        Uri pickerInitialUri = Uri.fromFile(new File(Environment.getExternalStorageDirectory().toString()+filename+".json"));
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, filename+".json");

        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);

        ActivityCompat.startActivityForResult(this,intent,1,null);

    }

    public void writeFile(Uri uri){
        exception.setText("save File");

        try {
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            outputStream.write(fileText.toString().getBytes(StandardCharsets.UTF_8));
            outputStream.close();

            Toast.makeText(getBaseContext(), "Saved", Toast.LENGTH_SHORT).show();
            fileText = new JSONObject();
        } catch (Exception ex) {
            exception.setText(ex.toString());
            Log.e(ex.getMessage(),"");
            ex.printStackTrace();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            writeFile(uri); // your file content here
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,  String[] permissions,
                                           int[] grantResults) {
         if (requestCode == 1) {
            //Log.i(TAG, "Received response for contact permissions request.");

            // We have requested multiple permissions for contacts, so all of them need to be
            // checked.
            if (verifyPermissions(grantResults)) {
                // All required permissions have been granted, display contacts fragment.
                //exception.setText("Bluetooth Access Granted");
                startScan();

            } else {
                //exception.setText("Bluetooth Access Denied");
            }

        } else if (requestCode==2) {
            if (verifyPermissions(grantResults)) {
                // All required permissions have been granted, display contacts fragment.
                exception.setText("File Access Granted");
                createFile();

            } else {
                exception.setText("File Access Denied");
            }
        }else{
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
    public static boolean verifyPermissions(int[] grantResults) {
        // At least one result must be checked.
        if (grantResults.length < 1) {
            return false;
        }

        // Verify that each required permission has been granted, otherwise return false.
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }





}