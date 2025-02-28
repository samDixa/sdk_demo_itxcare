package com.lztek.api.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.lztek.util.ActivityUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.List;

public class NewMainActivity extends Activity {
    private TextView silentinstallationtxt, navigationtxt, openslidetxt;
    private com.lztek.toolkit.Lztek mLztek;
    private TextView appinfo;

    private void checkNeedPermissions() {
        //6.0以上需要动态申请权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            //多个权限一起申请
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, 1);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ActivityUtil.isScreenOriatationPortrait(this)) {
            setContentView(R.layout.activity_port_main);
        } else {
            setContentView(R.layout.activity_land_main);
        }
        checkNeedPermissions();
        silentinstallationtxt = findViewById(R.id.silentinstallationtxt);
        navigationtxt = findViewById(R.id.navigationtxt);
        openslidetxt = findViewById(R.id.openslidetxt);
        mLztek = com.lztek.toolkit.Lztek.create(this);
        appinfo = findViewById(R.id.appinfo);
        appinfo.setText("V" + ActivityUtil.getVersionName(this));
        initview();

//        View view = this.getWindow().getDecorView();
//        depthTravelView(view);
    }

    public void depthTravelView(View rootView) {
        ArrayDeque queue = new ArrayDeque<>();
        queue.addLast(rootView);
        while (!queue.isEmpty()) {
            View temp = (View) queue.getLast();
            //队尾出队
            queue.pollLast();
            if (temp instanceof ViewGroup) {
                int childCount = ((ViewGroup) temp).getChildCount();
                for (int i = childCount - 1; i >= 0; i--) {
                    queue.addLast(((ViewGroup) temp).getChildAt(i));
                    View view = ((ViewGroup) temp).getChildAt(i);
                    if (view instanceof TextView) {
//                        Log.i("instanceof \t", ((TextView) view).getText().toString());
                        textViewOverFlowed((TextView) view);
                    }
                }
            }

        }
        queue.clear();
    }

    public void textViewOverFlowed(final TextView textView) {
        ViewTreeObserver vto = textView.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressWarnings("deprecation")
            @Override
            public void onGlobalLayout() {
                textView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                textView.getHeight();
                double widthView = textView.getWidth();//控件宽度
                double widthTextStr = textView.getPaint().measureText(textView.getText().toString());//文本宽度
//                Log.i("widthTextStr \t","文本宽度=============="+widthTextStr+"   控件宽度=============="+widthView);
                if (widthTextStr > widthView) {//文本会自动换行
                    //此处显示需要换行时的第2种UI效果
//                    Log.i("widthTextStr \t","换行");
                    textView.setGravity(Gravity.CENTER | Gravity.LEFT);
                } else {//文本无需换行
                    //此处显示无需换行时的第1种UI效果
//                    Log.i("widthTextStr \t","没有换行");
                    textView.setGravity(Gravity.CENTER);
                }
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View gameView = this.getWindow().getDecorView();
        if (gameView != null) {
//            gameView.setSystemUiVisibility(
//                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
//                            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
//                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

    }

    LinearLayout ethernet_demo, density_demo, onclick1,
            onclick2, onclick3, onclick4, onclick5, power_demo,
            serialport_demo, gpio_demo, watchdog_demo, screen_demo,
            hdmiin_demo, screenshot, apk_demo, autoinstall, silentinstallation,
            whitelist, blacklist, onclick6, onclick7, system_demo, ota_demo, navigation,
            openslide, navigationautohidden, startupbroadcast, startbroadcast;

    private void initview() {
        ethernet_demo = findViewById(R.id.ethernet_demo);
        density_demo = findViewById(R.id.density_demo);
        onclick1 = findViewById(R.id.onclick1);
        onclick2 = findViewById(R.id.onclick2);
        onclick3 = findViewById(R.id.onclick3);
        onclick4 = findViewById(R.id.onclick4);
        onclick5 = findViewById(R.id.onclick5);
        power_demo = findViewById(R.id.power_demo);
        serialport_demo = findViewById(R.id.serialport_demo);
        gpio_demo = findViewById(R.id.gpio_demo);
        watchdog_demo = findViewById(R.id.watchdog_demo);
        screen_demo = findViewById(R.id.screen_demo);
        hdmiin_demo = findViewById(R.id.hdmiin_demo);
        screenshot = findViewById(R.id.screenshot);
        apk_demo = findViewById(R.id.apk_demo);
        autoinstall = findViewById(R.id.autoinstall);
        silentinstallation = findViewById(R.id.silentinstallation);
        whitelist = findViewById(R.id.whitelist);
        blacklist = findViewById(R.id.blacklist);
        onclick6 = findViewById(R.id.onclick6);
        onclick7 = findViewById(R.id.onclick7);
        system_demo = findViewById(R.id.system_demo);
        ota_demo = findViewById(R.id.ota_demo);
        navigation = findViewById(R.id.navigation);
        openslide = findViewById(R.id.openslide);
        navigationautohidden = findViewById(R.id.navigationautohidden);
        startupbroadcast = findViewById(R.id.startupbroadcast);
        startbroadcast = findViewById(R.id.startbroadcast);
        LinearLayout linearLayoutonclick[] = {ethernet_demo, density_demo, onclick1,
                onclick2, onclick3, onclick4, onclick5, power_demo,
                serialport_demo, gpio_demo, watchdog_demo, screen_demo,
                hdmiin_demo, screenshot, apk_demo, autoinstall, silentinstallation,
                whitelist, blacklist, onclick6, onclick7, system_demo, ota_demo, navigation,
                openslide, navigationautohidden, startupbroadcast, startbroadcast};
        TextView appName = findViewById(R.id.textappname);
        TextView appinfo = findViewById(R.id.appinfo);

        for (int i = 0; i < linearLayoutonclick.length; i++) {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;
            ViewGroup.LayoutParams params = linearLayoutonclick[i].getLayoutParams();
            boolean isScreenOriatationPortrait = ActivityUtil.isScreenOriatationPortrait(getApplicationContext());
            if (isScreenOriatationPortrait) {
                params.width = (int) (((int) screenHeight / (WIDTH / (250))) - (getResources().getDimension(R.dimen.dp_20)));
                params.height = (int) (((int) screenHeight / (WIDTH / (250))) - (getResources().getDimension(R.dimen.dp_20)));
//                Log.i("onCreate \t", screenWidth + "\t" + screenHeight + "\t" + ((WIDTH / (250))) + "\t" + params.width + "\t" + params.height + "\t" + (isScreenOriatationPortrait ? "竖屏" : "横屏"));
            } else {

                params.width = (int) (((int) screenHeight / (HEIGHT / (250))) - (getResources().getDimension(R.dimen.dp_20) + appName.getTextSize() + appinfo.getTextSize()));
                params.height = (int) (((int) screenHeight / (HEIGHT / (250))) - (getResources().getDimension(R.dimen.dp_20) + appName.getTextSize() + appinfo.getTextSize()));
//                Log.i("onCreate \t", screenWidth + "\t" + screenHeight + "\t" + ((HEIGHT / (250))) + "\t" + params.width + "\t" + params.height + "\t" + (isScreenOriatationPortrait ? "竖屏" : "横屏"));
            }

            linearLayoutonclick[i].setOnClickListener(onClickListener);
            linearLayoutonclick[i].setLayoutParams(params);


        }
    }

    public void startActivity(Class<?> cls) {
        startActivity(new Intent(getApplicationContext(), cls));
    }

    public boolean fileIsExists(String strFile) {
        try {
            File f = new File(strFile);
            if (!f.exists()) {
                return false;
            }

        } catch (Exception e) {
            return false;
        }

        return true;
    }

    int index = 1;
    View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.ethernet_demo:
                    startActivity(EthernetActivity.class);
                    break;
                case R.id.density_demo:
                    startActivity(DensityActivity.class);
                    break;
                case R.id.power_demo:
                    startActivity(PowerActivity.class);
                    break;
                case R.id.serialport_demo:
                    startActivity(SerialPortActivity.class);
                    break;
                case R.id.gpio_demo:
                    startActivity(GPIOActivity.class);
                    break;
                case R.id.watchdog_demo:
                    startActivity(WatchDogActivity.class);
                    break;
                case R.id.screen_demo:
                    startActivity(ScreenActivity.class);
                    break;
                case R.id.hdmiin_demo:
                    if (fileIsExists("/proc/hdmiin")) {
                        startActivity(HdmiInActivity.class);
                    } else {
                        Toast.makeText(getApplicationContext(), getResources().getString(R.string.nofunction), Toast.LENGTH_SHORT).show();
                    }

                    break;
                case R.id.screenshot:
                    try {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mLztek.screenCapture(Environment.getExternalStorageDirectory() + "/" + "screenshot.png");
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getApplicationContext(), Environment.getExternalStorageDirectory() + "/" + "screenshot.png" + "保存成功", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                } catch (Exception e) {
                                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                }


                            }
                        }).start();
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
                    }

                    break;
                case R.id.apk_demo:
                    startActivity(ApkActivity.class);
                    break;
                case R.id.autoinstall:
                    startActivity(AutoInstallActivity.class);
                    break;
                case R.id.silentinstallation:
                    String SDPATH = Environment.getExternalStorageDirectory() + "";
                    copyFilesFromAssets(getApplicationContext(), "apk", SDPATH);
                    if (silentinstallationtxt.getText().toString().equals(getResources().getString(R.string.silentinstallation))) {
                        silentinstallationtxt.setText(getResources().getString(R.string.silentuninstallation));
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i("silentinstallation \t", Environment.getExternalStorageDirectory() + "/" + "via.apk");
                                mLztek.installApplication(Environment.getExternalStorageDirectory() + "/" + "via.apk");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.i("installApplication \t", "安装成功");
                                        Toast.makeText(getApplicationContext(), Environment.getExternalStorageDirectory() + "/" + "via.apk" + "安装成功", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }).start();

                    } else {
                        silentinstallationtxt.setText(getResources().getString(R.string.silentinstallation));
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                mLztek.uninstallApplication("mark.via");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.i("installApplication \t", "卸载成功");
                                        Toast.makeText(getApplicationContext(), Environment.getExternalStorageDirectory() + "/" + "via.apk" + "卸载成功", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }).start();

                    }
                    break;

                case R.id.system_demo:
                    startActivity(SystemActivity.class);
                    break;
                case R.id.ota_demo:
                    startActivity(OtaUpdateActivity.class);
                    break;
                case R.id.navigation:
                    if (navigationtxt.getText().toString().equals(getResources().getString(R.string.shownavigation))) {
                        navigationtxt.setText(getResources().getString(R.string.hidenavigation));
                        mLztek.showNavigationBar();
                    } else {
                        navigationtxt.setText(getResources().getString(R.string.shownavigation));
                        mLztek.hideNavigationBar();
                    }
                    break;
                case R.id.openslide:
                    if (openslidetxt.getText().toString().equals(getResources().getString(R.string.openslide))) {
                        openslidetxt.setText(getResources().getString(R.string.closeslide));
                        mLztek.navigationBarSlideShow(true);
                    } else {
                        openslidetxt.setText(getResources().getString(R.string.openslide));
                        mLztek.navigationBarSlideShow(false);
                    }
                    break;
                case R.id.navigationautohidden:
                    mLztek.navigationBarMaxIdle(1);
                    break;
                case R.id.startupbroadcast:
                    try {
                        if (isAvilible("com.lztek.bootmaster.poweralarm")) {
                            //[功能说明]: 定时开关机设置广播接口（需安装定时开关机 Apk 程序）
                            //每天模式定时开关机,调用示例(每天 08:05 开机、20:30 关机)：
                            Intent intent = new Intent("com.lztek.tools.action.ALARM_DAILY");
                            intent.addFlags(0x01000000);// android 8以上必须
//                            intent.setPackage("com.lztek.bootmaster.poweralarm7");  // android 8以上必须
                            intent.putExtra("onTime", "08:05");
                            intent.putExtra("offTime", "20:30");
                            sendBroadcast(intent);
                            Toast.makeText(getApplicationContext(), "添加成功(每天 08:05 开机、20:30 关机)", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplicationContext(), getResources().getString(R.string.installtips), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
                    }


                    break;
                case R.id.startbroadcast:
                    try {
                        if (isAvilible("com.lztek.bootmaster.autoboot")) {
                            //[功能说明]: 应用启动管理-开机直达应用守护广播接口（需安装应用启动管理 Apk 程序）
                            Intent intent1 = new Intent("com.lztek.tools.action.KEEPALIVE_SETUP");
                            intent1.addFlags(0x01000000);// android 8以上必须
//                    intent1.setPackage("com.lztek.bootmaster.poweralarm7");  // android 8以上必须
                            intent1.putExtra("packageName", getPackageName());
                            intent1.putExtra("delaySeconds", 5); // 应用退出后 5 秒重新启动
                            intent1.putExtra("foreground", false); // 应用可后台运行，进程退出后才重新打开
                            sendBroadcast(intent1);
                            Toast.makeText(getApplicationContext(), "应用守护添加成功(应用退出后 5 秒重新启动)", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplicationContext(), getResources().getString(R.string.installboottips), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
                    }

                    break;
            }
        }
    };

    private boolean isAvilible(String packageName) {
        final PackageManager packageManager = getPackageManager();
        // 获取所有已安装程序的包信息
        List<PackageInfo> pinfo = packageManager.getInstalledPackages(0);
        for (int i = 0; i < pinfo.size(); i++) {
            // 循环判断是否存在指定包名
            if (pinfo.get(i).packageName.contains(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkAppInstalled(Context context, String pkgName) {
        if (TextUtils.isEmpty(pkgName)) {
            return false;
        }
        try {
            context.getPackageManager().getPackageInfo(pkgName, 0);
        } catch (Exception x) {
            return false;
        }
        return true;
    }

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

    private final int WIDTH = 1920;
    private final int HEIGHT = 1080;
    private String poweralarm51 = "com.lztek.bootmaster.poweralarm";
    private String poweralarm71 = "com.lztek.bootmaster.poweralarm7";

    public boolean isAppInstalled(Context context, String packageName) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            packageInfo = null;
            e.printStackTrace();
        }
        return packageInfo != null;
    }
}