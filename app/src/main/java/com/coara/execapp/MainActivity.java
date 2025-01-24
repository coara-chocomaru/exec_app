package com.coara.grepmd5app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int PICK_FILE_REQUEST = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private File selectedFile;
    private EditText grepInput;
    private TextView resultTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button selectFileButton = findViewById(R.id.selectFileButton);
        Button grepButton = findViewById(R.id.grepButton);
        Button md5Button = findViewById(R.id.md5Button);
        grepInput = findViewById(R.id.grepInput);
        resultTextView = findViewById(R.id.resultTextView);

        
        if (!checkPermissions()) {
            requestPermissions();
        }

        
        selectFileButton.setOnClickListener(v -> openFilePicker());
        grepButton.setOnClickListener(v -> executeGrep());
        md5Button.setOnClickListener(v -> checkMd5());
    }

    
    private boolean checkPermissions() {
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

   
    private void requestPermissions() {
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
    }

    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "ストレージ権限が許可されました。", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "権限が拒否されました。アプリの機能が制限されます。", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openFilePicker() {
        if (checkPermissions()) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_FILE_REQUEST);
        } else {
            Toast.makeText(this, "権限がありません。ファイルを選択できません。", Toast.LENGTH_SHORT).show();
        }
    }

   
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            try {
                Uri selectedFileUri = data.getData();
                String fileName = getFileName(selectedFileUri);

                if (fileName == null) {
                    Toast.makeText(this, "ファイル名が取得できませんでした。", Toast.LENGTH_SHORT).show();
                    return;
                }

                File copiedFile = copyFileToAppStorage(selectedFileUri, fileName);
                selectedFile = copiedFile;
                Toast.makeText(this, "ファイルがコピーされました。", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(this, "ファイルのコピーに失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

   
    private String getFileName(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

  
    private File copyFileToAppStorage(Uri fileUri, String fileName) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(fileUri);
        File copiedFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);

        try (FileOutputStream outputStream = new FileOutputStream(copiedFile)) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        }
        if (inputStream != null) inputStream.close();
        return copiedFile;
    }

  
    private void executeGrep() {
        if (selectedFile == null || grepInput.getText().toString().isEmpty()) {
            resultTextView.setText("ファイルとキーワードを選択してください。");
            return;
        }
        String keyword = grepInput.getText().toString();
        new GrepTask().execute(selectedFile, keyword);
    }

    private class GrepTask extends AsyncTask<Object, Void, String> {
        @Override
        protected String doInBackground(Object... params) {
            File file = (File) params[0];
            String keyword = (String) params[1];
            return grepFile(file, keyword);
        }

        @Override
        protected void onPostExecute(String result) {
            resultTextView.setText(result);
            saveLog("grep_log", result);
        }
    }

    private String grepFile(File file, String keyword) {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            Pattern pattern = Pattern.compile(keyword, Pattern.CASE_INSENSITIVE);
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    result.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            result.append("エラー: ").append(e.getMessage());
        }
        return result.toString();
    }

    private void checkMd5() {
        if (selectedFile == null) {
            resultTextView.setText("ファイルを選択してください。");
            return;
        }
        new Md5Task().execute(selectedFile);
    }

    private class Md5Task extends AsyncTask<File, Void, String> {
        @Override
        protected String doInBackground(File... files) {
            return getMd5Checksum(files[0]);
        }

        @Override
        protected void onPostExecute(String md5) {
            resultTextView.setText("MD5: " + md5);
            saveLog("md5sum_log", md5);
        }
    }

    private String getMd5Checksum(File file) {
        if (!file.exists() || !file.isFile()) {
            return "エラー: ファイルが見つかりません。";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(file);
            byte[] byteArray = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesRead);
            }
            byte[] md5Bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : md5Bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "エラー: " + e.getMessage();
        }
    }

    private void saveLog(String logType, String content) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File logFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), logType + "_" + timestamp + ".txt");

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(content + "\n");
        } catch (IOException e) {
            Log.e("MainActivity", "ログ保存エラー", e);
        }
    }
}
