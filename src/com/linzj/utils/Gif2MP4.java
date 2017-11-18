/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linzj.utils;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import android.app.Activity;
import android.widget.TextView;
import android.os.Bundle;
import android.content.Intent;
import android.widget.Toast;
import android.net.Uri;
import android.util.Log;
import android.database.Cursor;


public class Gif2MP4 extends Activity
{
    private static String TAG = "gif2mp4";
    /** Called when the activity is first created. */
    @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);

            /* Create a TextView and set its content.
             * the text is retrieved by calling a native
             * function.
             */
            TextView  tv = new TextView(this);
            tv.setText( "hello" );
            setContentView(tv);
            showFileChooser();
        }

    private static final int FILE_SELECT_CODE = 0;

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT); 
        intent.setType("*/*"); 
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to Convert"),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.", 
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String getLibDir()
    {
        return getApplicationInfo().dataDir + "/lib";
    }

    private String getOutputPath(String inputPath)
    {
        int lastSlash = inputPath.lastIndexOf("/");
        if (lastSlash == -1) {
            Log.e(TAG, "impossible no lastSlash: " + inputPath);
            throw new RuntimeException("impossible no lastSlash: " + inputPath);
        }
        String outputDirectory = inputPath.substring(0, lastSlash + 1);
        String inputFileName = inputPath.substring(lastSlash + 1);
        int lastDot = inputFileName.lastIndexOf(".");
        if (lastDot == -1) {
            Log.e(TAG, "impossible no lastDot: " + inputPath);
            throw new RuntimeException("impossible no lastDot");
        }
        String outputFileName = inputFileName.substring(0, lastDot) + ".mp4";
        return outputDirectory + outputFileName;
    }
    
    private String getPath(Uri uri) {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = { "_data" };
            Cursor cursor = null;

            try {
                Log.e(TAG, "getPath: resolving the cursor.");
                cursor = this.getContentResolver().query(uri, projection, null, null, null);
                Log.e(TAG, "getPath: cursor: " + cursor.toString());
                int column_index = cursor.getColumnIndexOrThrow("_data");
                Log.e(TAG, "getPath: column_index: " + column_index);
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                // Eat it
                Log.e(TAG, "getPath: exception: " + e.getMessage());
            }
        }
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private int startAndWaitProcess(String cmd)
    {
        Log.e(TAG, "cmd: " + cmd);
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);
        } catch (java.io.IOException e) {
            Log.e(TAG, String.format("IOException occurs: %s", e.getLocalizedMessage()));
            Toast.makeText(this, String.format("IOException occurs: %s", e.getLocalizedMessage()), 
                    Toast.LENGTH_SHORT).show();
            return -1;
        }
        int exitcode = -1;
        try {
            exitcode = p.waitFor();
        } catch (InterruptedException e) {
            Toast.makeText(this, "some one send a interrupt exception", 
                    Toast.LENGTH_SHORT).show();
        }
        try {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String errString = String.format("Error code: %d, error: %s", (exitcode), errorReader.readLine());
            Log.e(TAG, errString);
            Toast.makeText(this, errString,
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return exitcode;
    }

    private void scanOutputFile(String outputPath) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri fileUri = Uri.fromFile(new File(outputPath));
        intent.setData(fileUri);
        sendBroadcast(intent);
    }

    private void myOnActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    // Get the Uri of the selected file 
                    Uri uri = data.getData();
                    Log.e(TAG, "File Uri: " + uri.toString());
                    // Get the path
                    String path = getPath(uri);
                    Log.e(TAG, "File Path: " + path);
                    // Get the file instance
                    // File file = new File(path);
                    // Initiate the upload
                    String outputPath = getOutputPath(path);
                    String cmd = getLibDir() + "/libffmpeg.so -i " + path
                                 + " -r 24 -c:v libx264 -pix_fmt yuv420p -y "
                                 + "-vf scale=640:-1,pad=640:480:0:60:black " + outputPath;
                    int exitcode = startAndWaitProcess(cmd);
                    if (exitcode == 0) {
                        scanOutputFile(outputPath);
                        return;
                    }
                    // try rotate
                    cmd = getLibDir() + "/libffmpeg.so -i " + path
                                 + " -r 24 -c:v libx264 -pix_fmt yuv420p -y "
                                 + "-vf transpose=1,scale=640:-1,pad=640:480:0:60:black " + outputPath;
                    exitcode = startAndWaitProcess(cmd);
                    if (exitcode == 0) {
                        scanOutputFile(outputPath);
                        return;
                    }
                    cmd = getLibDir() + "/libffmpeg.so -i " + path
                                 + " -r 24 -c:v libx264 -pix_fmt yuv420p -y "
                                 + "-vf scale=640:480 " + outputPath;
                    exitcode = startAndWaitProcess(cmd);
                    if (exitcode == 0) {
                        scanOutputFile(outputPath);
                        return;
                    }
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            myOnActivityResult(requestCode, resultCode, data);
        } catch (Throwable e) {
            Toast.makeText(this, String.format("exception occurs: %s, type: %s", e.getMessage(), e.getClass().getName()), 
                    Toast.LENGTH_LONG).show();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

}
