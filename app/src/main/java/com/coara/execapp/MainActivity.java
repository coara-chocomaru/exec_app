package com.coara.execapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.database.Cursor;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private Process currentProcess;
    private File selectedBinary;
    private ScheduledExecutorService timeoutExecutor; // タイムアウト用スレッド

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int FILE_PICKER_REQUEST_CODE = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // レイアウト内のビューを取得
        EditText commandInput = findViewById(R.id.command_input);
        Button executeButton = findViewById(R.id.execute_button);
        Button pickBinaryButton = findViewById(R.id.pick_binary_button);
        Button clearBinaryButton = findViewById(R.id.clear_binary_button);
        Button stopButton = findViewById(R.id.stop_button);
        Button keyboardButton = findViewById(R.id.keyboard_button); // キーボード開閉ボタン
        TextView resultView = findViewById(R.id.result_view);

        // 権限確認
        checkPermissions();

        // バイナリ選択ボタン
        pickBinaryButton.setOnClickListener(view -> launchFilePicker());

        // バイナリ解除ボタン
        clearBinaryButton.setOnClickListener(view -> {
            selectedBinary = null;
            Toast.makeText(this, "バイナリが解除されました。", Toast.LENGTH_SHORT).show();
        });

        // コマンド実行ボタン
        executeButton.setOnClickListener(view -> {
            String command = commandInput.getText().toString().trim();
            if (command.isEmpty() && selectedBinary == null) {
                Toast.makeText(this, "コマンドまたはバイナリを指定してください。", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedBinary != null && selectedBinary.exists()) {
                command = selectedBinary.getAbsolutePath() + " " + command;
            }

            executeCommand(command, resultView);
        });

        // 強制停止ボタン
        stopButton.setOnClickListener(view -> {
            if (currentProcess != null && currentProcess.isAlive()) {
                currentProcess.destroy();
                resultView.append("INFO: コマンドが強制終了されました\n");
            } else {
                Toast.makeText(this, "実行中のプロセスはありません。", Toast.LENGTH_SHORT).show();
            }
        });

        // キーボード開閉ボタン
        keyboardButton.setOnClickListener(view -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                if (imm.isAcceptingText()) {
                    imm.hideSoftInputFromWindow(commandInput.getWindowToken(), 0);
                    Toast.makeText(this, "キーボードを閉じました。", Toast.LENGTH_SHORT).show();
                } else {
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                    Toast.makeText(this, "キーボードを開きました。", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void checkPermissions() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, permissions[i] + " 権限が許可されました", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, permissions[i] + " 権限が拒否されました", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                selectedBinary = copyFileToInternalStorage(uri);
                if (selectedBinary != null && selectedBinary.setExecutable(true)) { // 実行権限を付与
                    Toast.makeText(this, "バイナリが選択され、実行権限が付与されました: " + selectedBinary.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "バイナリ選択または実行権限付与に失敗しました。", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private File copyFileToInternalStorage(Uri uri) {
        File directory = new File(getFilesDir(), "binaries");
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(this, "ディレクトリ作成に失敗しました。", Toast.LENGTH_SHORT).show();
            return null;
        }

        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;

            String fileName = getFileName(uri);
            File destFile = new File(directory, fileName);
            try (OutputStream outputStream = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }
            return destFile;
        } catch (IOException e) {
            Toast.makeText(this, "ファイルのコピーに失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void executeCommand(String command, TextView resultView) {
        resultView.setText("");
        try {
            currentProcess = Runtime.getRuntime().exec(command);

            timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
            timeoutExecutor.schedule(() -> {
                if (currentProcess.isAlive()) {
                    currentProcess.destroy();
                    runOnUiThread(() -> resultView.append("INFO: タイムアウトにより強制終了されました\n"));
                }
            }, 30, TimeUnit.SECONDS);

            Executors.newSingleThreadExecutor().submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(currentProcess.getErrorStream()))) {

                    StringBuilder output = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        final String finalLine = line;
                        runOnUiThread(() -> resultView.append(finalLine + "\n"));
                    }

                    while ((line = errorReader.readLine()) != null) {
                        output.append("ERROR: ").append(line).append("\n");
                        final String finalErrorLine = line;
                        runOnUiThread(() -> resultView.append("ERROR: " + finalErrorLine + "\n"));
                    }

                    saveLogToFile(command, output.toString());

                } catch (IOException e) {
                    runOnUiThread(() -> resultView.append("ERROR: " + e.getMessage() + "\n"));
                }
            });

        } catch (IOException e) {
            resultView.setText("ERROR: " + e.getMessage());
        }
    }

    private void saveLogToFile(String command, String logContent) {
        File directory = new File(getExternalFilesDir(null), "command_logs");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = command.replaceAll("[^a-zA-Z0-9]", "_") + "_" + timeStamp + ".txt";
        File logFile = new File(directory, fileName);

        try (FileOutputStream fos = new FileOutputStream(logFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos)) {
            writer.write(logContent);
            runOnUiThread(() -> Toast.makeText(this, "ログが保存されました: " + logFile.getAbsolutePath(), Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "ログ保存中にエラー: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }
}
