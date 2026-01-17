package com.example.clb_blutoothsniffer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private android.location.LocationManager locationManager;
    private android.location.LocationListener locationListener;
    private SensorManager sensorManager;
    private float currentSpeedKMH = 0f;

    private float speedThresholdMS = 5.56f;
    private long currentWindowDuration = 30 * 1000;
    private int rssiThreshold = -85;
    private int minPingsRequired = 3;

    private CheckBox cbUseSpeed, cbUseRSSI, cbUsePings, cbUseDuration;
    private SeekBar sbSpeed, sbRSSI, sbPings, sbDuration;
    private TextView statusTV, lblSpeedValue, lblDurationValue, lblRSSIValue, lblPingsValue, lblPrognose;
    private Button startBT, haltestelleBT, stopBT, btnBus, btnBahn, btnZug;
    private EditText lineET, stopET, directionET,commentET, idET, nFahrgaste, nGerate, nFilteredET, nHst;
    private TextView exception;

    private final int COLOR_PASTEL_GREEN = Color.parseColor("#77926F");
    private final int COLOR_DISABLED_GREY = Color.parseColor("#A9A9A9");

    private Mode mode = Mode.Off;
    private boolean windowActive = false;
    private boolean wasTriggeredInThisSegment = false;
    private Handler countdownHandler = new Handler();
    private Runnable countdownRunnable;

    private JSONObject fileText;
    private HashMap<String, RSSIStats> bluetoothData = new HashMap<>();
    private BluetoothLeScanner scanner;
    private ScanCallback scb;


    private String filename;
    // NEU: Für die Live-Überwachung des Scanners
    private long lastPacketTimestamp = 0;

    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = true;
                    for (boolean b : result.values()) if (!b) granted = false;
                    if (granted) Start();
                    else Toast.makeText(this, "Berechtigungen verweigert!", Toast.LENGTH_SHORT).show();
                }
        );

        initUI();
        setupFilters();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);

        locationManager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new android.location.LocationListener() {
            @Override
            public void onLocationChanged(android.location.Location location) {
                if (location.hasSpeed()) {
                    float gpsSpeedKMH = location.getSpeed() * 3.6f;
                    currentSpeedKMH = (gpsSpeedKMH < 1.0f) ? 0 : (gpsSpeedKMH * 0.8f) + (currentSpeedKMH * 0.2f);
                }
                checkSpeedTrigger();
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };
    }

    private void checkSpeedTrigger() {
        if (mode == Mode.On && cbUseSpeed.isChecked() && !windowActive && !wasTriggeredInThisSegment) {
            if ((currentSpeedKMH / 3.6f) > speedThresholdMS) {
                startMeasurementWindow();
            }
        }
    }

    private void startMeasurementWindow() {
        if (windowActive) return;
        windowActive = true;
        wasTriggeredInThisSegment = true;
        haltestelleBT.setEnabled(false);
        haltestelleBT.setBackgroundTintList(android.content.res.ColorStateList.valueOf(COLOR_DISABLED_GREY));

        if (cbUseDuration.isChecked()) {
            final long[] timeLeft = {currentWindowDuration / 1000};
            countdownRunnable = new Runnable() {
                @SuppressLint("DefaultLocale")
                @Override
                public void run() {
                    if (windowActive && timeLeft[0] > 0) {
                        statusTV.setText(String.format("MESSUNG: %ds | %.1f km/h", timeLeft[0], currentSpeedKMH));
                        timeLeft[0]--;
                        countdownHandler.postDelayed(this, 1000);
                    } else if (windowActive) {
                        stopWindowAuto();
                    }
                }
            };
            countdownHandler.post(countdownRunnable);
        } else {
            statusTV.setText("Dauer-Messung aktiv");
        }
    }

    private void stopWindowAuto() {
        windowActive = false;
        countdownHandler.removeCallbacks(countdownRunnable);
        haltestelleBT.setEnabled(true);
        haltestelleBT.setBackgroundTintList(android.content.res.ColorStateList.valueOf(COLOR_PASTEL_GREEN));
        statusTV.setText("Fenster zu. Bitte Fahrgäste eintragen & CUT drücken.");
    }

    private void Start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                });
                return;
            }
        }
        filename = "SemProMess" + new SimpleDateFormat("dd:MM:yyyy_HH:mm").format(Calendar.getInstance().getTime());
        mode = Mode.On;
        wasTriggeredInThisSegment = false;
        currentSpeedKMH = 0;
        setSettingsEnabled(false);

        startBT.setEnabled(false);
        stopBT.setEnabled(true);
        haltestelleBT.setEnabled(true);
        nFahrgaste.setEnabled(true);
        nFahrgaste.requestFocus();
        nHst.setText("0");
        nFahrgaste.setText("");

        haltestelleBT.setBackgroundTintList(android.content.res.ColorStateList.valueOf(COLOR_PASTEL_GREEN));

        if (!cbUseSpeed.isChecked()) {
            if (cbUseDuration.isChecked()) startMeasurementWindow();
            else { windowActive = true; statusTV.setText("Dauermessung aktiv."); }
        } else {
            windowActive = false;
            statusTV.setText("Warte auf Speed-Trigger...");
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.println(Log.WARN,"PERM","Permission Granted");
            locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
        }
        else
            Log.println(Log.ERROR,"PERM","Permission Denied");

        bluetoothData.clear();
        fileText = new JSONObject();
        try {
            fileText.put("Startzeit", new SimpleDateFormat("dd.MM.yyyy_HH:mm").format(Calendar.getInstance().getTime()));
            fileText.put("Linie", lineET.getText().toString());
            fileText.put("Haltestelle", stopET.getText().toString());
            fileText.put("Richtung",directionET.getText().toString());
            fileText.put("Kommentar",commentET.getText().toString());
        } catch (Exception ex) { exception.setText(ex.toString()); }
        findeBL();

    }

    private void Haltestelle() {
        try {
            windowActive = false;
            JSONObject thisStation = new JSONObject();
            JSONObject macsJson = new JSONObject();
            int netCount = 0;

            for (Map.Entry<String, RSSIStats> entry : bluetoothData.entrySet()) {
                RSSIStats stats = entry.getValue();
                if ((!cbUseRSSI.isChecked() || stats.getAverage() >= rssiThreshold) &&
                        (!cbUsePings.isChecked() || stats.count >= minPingsRequired)) {
                    netCount++;
                    JSONObject d = new JSONObject();
                    d.put("pings", stats.count);
                    d.put("rssi_avrg", stats.getAverage());
                    d.put("rssi_min", stats.min);
                    d.put("rssi_max", stats.max);
                    macsJson.put(entry.getKey(), d);
                }
            }

            //thisStation.put("ID", idET.getText().toString());
            thisStation.put("Fahrgaeste", nFahrgaste.getText().toString());
            thisStation.put("Netto", netCount);
            thisStation.put("Speed_kmh", String.format("%.2f", currentSpeedKMH));
            thisStation.put("MacDetails", macsJson);
            fileText.accumulate("Abschnitte", thisStation);

            nHst.setText(String.valueOf(Integer.parseInt(nHst.getText().toString()) + 1));

            bluetoothData.clear();
            nGerate.setText("0");
            nFilteredET.setText("0");



            if (cbUseSpeed.isChecked())
            {
                statusTV.setText("Schnitt gesichert. Warte auf Abfahrt.");
                wasTriggeredInThisSegment = false;
                windowActive = false;
            }
            else if (cbUseDuration.isChecked()) {
                windowActive = false;
                startMeasurementWindow();

            }
            else
                windowActive = true;
        } catch (Exception ex) { exception.setText(ex.getMessage()); }
    }

    private void Stop() {
        mode = Mode.Off;
        windowActive = false;
        countdownHandler.removeCallbacks(countdownRunnable);
        if (locationManager != null) locationManager.removeUpdates(locationListener);
        if (scanner != null && scb != null) {
            try { scanner.stopScan(scb); } catch (Exception ignored) {}
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, filename+ ".json");
        startActivityForResult(intent, 1);

        startBT.setEnabled(true);
        stopBT.setEnabled(false);
        haltestelleBT.setEnabled(false);
        setSettingsEnabled(true);

    }

    @Override public void onSensorChanged(SensorEvent event) {}
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void initUI() {
        startBT = findViewById(R.id.startMeasurementBT);
        haltestelleBT = findViewById(R.id.ArrivedAtStopBT);
        stopBT = findViewById(R.id.stopMeasurementBT);
        statusTV = findViewById(R.id.statusTV);
        lblPrognose = findViewById(R.id.lblPrognose);
        nFahrgaste = findViewById(R.id.nFahrgasteET);
        nGerate = findViewById(R.id.nGerateET);
        nFilteredET = findViewById(R.id.nFilteredET);
        nHst = findViewById(R.id.nHstET);
        lineET = findViewById(R.id.lineET);
        directionET = findViewById(R.id.directionET);
        commentET = findViewById(R.id.commentET);
        idET = findViewById(R.id.idET);
        stopET = findViewById(R.id.stopET);
        exception = findViewById(R.id.exceptionTV);

        btnBus = findViewById(R.id.btnBus);
        btnBahn = findViewById(R.id.btnBahn);
        btnZug = findViewById(R.id.btnZug);

        startBT.setOnClickListener(v -> Start());
        haltestelleBT.setOnClickListener(v -> Haltestelle());
        stopBT.setOnClickListener(v -> pseudoStop());

        btnBus.setOnClickListener(v -> applyPreset("Bus"));
        btnBahn.setOnClickListener(v -> applyPreset("Bahn"));
        btnZug.setOnClickListener(v -> applyPreset("Zug"));
    }

    private void applyPreset(String type) {
        if (mode == Mode.On) return;
        switch (type) {
            case "Bus": sbSpeed.setProgress(10); sbPings.setProgress(9); sbRSSI.setProgress(20); break;
            case "Bahn": sbSpeed.setProgress(20); sbPings.setProgress(14); sbRSSI.setProgress(15); break;
            case "Zug": sbSpeed.setProgress(35); sbPings.setProgress(24); sbRSSI.setProgress(5); break;
        }
        Toast.makeText(this, "Szenario " + type + " geladen", Toast.LENGTH_SHORT).show();
    }

    private void setupFilters() {
        lblSpeedValue = findViewById(R.id.lblSpeedValue);
        lblDurationValue = findViewById(R.id.lblDurationValue);
        lblRSSIValue = findViewById(R.id.lblRSSIValue);
        lblPingsValue = findViewById(R.id.lblPingsValue);
        cbUseSpeed = findViewById(R.id.cbUseSpeed);
        cbUseRSSI = findViewById(R.id.cbUseRSSI);
        cbUsePings = findViewById(R.id.cbUsePings);
        cbUseDuration = findViewById(R.id.cbUseDuration);
        sbSpeed = findViewById(R.id.sbSpeed);
        sbRSSI = findViewById(R.id.sbRSSI);
        sbPings = findViewById(R.id.sbPings);
        sbDuration = findViewById(R.id.sbDuration);

        setupSlider(sbSpeed, lblSpeedValue, 5, " km/h", v -> speedThresholdMS = v / 3.6f);
        setupSlider(sbDuration, lblDurationValue, 10, " s", v -> currentWindowDuration = v * 1000L);
        setupSlider(sbRSSI, lblRSSIValue, -100, " dBm", v -> rssiThreshold = v - 100);
        setupSlider(sbPings, lblPingsValue, 1, "", v -> minPingsRequired = v);

        cbUseSpeed.setOnCheckedChangeListener((v, is) -> sbSpeed.setEnabled(is));
        cbUseRSSI.setOnCheckedChangeListener((v, is) -> sbRSSI.setEnabled(is));
        cbUsePings.setOnCheckedChangeListener((v, is) -> sbPings.setEnabled(is));
        cbUseDuration.setOnCheckedChangeListener((v, is) -> sbDuration.setEnabled(is));
    }

    private void setupSlider(SeekBar sb, TextView lbl, int offset, String unit, OnValueChange listener) {
        int initialVal = sb.getProgress() + offset;
        lbl.setText(initialVal + unit);
        listener.onChange(initialVal);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean f) {
                int v = p + offset; lbl.setText(v + unit); listener.onChange(v);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void findeBL() {
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bm == null || bm.getAdapter() == null) return;

        scanner = bm.getAdapter().getBluetoothLeScanner();
        if (scanner == null) return;

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .build();

        scb = new ScanCallback() {
            @Override public void onScanResult(int ct, ScanResult r) {
                if (!windowActive) return;
                lastPacketTimestamp = System.currentTimeMillis();
                String mac = r.getDevice().getAddress();
                if (!bluetoothData.containsKey(mac)) bluetoothData.put(mac, new RSSIStats());
                bluetoothData.get(mac).addValue(r.getRssi());
                updateLiveStats();
            }
        };
        ScanFilter filter = new ScanFilter.Builder().build();
        if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            scanner.startScan(Collections.singletonList(filter), settings, scb);
            Log.println(Log.INFO,"START","STARTED");
        }
        else
        {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH},
                    1);
            Log.println(Log.ERROR,"START","FATAAAAAAL");
        }
    }

    private void updateLiveStats() {
        int brute = bluetoothData.size();
        int net = 0, borderline = 0;
        for (RSSIStats s : bluetoothData.values()) {
            boolean rssiPass = !cbUseRSSI.isChecked() || s.getAverage() >= rssiThreshold;
            boolean pingsPass = !cbUsePings.isChecked() || s.count >= minPingsRequired;
            if (rssiPass && pingsPass) net++;
            else if (rssiPass && s.count >= (minPingsRequired * 0.7)) borderline++;
        }

        final int fb = brute, fn = net, fbl = borderline;
        final long diff = System.currentTimeMillis() - lastPacketTimestamp;

        runOnUiThread(() -> {
            nGerate.setText(String.valueOf(fb));
            nFilteredET.setText(String.valueOf(fn));

            if(lblPrognose != null) {
                lblPrognose.setText("Prognose: " + (fn + fbl) + " | Latency: " + diff + "ms");

                // Farblogik für Dark-Mode Kompatibilität:
                if (diff > 3000) {
                    // Scanner stockt -> Warnung in Rot
                    lblPrognose.setTextColor(Color.RED);
                } else {
                    // Normalbetrieb -> Knalliges Grün oder Weiß für beste Lesbarkeit
                    // Color.GREEN ist im Dark Mode sehr gut sichtbar.
                    lblPrognose.setTextColor(Color.GREEN);
                }
            }
        });
    }

    private void setSettingsEnabled(boolean e) {
        cbUseSpeed.setEnabled(e); cbUseRSSI.setEnabled(e); cbUsePings.setEnabled(e); cbUseDuration.setEnabled(e);
        sbSpeed.setEnabled(e && cbUseSpeed.isChecked());
        sbRSSI.setEnabled(e && cbUseRSSI.isChecked());
        sbPings.setEnabled(e && cbUsePings.isChecked());
        sbDuration.setEnabled(e && cbUseDuration.isChecked());
        btnBus.setEnabled(e); btnBahn.setEnabled(e); btnZug.setEnabled(e);
    }

    private void pseudoStop() {
        new AlertDialog.Builder(this).setTitle("Beenden?").setMessage("Messung jetzt abschließen?")
                .setPositiveButton("Ja", (d, w) -> Stop()).setNegativeButton("Nein", null).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                os.write(fileText.toString(4).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Datei gespeichert!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { exception.setText("Fehler: " + e.getMessage()); }
        }
    }

    public static class RSSIStats {
        int count = 0; long sum = 0; int min = 0; int max = -120;
        public void addValue(int r) {
            if (count == 0) { min = r; max = r; }
            else { if (r < min) min = r; if (r > max) max = r; }
            sum += r; count++;
        }
        public int getAverage() { return count > 0 ? (int)(sum/count) : -100; }
    }

    interface OnValueChange { void onChange(int val); }
    enum Mode { Off, On }
}