package org.crc32.pbzchucker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;

import static android.provider.AlarmClock.EXTRA_MESSAGE;

public class MainActivity extends AppCompatActivity {
    String chosen = null;
    Uri uchosen = null;
    EditText path = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        path = findViewById(R.id.fileName);

        Button fileButton = findViewById(R.id.file_select);
        fileButton.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                chooseFile();
            }
        });
        Button flashButton = findViewById(R.id.flash);
        final Spinner appChoice = findViewById(R.id.app_select);
        flashButton.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                String appID;
                if(appChoice.getSelectedItemPosition() == 0){
                    appID = "com.getpebble.android.basalt";
                }else{
                    appID = "nodomain.freeyourgadget.gadgetbridge";
                }
                chosen = path.getText().toString();
                attemptFlash(chosen, appID);
            }
        });
        path.setText(Environment.getExternalStorageDirectory().getPath() + "/"); //XXX: Needs to use resource placeholders to fit best practises
    }

    private static final int FILE_SELECT_CODE = 0;

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to Upload"),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        String TAG = "FILESEL";
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    Log.d(TAG, "File Uri: " + uri.toString());
                    path.setText(uri.getPath());
                    uchosen = uri;
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    public void attemptFlash(String filePath, String appID) {
        if(Build.VERSION.SDK_INT>=24){
            try{
                Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
                m.invoke(null);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        Intent sendIntent = new Intent();
        if(appID.equals("com.getpebble.android.basalt")){
            sendIntent.setComponent(new ComponentName("com.getpebble.android.basalt", "com.getpebble.android.main.activity.MainActivity"));
        }else{
            sendIntent.setComponent(new ComponentName("nodomain.freeyourgadget.gadgetbridge", "nodomain.freeyourgadget.gadgetbridge.activities.FwAppInstallerActivity")); //TODO: Add support for Google Play unofficial build
        }
        sendIntent.setPackage(appID);
        sendIntent.setAction("android.intent.action.VIEW");
        if(uchosen != null){
            sendIntent.setData(uchosen);
        }else{
            sendIntent.setData(Uri.fromFile(new File(filePath)));
        }
        startActivity(sendIntent);
    }
}
