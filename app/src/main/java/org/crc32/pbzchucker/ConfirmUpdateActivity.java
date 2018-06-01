package org.crc32.pbzchucker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ConfirmUpdateActivity extends AppCompatActivity {
    FlashService mFlashService;
    boolean mServiceBound = false;
    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FlashService.LocalBinder binder = (FlashService.LocalBinder) service;
            mFlashService = binder.getService();
            mServiceBound = true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm_update);
        final Button confirm = findViewById(R.id.confirm);
        TextView commitno = findViewById(R.id.commitno);
        TextView version = findViewById(R.id.version);
        TextView device = findViewById(R.id.fwdevice);
        TextView revision = findViewById(R.id.hwrev);
        TextView timestamp = findViewById(R.id.timestamp);
        final TextView targetdev = findViewById(R.id.targetdev);
        final TextView targetrev = findViewById(R.id.targetrev);
        final ProgressBar isLoading = findViewById(R.id.progressBar);
        confirm.setEnabled(false);
        isLoading.setVisibility(View.VISIBLE);
        int id = android.os.Process.myPid();
        Log.d("PID", String.valueOf(id));
        final String[] details;
        if ((details = getIntent().getStringArrayExtra("details")) == null) {
            finish();
        } else {
            commitno.setText(details[0]);
            version.setText(details[1]);
            device.setText(details[4]);
            revision.setText(details[2]);
            timestamp.setText(details[3]);
        }
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mServiceBound) {
                    mFlashService.permitFlash();
                    finish();
                }
            }
        });
        new Thread(new Runnable() {
            public void run() {
                while (!mServiceBound) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mFlashService.ensureConnection();
                final String[] btDetails = mFlashService.getConnectedDetails();
                if (btDetails != null && details != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            targetdev.setText(btDetails[2]);
                            targetrev.setText(btDetails[0]);
                            boolean permitFlash = true;
                            if (!details[4].equals(btDetails[2])) {
                                targetdev.setError("Target device does not match update! Do NOT Continue.");
                                permitFlash = false;
                            } else if (!details[2].equals(btDetails[0])) {
                                targetrev.setError("Target HW revision does not match update! This may be dangerous");
                            }
                            isLoading.setVisibility(View.INVISIBLE);
                            if (permitFlash) {
                                confirm.setEnabled(true);
                            }
                        }
                    });
                } else {
                    finish();
                }
            }
        }).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, FlashService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mServiceBound) {
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
    }

}
