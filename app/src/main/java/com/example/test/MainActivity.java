package com.example.test;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.database.Cursor;
import android.provider.OpenableColumns;


import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Process currentProcess; // 実行中のプロセス
    private File selectedBinary;    // 選択されたバイナリファイル

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText commandInput = findViewById(R.id.command_input);
        Button executeButton = findViewById(R.id.execute_button);
        Button pickBinaryButton = findViewById(R.id.pick_binary_button);
        Button clearBinaryButton = findViewById(R.id.clear_binary_button); // バイナリ解除ボタン
        TextView resultView = findViewById(R.id.result_view);
        ScrollView scrollView = findViewById(R.id.scroll_view);

        resultView.setMovementMethod(new android.text.method.ScrollingMovementMethod());

        // ファイルピックアッパーのセットアップ
        ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        selectedBinary = copyFileToInternalStorage(uri);
                        Toast.makeText(
                            this,
                            "バイナリが選択されました: " + selectedBinary.getAbsolutePath(),
                            Toast.LENGTH_SHORT
                        ).show();
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
            selectedBinary = null; // バイナリ選択を解除
            Toast.makeText(
                this,
                "バイナリが解除されました。",
                Toast.LENGTH_SHORT
            ).show();
        });

        // コマンド実行ボタンのリスナー
        executeButton.setOnClickListener(view -> {
            String command = commandInput.getText().toString();

            if (selectedBinary != null && selectedBinary.exists()) {
                // バイナリが選択されている場合、そのパスをコマンドに追加
                if (!command.startsWith(selectedBinary.getAbsolutePath())) {
                    command = selectedBinary.getAbsolutePath() + " " + command;
                }
            } else {
                // バイナリが選択されていない場合は通知を表示
                Toast.makeText(
                    this,
                    "バイナリが選択されていません。コマンドをそのまま実行します。",
                    Toast.LENGTH_SHORT
                ).show();
            }

            executeCommand(command, resultView); // コマンドを実行
        });
    }

    private File copyFileToInternalStorage(Uri uri) {
        File directory = new File(getFilesDir(), "binaries");
        if (!directory.exists()) {
            directory.mkdirs();
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
            destFile.setExecutable(true); // 実行可能フラグを設定
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
        resultView.setText(""); // 出力リセット
        try {
            // currentProcessを実行
            currentProcess = Runtime.getRuntime().exec(command);

            // 非同期タスクの実行
            Executors.newSingleThreadExecutor().submit(() -> {
                try (
                    // try-with-resourcesでリソースを管理
                    BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()));
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(currentProcess.getErrorStream()))
                ) {
                    StringBuilder output = new StringBuilder();

                    // 標準出力の処理
                    processStream(reader, resultView, output, false);

                    // 標準エラーの処理
                    processStream(errorReader, resultView, output, true);

                    // ログファイルに保存
                    saveLogToFile(command, output.toString());

                } catch (IOException e) {
                    // 例外をキャッチしてエラーメッセージをUIスレッドで表示
                    runOnUiThread(() -> resultView.append("ERROR: " + e.getMessage() + "\n"));
                    e.printStackTrace(); // スタックトレースを表示
                }
            });

        } catch (IOException e) {
            // コマンドの実行エラーをキャッチ
            runOnUiThread(() -> resultView.append("ERROR: " + e.getMessage() + "\n"));
            e.printStackTrace();
        }
    }

    private void processStream(BufferedReader reader, TextView resultView, StringBuilder output, boolean isError) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            final String displayedLine = isError ? "ERROR: " + line : line;
            output.append(displayedLine).append("\n");

            // UIスレッドでTextViewを更新
            runOnUiThread(() -> resultView.append(displayedLine + "\n"));
        }
    }

    private void saveLogToFile(String command, String logContent) {
        File directory = new File(getExternalFilesDir(null), "command_logs");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = command.replaceAll("[^a-zA-Z0-9]", "_") + "_" + timeStamp + ".txt";
        File logFile = new File(directory, fileName);

        try (FileOutputStream fos = new FileOutputStream(logFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos)) {
            writer.write(logContent);
        } catch (Exception e) {
            Toast.makeText(this, "ログ保存中にエラー: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
