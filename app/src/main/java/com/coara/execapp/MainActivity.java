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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity; // 変更
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.database.Cursor;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity { // ActivityからAppCompatActivityに変更

    private Process currentProcess; // 実行中のプロセス
    private File selectedBinary;    // 選択されたバイナリファイル
    private ScheduledExecutorService timeoutExecutor; // タイムアウト用スレッド
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText commandInput = findViewById(R.id.command_input);
        Button executeButton = findViewById(R.id.execute_button);
        Button pickBinaryButton = findViewById(R.id.pick_binary_button);
        Button clearBinaryButton = findViewById(R.id.clear_binary_button);
        Button stopButton = findViewById(R.id.stop_button);
        Button keyboardButton = findViewById(R.id.keyboard_button); // 新規追加
        TextView resultView = findViewById(R.id.result_view);
        ScrollView scrollView = findViewById(R.id.scroll_view);

        resultView.setMovementMethod(new android.text.method.ScrollingMovementMethod());

        checkPermissions();

        // ファイルピッカーのセットアップ
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        selectedBinary = copyFileToInternalStorage(uri);
                        if (selectedBinary != null && setFileExecutable(selectedBinary)) {
                            Toast.makeText(this, "バイナリが選択され、実行権限が付与されました: " + selectedBinary.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "バイナリ選択または実行権限付与に失敗しました。", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

        // バイナリ選択ボタンのリスナー
        pickBinaryButton.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            filePickerLauncher.launch(intent);
        });

        // バイナリ解除ボタンのリスナー
        clearBinaryButton.setOnClickListener(view -> {
            selectedBinary = null;
            Toast.makeText(this, "バイナリが解除されました。", Toast.LENGTH_SHORT).show();
        });

        // コマンド実行ボタンのリスナー
        executeButton.setOnClickListener(view -> {
            String command = commandInput.getText().toString().trim();

            if (command.isEmpty() && selectedBinary == null) {
                Toast.makeText(this, "コマンドまたはバイナリを指定してください。", Toast.LENGTH_SHORT).show();
                return; // コマンドもバイナリもない場合は処理を中断
            }

            if (selectedBinary != null && selectedBinary.exists()) {
                if (!command.startsWith(selectedBinary.getAbsolutePath())) {
                    command = selectedBinary.getAbsolutePath() + " " + command;
                }
            } else {
                Toast.makeText(this, "バイナリが選択されていません。コマンドをそのまま実行します。", Toast.LENGTH_SHORT).show();
            }

            executeCommand(command, resultView);
        });

        // 強制終了ボタンのリスナー
        stopButton.setOnClickListener(view -> {
            if (currentProcess != null && currentProcess.isAlive()) {
                currentProcess.destroy();
                resultView.append("INFO: コマンドが強制終了されました\n");
            } else {
                Toast.makeText(this, "実行中のプロセスはありません。", Toast.LENGTH_SHORT).show();
            }
        });

        // キーボード開閉ボタンのリスナー (新規追加)
        keyboardButton.setOnClickListener(view -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                commandInput.requestFocus();
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
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

    private boolean setFileExecutable(File file) {
        return file.setExecutable(true, false);
    }

    private File copyFileToInternalStorage(Uri uri) {
        File directory = new File(getFilesDir(), "binaries");
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Toast.makeText(this, "ディレクトリ作成に失敗しました。", Toast.LENGTH_SHORT).show();
                return null;
            }
        }

        File destFile = null;
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(
                     new File(directory, getFileName(uri)))) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            destFile = new File(directory, getFileName(uri));
            destFile.setExecutable(true);
        } catch (Exception e) {
            Toast.makeText(this, "バイナリのコピーに失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return destFile;
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
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

            // タイムアウト用スレッドを開始
            timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
            timeoutExecutor.schedule(() -> {
                if (currentProcess.isAlive()) {
                    currentProcess.destroy();
                    runOnUiThread(() -> resultView.append("INFO: コマンドがタイムアウトにより強制終了されました\n"));
                }
            }, 30, TimeUnit.SECONDS); // 30秒でタイムアウト

            Executors.newSingleThreadExecutor().submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(currentProcess.getErrorStream()))) {
                    StringBuilder output = new StringBuilder();

                    processStream(reader, resultView, output, false);
                    processStream(errorReader, resultView, output, true);
                    saveLogToFile(command, output.toString());
                } catch (IOException e) {
                    runOnUiThread(() -> resultView.append("ERROR: " + e.getMessage() + "\n"));
                }
            });

        } catch (IOException e) {
            runOnUiThread(() -> resultView.append("ERROR: " + e.getMessage() + "\n"));
        }
    }

    private void processStream(BufferedReader reader, TextView resultView, StringBuilder output, boolean isError) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            final String displayedLine = isError ? "ERROR: " + line : line;
            output.append(displayedLine).append("\n");
            runOnUiThread(() -> resultView.append(displayedLine + "\n"));
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
