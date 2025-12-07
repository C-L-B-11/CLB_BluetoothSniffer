package com.example.clb_blutoothsniffer;

import static android.content.Context.BLUETOOTH_SERVICE;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.io.File;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.clb_blutoothsniffer.databinding.FragmentFirstBinding;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private Mode mode = Mode.Off;
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
    private ScanCallback scb;

    private HashSet<String> hash;


    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {


        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        startBT = view.findViewById(R.id.startMeasurementBT);
        haltestelleBT = view.findViewById(R.id.ArrivedAtStopBT);
        stopBT = view.findViewById(R.id.stopMeasurementBT);
        lineET = view.findViewById(R.id.lineET);
        stopET = view.findViewById(R.id.stopET);
        weatherET = view.findViewById(R.id.weatherET);
        commentET = view.findViewById(R.id.commentET);
        idET = view.findViewById(R.id.idET);
        directionET = view.findViewById(R.id.directionET);
        nFahrgaste = view.findViewById(R.id.nFahrgasteET);
        nGerate = view.findViewById(R.id.nGerateET);
        exception = view.findViewById(R.id.exceptionTV);
        list = view.findViewById(R.id.listTV);

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    enum Mode {
        Off,
        On
    }

    private void Start() {
        mode = Mode.On;
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

        android.content.Context ctx = this.getContext();
        findeBL(ctx);


    }

    private void Haltestelle() {
        try {
            hash.clear();
            list.setText("");
            nGerate.setText("0");
            thisStation.put("Fahrgaste", nFahrgaste.getText());
            fileText.accumulate("Haltestellen", thisStation);
            thisStation = new JSONObject();
        } catch (Exception ex) {
            exception.setText(ex.toString());
        }


    }

    private void Stop() {
        mode = Mode.Off;

        startBT.setEnabled(true);
        stopBT.setEnabled(false);
        haltestelleBT.setEnabled(false);
        //nFahrgaste.setText("0");
        nFahrgaste.setEnabled(false);

        android.content.Context ctx = this.getContext();
        generateNoteOnSD(ctx, filename, fileText.toString());
        fileText = new JSONObject();
        if (ActivityCompat.checkSelfPermission(scb.ctx, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        scanner.stopScan(scb);
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
            android.bluetooth.le.ScanSettings settings = new android.bluetooth.le.ScanSettings.Builder()
                    .build();
            android.bluetooth.le.ScanFilter filters = new ScanFilter.Builder().build();

            scb = new ScanCallback(ctx);
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                expRun req = new expRun();
                req.requestPerm();
                exception.setText("asked for Perm");
                while (req.granted == 0) {

                }
                if (req.granted == -1) {
                    exception.setText("No Bluetooth Perms");
                    return;
                }

            }
            exception.setText("startScan");
            scanner.startScan(Collections.singletonList(filters), settings, scb);
        } else {
            exception.setText("huh?");
        }
    }

    private void onLeScanResult(BluetoothDevice btDevice, byte[] bytes,String name) {
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

    public void generateNoteOnSD(android.content.Context context, String sFileName, String sBody) {
        try {
            File root = new File(Environment.getExternalStorageDirectory(), "Measurements");
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, sFileName+".json");
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(sBody);
            writer.flush();
            writer.close();
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            exception.setText(ex.toString());
            Log.e("ERROR",ex.getMessage());
        }
    }

    private class ScanCallback extends android.bluetooth.le.ScanCallback {
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
            exception.setText("Scan Failed:" + errorCode);
        }
    };



}

