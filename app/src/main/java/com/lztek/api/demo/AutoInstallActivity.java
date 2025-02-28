package com.lztek.api.demo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class AutoInstallActivity extends Activity {
    public static void copyFilesFromAssets(Context context, String assetsPath, String savePath) {
        try {
            String fileNames[] = context.getAssets().list(assetsPath);// 获取assets目录下的所有文件及目录名
            if (fileNames.length > 0) {// 如果是目录
                File file = new File(savePath);
                file.mkdirs();// 如果文件夹不存在，则递归
                for (String fileName : fileNames) {
                    copyFilesFromAssets(context, assetsPath + "/" + fileName,
                            savePath + "/" + fileName);
                }
            } else {// 如果是文件
                InputStream is = context.getAssets().open(assetsPath);
                FileOutputStream fos = new FileOutputStream(new File(savePath));
                byte[] buffer = new byte[1024];
                int byteCount = 0;
                while ((byteCount = is.read(buffer)) != -1) {// 循环从输入流读取
                    // buffer字节
                    fos.write(buffer, 0, byteCount);// 将读取的输入流写入到输出流
                }
                fos.flush();// 刷新缓冲区
                is.close();
                fos.close();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    String SDPATH = Environment.getExternalStorageDirectory() + "/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_install);
        setTitle(R.string.autoinstall);
        AutoInstall();
    }


    /*
    1:provider配置示例：
            <provider
            android:name="android.support.v4.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_provider_paths" />
        </provider>


        2:res/xml中对应的文件访问配置file_provider_paths.xml示例：

                    <?xml version="1.0" encoding="utf-8"?>
        <paths xmlns:android="http://schemas.android.com/apk/res/android">
            <cache-path name="cache" path="/" />
            <external-path name="sdcard" path="/" />
        </paths>


        3:  添加权限:<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
            配置：android:requestLegacyExternalStorage="true"
     */

    public void AutoInstall() {
        //自动安装后重新打开
        copyFilesFromAssets(this, "apk", SDPATH);
        java.io.File file = new java.io.File(SDPATH + "via.apk"); //apk文件
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            String authority = "com.lztek.api.demo.fileprovider"; // 与AndroidManifest.xml中的authorities配置一致
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            android.net.Uri uri = FileProvider.getUriForFile(this, authority, file);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            // grantUriPermission(getApplicationContext(), uri, intent); // 如果需要新任务（Intent.FLAG_ACTIVITY_NEW_TASK），需加uri授权处理
        } else {
            android.net.Uri uri = android.net.Uri.fromFile(file);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
        }
        intent.putExtra("IMPLUS_INSTALL", "SILENT_INSTALL");
        // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 如果需要新任务
        startActivity(intent);
        finish();
    }
}