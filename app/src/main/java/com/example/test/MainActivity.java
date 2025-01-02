package com.example.test;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1;  // リクエストコード
    private int logIndex = 1;  // ログファイルの連番

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText commandInput = findViewById(R.id.command_input);
        Button executeButton = findViewById(R.id.execute_button);
        TextView resultView = findViewById(R.id.result_view);
        ScrollView scrollView = findViewById(R.id.scroll_view);  // ScrollViewのIDを設定

        // TextViewをスクロール可能にするための設定
        resultView.setMovementMethod(new android.text.method.ScrollingMovementMethod());

        // ストレージ権限があるか確認
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // 権限がない場合、リクエスト
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST_CODE);
        }

        executeButton.setOnClickListener(view -> {
            String command = commandInput.getText().toString();
            try {
                Process process = Runtime.getRuntime().exec(command);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");  // 改行を追加
                }

                BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream())
                );
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");  // 改行を追加
                }

                // 結果をTextViewに表示
                resultView.setText(output.toString());

                // ログファイルの保存
                saveLogToFile(output.toString());

            } catch (Exception e) {
                resultView.setText("Error: " + e.getMessage());
                Toast.makeText(this, "エラー: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ストレージ権限のリクエスト結果の処理
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "ストレージ権限が許可されました", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "ストレージ権限が拒否されました", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveLogToFile(String logContent) {
        // /sdcard/command/ フォルダの代わりにアプリ専用のディレクトリに保存
        File directory = new File(getExternalFilesDir(null), "command");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // ログファイルを連番で作成
        File logFile = new File(directory, "log" + logIndex + ".txt");
        try {
            // ログ内容をファイルに書き込む
            FileOutputStream fos = new FileOutputStream(logFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            writer.write(logContent);
            writer.close();

            // ファイル番号をインクリメント
            logIndex++;

            // 保存完了メッセージ
            Toast.makeText(this, "ログが保存されました: " + logFile.getAbsolutePath(), Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "ログ保存中にエラーが発生しました", Toast.LENGTH_LONG).show();
        }
    }
}
