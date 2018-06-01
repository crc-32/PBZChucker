package org.crc32.pbzchucker;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static android.content.Intent.ACTION_VIEW;
import static java.lang.Thread.sleep;

public class FlashService extends IntentService {
    int lastErrorID;
    InputStream pInputStream;
    OutputStream pOutputStream;
    PebbleProtocolHelper protocolHelper;
    Intent updateFileIntent;
    PebbleProtocolHelper.HandleResponse pResponse;
    Thread readThread;
    NotificationManager notificationManager;
    BluetoothSocket pebbleSocket;
    private IBinder mBinder = new LocalBinder();
    private Handler mHandler;
    private boolean isInstalling = false;
    private boolean waitingForPermission = false;
    private Intent launchingIntent = null;

    public FlashService() {
        super("FlashService");
    }

    @Override
    protected void onHandleIntent(Intent workIntent) {
        if (launchingIntent == null) {
            launchingIntent = workIntent;
        }
        if (workIntent.getAction() != null) {
            switch (workIntent.getAction()) {
                case ACTION_VIEW:
                    if (!parseFileAndFlash(workIntent, FlashService.this)) {
                        setNotif(1, 0, false);
                        stopSelf();
                    }
                    break;
                default:
                    stopSelf();
                    break;
            }
        } else {
            stopSelf();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();
        createNotificationChannel("progress", "Progress", "Displays the flash progress, or if it's failed");
    }

    public void permitFlash() {
        if (waitingForPermission) {
            waitingForPermission = false;
            if (doFlash()) {
                setNotif(2, 0, false);
            } else {
                setNotif(1, 0, false);
                stopSelf();
            }
        }
    }

    private void setNotif(int type, int progress, boolean pending) {
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "progress")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        switch (type) {
            case 0:
                mBuilder.setContentTitle("Flashing pebble firmware update...");
                mBuilder.setProgress(100, progress, pending);
                mBuilder.setOngoing(true);
                if (notificationManager != null) {
                    notificationManager.notify(100, mBuilder.build());
                }
                break;
            case 1:
                mBuilder.setContentTitle("Failed to flash pebble");
                try {
                    if (lastErrorID != 0) {
                        mBuilder.setContentText(getString(lastErrorID));
                    } else {
                        mBuilder.setContentText(getString(R.string.unknowne));
                    }
                    if (notificationManager != null) {
                        notificationManager.cancel(100);
                        notificationManager.notify(101, mBuilder.build());
                    }
                } catch (android.content.res.Resources.NotFoundException e) {
                    e.printStackTrace();
                }
                break;
            case 2:
                mBuilder.setContentTitle("Flashing success");
                mBuilder.setContentText("Your Pebble firmware is updated");
                if (notificationManager != null) {
                    notificationManager.cancel(100);
                    notificationManager.notify(101, mBuilder.build());
                }
                break;
        }
    }

    public String[] getConnectedDetails() {
        try {
            PebbleProtocolHelper.HandleResponse cache = null;
            while (cache == null || cache.triggerEndpoint != 16) {
                sendMessageAndWaitForResponse((short) 16, (byte) 0);
                cache = pResponse;
            }
            return cache.interpretedData;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void createNotificationChannel(String id, String mname, String desc) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(id, mname, importance);
            channel.setDescription(desc);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        }
    }

