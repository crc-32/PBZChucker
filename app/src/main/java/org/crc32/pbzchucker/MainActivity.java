package org.crc32.pbzchucker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import static android.content.Intent.ACTION_VIEW;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button flashButton = findViewById(R.id.flash);
        flashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseFile();
            }
        });
    }

    private static final int FILE_SELECT_CODE = 0;

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to Flash"),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        Log.d("FILESEL", "File Uri: " + uri.toString());
                        attemptDirectFlash(uri);
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void attemptDirectFlash(Uri filePath) {
        if (filePath == null) {
            Toast.makeText(this, "File path invalid or no file chosen",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent serviceIntent = new Intent(this, FlashService.class);
        serviceIntent.setAction(ACTION_VIEW);
        serviceIntent.setData(filePath);
        startService(serviceIntent);
    }
}
