package com.espressif.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.security.Security1;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.Transport;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;

import espressif.WifiScan;

public class WiFiScanList extends AppCompatActivity {

    private static final String TAG = WiFiScanList.class.getSimpleName();

    private ProgressBar progressBar;
    private ArrayList<WiFiAccessPoint> apDevices;
    private WiFiListAdapter adapter;
    public Session session;
    public Security security;
    public Transport transport;
    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifi_scan_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_wifi_scan_list);
        setSupportActionBar(toolbar);

        progressBar = findViewById(R.id.wifi_progress_indicator);
        progressBar.setVisibility(View.VISIBLE);

        apDevices = new ArrayList<>();
        intent = getIntent();
        final String pop = intent.getStringExtra(ProofOfPossessionActivity.KEY_PROOF_OF_POSSESSION);
        Log.e("WiFiScanList", "POP : " + pop);
        final String baseUrl = intent.getStringExtra(Provision.CONFIG_BASE_URL_KEY);
        final String transportVersion = intent.getStringExtra(Provision.CONFIG_TRANSPORT_KEY);
        final String securityVersion = intent.getStringExtra(Provision.CONFIG_SECURITY_KEY);

        ListView listView = findViewById(R.id.wifi_ap_list);
        adapter = new WiFiListAdapter(this, R.id.tv_wifi_name, apDevices);

        // Assign adapter to ListView
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {

                progressBar.setVisibility(View.VISIBLE);
                Log.d("WiFiScanList", "Device to be connected -" + apDevices.get(pos));
                callProvision(apDevices.get(pos).getWifiName());
            }
        });

        listView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {

            }
        });

        if (securityVersion.equals(Provision.CONFIG_SECURITY_SECURITY1)) {
            this.security = new Security1(pop);
        } else {
            this.security = new Security0();
        }
        if (transportVersion.equals(Provision.CONFIG_TRANSPORT_BLE)) {
            if (BLEProvisionLanding.bleTransport == null) {
            } else {
                transport = BLEProvisionLanding.bleTransport;
                fetchScanList();
            }
        }
    }

    @Override
    public void onBackPressed() {
//        BLEProvisionLanding.isBleWorkDone = true;
        super.onBackPressed();
    }

    private void callProvision(String ssid) {

        Log.e("WiFiScanList", "Selected AP -" + ssid);
        finish();
        Intent launchProvisionInstructions = new Intent(getApplicationContext(), ProvisionActivity.class);
        launchProvisionInstructions.putExtras(getIntent());
        launchProvisionInstructions.putExtra(Provision.PROVISIONING_WIFI_SSID, ssid);
        startActivity(launchProvisionInstructions);
    }

    private void fetchScanList() {

        session = new Session(this.transport, this.security);
        session.sessionListener = new Session.SessionListener() {
            @Override
            public void OnSessionEstablished() {
                Log.d("WiFiScanList", "Session established");
                startWifiScan();
            }

            @Override
            public void OnSessionEstablishFailed(Exception e) {
                Log.d("WiFiScanList", "Session failed");
            }
        };
        session.init(null);
    }

    public void startWifiScan() {
        WifiScan.CmdScanStart configRequest = WifiScan.CmdScanStart.newBuilder()
                .setBlocking(true)
                .setPassive(false)
                .setGroupChannels(0)
                .setPeriodMs(120)
                .build();
        WifiScan.WiFiScanMsgType msgType = WifiScan.WiFiScanMsgType.TypeCmdScanStart;
        WifiScan.WiFiScanPayload payload = WifiScan.WiFiScanPayload.newBuilder()
                .setMsg(msgType)
                .setCmdScanStart(configRequest)
                .build();
        byte[] data = this.security.encrypt(payload.toByteArray());
        transport.sendConfigData("prov-scan", data, new ResponseListener() {
            @Override
            public void onSuccess(byte[] returnData) {
                processStartScan(returnData);
                Log.d("WiFiScan", "Successfully sent start scan");

                getWifiScanStatus();

            }

            @Override
            public void onFailure(Exception e) {

            }
        });
    }

    private void processStartScan(byte[] responseData) {
        byte[] decryptedData = this.security.decrypt(responseData);
        try {
            WifiScan.WiFiScanPayload payload = WifiScan.WiFiScanPayload.parseFrom(decryptedData);
            WifiScan.RespScanStart response = WifiScan.RespScanStart.parseFrom(payload.toByteArray());
            // TODO Proto should send status as ok started or failed
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    private void getWifiScanStatus() {

        WifiScan.CmdScanStatus configRequest = WifiScan.CmdScanStatus.newBuilder()
                .build();
        WifiScan.WiFiScanMsgType msgType = WifiScan.WiFiScanMsgType.TypeCmdScanStatus;
        WifiScan.WiFiScanPayload payload = WifiScan.WiFiScanPayload.newBuilder()
                .setMsg(msgType)
                .setCmdScanStatus(configRequest)
                .build();
        byte[] data = this.security.encrypt(payload.toByteArray());
        transport.sendConfigData("prov-scan", data, new ResponseListener() {
            @Override
            public void onSuccess(byte[] returnData) {
                Log.d("WiFiScan", "Successfully got scan result");
                processGetWifiStatus(returnData);
            }

            @Override
            public void onFailure(Exception e) {

            }
        });
    }

    private void processGetWifiStatus(byte[] responseData) {
        byte[] decryptedData = this.security.decrypt(responseData);

        boolean scanFinished;

        try {
            WifiScan.WiFiScanPayload payload = WifiScan.WiFiScanPayload.parseFrom(decryptedData);
            WifiScan.RespScanStatus response = payload.getRespScanStatus();

            scanFinished = response.getScanFinished();

//            if(scanFinished == true) {
            getWiFiScanList(response.getResultCount());
//            } else {
//                getWifiScanStatus();
//            }


        } catch (InvalidProtocolBufferException e) {

            e.printStackTrace();
        }
    }

    private void getWiFiScanList(int count) {

        Log.d("WIFIScanList", "Getting " + count + " SSIDs");
        WifiScan.CmdScanResult configRequest = WifiScan.CmdScanResult.newBuilder()
                .setStartIndex(0)
                .setCount(count)
                .build();
        WifiScan.WiFiScanMsgType msgType = WifiScan.WiFiScanMsgType.TypeCmdScanResult;
        WifiScan.WiFiScanPayload payload = WifiScan.WiFiScanPayload.newBuilder()
                .setMsg(msgType)
                .setCmdScanResult(configRequest)
                .build();
        byte[] data = this.security.encrypt(payload.toByteArray());
        transport.sendConfigData("prov-scan", data, new ResponseListener() {
            @Override
            public void onSuccess(byte[] returnData) {
                Log.d("WiFiScan", "Successfully got SSID list");
                processGetSSIDs(returnData);
            }

            @Override
            public void onFailure(Exception e) {

            }
        });

    }

    private void processGetSSIDs(byte[] responseData) {

        byte[] decryptedData = this.security.decrypt(responseData);
        try {
            WifiScan.WiFiScanPayload payload = WifiScan.WiFiScanPayload.parseFrom(decryptedData);
            final WifiScan.RespScanResult response = payload.getRespScanResult();

            for (int i = 0; i < response.getEntriesCount(); i++) {
//                apDevices.add(response.getEntries(i).getSsid().toStringUtf8());
            }

            runOnUiThread(new Runnable() {

                public void run() {

                    // do your modifications here

                    for (int i = 0; i < response.getEntriesCount(); i++) {

                        Log.e("WifiScan", "Response : " + response.getEntries(i).getSsid().toStringUtf8());
                        String ssid = response.getEntries(i).getSsid().toStringUtf8();
                        int rssi = response.getEntries(i).getRssi();
                        boolean isAvailable = false;

                        for (int index = 0; index < apDevices.size(); index++) {

                            if (ssid.equals(apDevices.get(index).getWifiName())) {

                                isAvailable = true;

                                if (apDevices.get(index).getRssi() < rssi) {

                                    apDevices.get(index).setRssi(rssi);
                                }
                                break;
                            }
                        }

                        if (!isAvailable) {

                            WiFiAccessPoint wifiAp = new WiFiAccessPoint();
                            wifiAp.setWifiName(response.getEntries(i).getSsid().toStringUtf8());
                            wifiAp.setRssi(response.getEntries(i).getRssi());
                            apDevices.add(wifiAp);
                            Log.e(TAG, "" + ssid + " added in list : " + wifiAp.getWifiName() + ", RSSI : " + wifiAp.getRssi());
                        }

                        Log.e(TAG, "Size of  list : " + apDevices.size());
                    }

                    progressBar.setVisibility(View.INVISIBLE);
                    adapter.notifyDataSetChanged();
                }
            });

        } catch (InvalidProtocolBufferException e) {

            e.printStackTrace();
        }
    }
}