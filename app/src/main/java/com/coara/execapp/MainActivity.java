package com.coara.execapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
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

public class MainActivity extends AppCompatActivity {

    private Process currentProcess; 
    private File selectedBinary;    
    private ScheduledExecutorService timeoutExecutor; 
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private boolean permissionsGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        
        EditText commandInput = findViewById(R.id.command_input);
        Button executeButton = findViewById(R.id.execute_button);
        Button pickBinaryButton = findViewById(R.id.pick_binary_button);
        Button clearBinaryButton = findViewById(R.id.clear_binary_button);
        Button stopButton = findViewById(R.id.stop_button);
        Button keyboardButton = findViewById(R.id.keyboard_button);
        TextView resultView = findViewById(R.id.result_view);
        ScrollView scrollView = findViewById(R.id.scroll_view);

        // UI要素がnullでないことを確認
        if (commandInput == null || executeButton == null || pickBinaryButton == null || 
            clearBinaryButton == null || stopButton == null || keyboardButton == null || 
            resultView == null || scrollView == null) {
            Toast.makeText(this, "レイアウトに問題があります。", Toast.LENGTH_LONG).show();
            return;
        }

        resultView.setMovementMethod(new android.text.method.ScrollingMovementMethod());

        checkPermissions();

        // ファイルピッカーのセットアップ
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        if (permissionsGranted) {
                            selectedBinary = copyFileToInternalStorage(uri);
                            if (selectedBinary != null && setFileExecutable(selectedBinary)) {
                                Toast.makeText(this, "バイナリが選択され、実行権限が付与されました: " + selectedBinary.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "バイナリ選択または実行権限付与に失敗しました。", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "必要なパーミッションが許可されていません。", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

        // ボタンクリックリスナーの設定
        setupButtonListeners(commandInput, executeButton, pickBinaryButton, clearBinaryButton, stopButton, keyboardButton, resultView);
    }

    private void setupButtonListeners(EditText commandInput, Button executeButton, Button pickBinaryButton, 
                                      Button clearBinaryButton, Button stopButton, Button keyboardButton, TextView resultView) {
        pickBinaryButton.setOnClickListener(view -> {
            if (permissionsGranted) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                filePickerLauncher.launch(intent);
            } else {
                Toast.makeText(this, "必要なパーミッションが許可されていません。", Toast.LENGTH_SHORT).show();
            }
        });

        clearBinaryButton.setOnClickListener(view -> {
            selectedBinary = null;
            Toast.makeText(this, "バイナリが解除されました。", Toast.LENGTH_SHORT).show();
        });

        executeButton.setOnClickListener(view -> {
            if (permissionsGranted) {
                String command = commandInput.getText().toString().trim();
                if (command.isEmpty() && selectedBinary == null) {
                    Toast.makeText(this, "コマンドまたはバイナリを指定してください。", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (selectedBinary != null && selectedBinary.exists()) {
                    command = selectedBinary.getAbsolutePath() + " " + command;
                }
                executeCommand(command, resultView);
            } else {
                Toast.makeText(this, "必要なパーミッションが許可されていません。", Toast.LENGTH_SHORT).show();
            }
        });

        stopButton.setOnClickListener(view -> {
            if (currentProcess != null && currentProcess.isAlive()) {
                currentProcess.destroy();
                resultView.append("INFO: コマンドが強制終了されました\n");
            } else {
                Toast.makeText(this, "実行中のプロセスはありません。", Toast.LENGTH_SHORT).show();
            }
        });

        keyboardButton.setOnClickListener(view -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                commandInput.requestFocus();
            }
        });
    }

    private void checkPermissions() {
        String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        permissionsGranted = true;

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
                break;
            }
        }

        if (!permissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            permissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    permissionsGranted = false;
                    break;
                }
            }
            if (permissionsGranted) {
                Toast.makeText(this, "必要なパーミッションが許可されました。", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "一部のパーミッションが拒否されました。アプリの全機能が利用できない場合があります。", Toast.LENGTH_LONG).show();
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