    private boolean parseFileAndFlash(Intent fileIntent, Context context) {
        setNotif(0, 1, true);
        if (!ensureConnection()) {
            setNotif(1, 0, false);
            return false;
        }
        if (fileIntent.getData() != null) {
            ZipInputStream pbz;
            updateFileIntent = fileIntent;
            ContentResolver contentResolver = context.getContentResolver();
            try {
                pbz = new ZipInputStream(contentResolver.openInputStream(fileIntent.getData()));
            } catch (FileNotFoundException e) {
                mHandler.post(new ToastRunnable(getString(R.string.file_404)));
                lastErrorID = R.string.file_404;
                e.printStackTrace();
                return false;
            }

            boolean found = false;
            ZipEntry entry;
            String jsonManifest = "";
            StringBuilder manifestBuilder = new StringBuilder();
            JSONObject manifestObject;
            try {
                while (!found && (entry = pbz.getNextEntry()) != null) {
                    if (entry.getName().equals("manifest.json")) {
                        found = true;
                        BufferedReader br = new BufferedReader(new InputStreamReader(pbz, "ASCII7"));
                        String line;

                        while ((line = br.readLine()) != null) {
                            manifestBuilder.append(line);
                        }
                        jsonManifest = manifestBuilder.toString();
                    }
                }
                pbz.closeEntry();
                pbz.close();
            } catch (IOException e) {
                mHandler.post(new ToastRunnable(getString(R.string.file_noread)));
                lastErrorID = R.string.file_noread;
                e.printStackTrace();
                return false;
            }
            try {
                manifestObject = new JSONObject(jsonManifest);
            } catch (JSONException e) {
                mHandler.post(new ToastRunnable(getString(R.string.file_badjson)));
                lastErrorID = R.string.file_badjson;
                e.printStackTrace();
                return false;
            }
            try {
                Log.d("ManifestInterpret", "Found manifest detailing commit: " + manifestObject.getJSONObject("firmware").getString("commit"));
                String detailDevice;
                detailDevice = protocolHelper.parseForDevice(manifestObject.getJSONObject("firmware").getString("hwrev"));
                String[] details = {manifestObject.getJSONObject("firmware").getString("commit"), manifestObject.getJSONObject("firmware").getString("versionTag"), manifestObject.getJSONObject("firmware").getString("hwrev"), manifestObject.getJSONObject("firmware").getString("timestamp"), detailDevice};
                Intent confirmationDialog = new Intent(getApplicationContext(), ConfirmUpdateActivity.class);
                confirmationDialog.putExtra("details", details);
                waitingForPermission = true;
                startActivity(confirmationDialog);
                return true;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private boolean doFlash() {
        if (!ensureConnection()) {
            return false;
        }
        prepareRead();
        if (!isInstalling) {
            isInstalling = true;
        }
        return false;
    }

    private void endRead() {
        if (readThread != null && readThread.isAlive() && !readThread.isInterrupted()) {
            readThread.interrupt();
        }
    }

    private void prepareRead() {
        if (readThread == null || !readThread.isAlive()) {
            readThread = new Thread(new Runnable() {
                public void run() {
                    int errCount = 0;
                    while (!readThread.isInterrupted()) {
                        try {
                            int read;
                            byte[] buffer = new byte[8192];
                            read = pInputStream.read(buffer, 0, 4);
                            while (read < 4) {
                                read += pInputStream.read(buffer, read, 4 - read);
                            }
                            ByteBuffer buf = ByteBuffer.wrap(buffer);
                            buf.order(ByteOrder.BIG_ENDIAN);
                            short length = buf.getShort();
                            short endpoint = buf.getShort();
                            if (length < 0 || length > 8192) {
                                Log.e("Blue", "Got packet of invalid length:" + length);
                            } else {
                                read = pInputStream.read(buffer, 4, length);
                                while (read < length) {
                                    read += pInputStream.read(buffer, read + 4, length - read);
                                }
                                if ((pResponse = protocolHelper.handlePacket(buffer)) == null) {
                                    Log.e("Blue", "Couldn't process packet");
                                }
                            }
                        } catch (IOException e) {
                            if (!e.getMessage().equals("bt socket closed, read return: -1") && errCount < 5) {
                                e.printStackTrace();
                                return;
                            } else {
                                errCount++;
                            }
                        }
                    }
                }
            });
            readThread.start();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onDestroy() {
        endRead();
        if (notificationManager != null) {
            notificationManager.cancel(100);
        }
        super.onDestroy();
    }

    private boolean sendMessageAndWaitForResponse(short endpoint, byte cmd) throws IOException {
        pResponse = null;
        protocolHelper.sendMessage(endpoint, cmd);
        int attempt = 0;
        while ((pResponse == null) && attempt < 10) {
            try {
                sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
            attempt++;
        }
        return pResponse != null;
    }

    private boolean bluetoothInit() {
        if (pebbleSocket != null || pInputStream != null || pOutputStream != null) {
            return true;
        }
        Log.d("Blue", "Initializing Bluetooth");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                if (!bluetoothAdapter.enable()) {
                    return false;
                }
            }
            pebbleSocket = getBluetoothSocket(bluetoothAdapter);
            if (pebbleSocket == null) {
                return false;
            }
            try {
                int attempt = 0;
                while (!pebbleSocket.isConnected() && attempt < 10) {
                    try {
                        pebbleSocket.connect();
                    } catch (IOException e) {
                        Log.e("Blue", "Pebble failed to connect, retrying (" + attempt + ")");
                    }
                    sleep(100);
                    attempt++;
                }
                if (!pebbleSocket.isConnected()) {
                    lastErrorID = R.string.bt_connfail;
                    return false;
                }
                pInputStream = pebbleSocket.getInputStream();
                pOutputStream = pebbleSocket.getOutputStream();
                if (pInputStream == null || pOutputStream == null) {
                    return false;
                }
                protocolHelper = new PebbleProtocolHelper(pInputStream, pOutputStream);
            } catch (IOException e) {
                e.printStackTrace();
                setNotif(1, 0, false);
                return false;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
            Log.d("Blue", "Bluetooth init success");
            prepareRead();
            return true;
        }
        return false;
    }

    public boolean ensureConnection() {
        if (bluetoothInit()) {
            return true;
        } else {
            boolean isInit = false;
            int attempt = 0;
            while (!isInit && attempt < 10) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                attempt++;
                Log.i("BlueEnsureConnection", "Could not ensure connection, retrying (" + attempt + ")");
                isInit = bluetoothInit();
            }
            if (!isInit) {
                mHandler.post(new ToastRunnable(getString(lastErrorID)));
                return false;
            }
            prepareRead();
            return true;
        }
    }

    private BluetoothSocket getBluetoothSocket(BluetoothAdapter bluetoothAdapter) {
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

            if (bondedDevices.size() > 0) {
                Object[] devices = bondedDevices.toArray();
                Object pebbleDevice = null;
                for (Object device : devices) {
                    if (((BluetoothDevice) device).getName().contains("Pebble")) {
                        pebbleDevice = device;
                    }
                }
                if (pebbleDevice == null) {
                    lastErrorID = R.string.bt_nodevice;
                    return null;
                }
                BluetoothSocket socket;
                ParcelUuid[] uuids = ((BluetoothDevice) pebbleDevice).getUuids();
                try {
                    socket = ((BluetoothDevice) pebbleDevice).createRfcommSocketToServiceRecord(uuids[0].getUuid());
                } catch (IOException e) {
                    e.printStackTrace();
                    mHandler.post(new ToastRunnable(getString(R.string.unknowne)));
                    lastErrorID = R.string.unknowne;
                    return null;
                }
                return socket;
            }
        }
        return null;
    }

    public class LocalBinder extends Binder {
        FlashService getService() {
            return FlashService.this;
        }
    }

    private class ToastRunnable implements Runnable {
        String mText;

        ToastRunnable(String text) {
            mText = text;
        }

        @Override
        public void run() {
            Toast.makeText(getApplicationContext(), mText, Toast.LENGTH_LONG).show();
        }
    }
}
