package com.dsh.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.InetSocketAddress;
import java.net.Socket;

public class MainActivity extends Activity {

    // ---------- 简洁纯色主题 ----------
    private static final int C_BLUE = Color.rgb(77, 107, 254);       // 主蓝 #4D6BFE
    private static final int C_BLUE_TEXT = Color.rgb(37, 99, 235);   // 蓝字 #2563EB
    private static final int C_BLUE_BG = Color.rgb(219, 234, 254);   // 浅蓝底 #DBEAFE
    private static final int C_RED = Color.rgb(220, 38, 38);         // 红字 #DC2626
    private static final int C_RED_BG = Color.rgb(254, 226, 226);    // 浅红底 #FEE2E2
    private static final int C_GREEN = Color.rgb(16, 185, 129);      // 绿 #10B981
    private static final int C_INK = Color.rgb(17, 24, 39);          // 主文字 #111827
    private static final int C_GRAY = Color.rgb(107, 114, 128);      // 次级文字 #6B7280
    private static final int C_LIGHT = Color.rgb(156, 163, 175);     // 弱文字 #9CA3AF
    private static final int C_DIVIDER = Color.rgb(243, 244, 246);   // 分割线 #F3F4F6
    private static final int C_WHITE = Color.WHITE;

    private static final String TERMUX_PKG = "com.termux";
    private static final String RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    private static final String RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    private static final String START_SCRIPT = "/data/data/com.termux/files/home/bin/start-dsh.sh";
    private static final String STOP_SCRIPT = "/data/data/com.termux/files/home/bin/stop-dsh.sh";
    private static final int REQ_RUN_COMMAND = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int commandAttempts = 0;
    private boolean stopPending = false;
    private boolean dialogShown = false;
    private TextView termuxStatus;
    private TextView permStatus;
    private TextView webStatus;
    private TextView actionNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        makeLightStatusBar();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void makeLightStatusBar() {
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().setStatusBarColor(C_WHITE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    // ---------- UI ----------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_WHITE);
        root.setPadding(dp(20), dp(24), dp(20), dp(210));

        // 标题
        TextView title = new TextView(this);
        title.setText("DSH 启动器");
        title.setTextColor(C_INK);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("DeepSeek Harness 控制台   --provided by Justin Jee");
        subtitle.setTextColor(C_GRAY);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle);

        // 状态区（白底列表 + 分割线）
        LinearLayout statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        statusBox.setBackgroundColor(C_WHITE);
        root.addView(statusBox, matchWrap());

        termuxStatus = makeStatusLine(statusBox, "Termux 环境", "检测中…");
        addDivider(statusBox);
        permStatus = makeStatusLine(statusBox, "Termux 命令权限", "检测中…");
        addDivider(statusBox);
        webStatus = makeStatusLine(statusBox, "DSH Web 服务", "检测中…");

        root.addView(spacer(dp(24)));

        // 启动（蓝底白字）
        Button startButton = makeSolidButton("启动 / 重启 DSH Web", C_BLUE, C_WHITE);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartPressed();
            }
        });
        root.addView(startButton, matchWrap());

        root.addView(spacer(dp(12)));

        // 停止（浅红底红字）
        Button stopButton = makeSolidButton("停止 DSH Web", C_RED_BG, C_RED);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStopPressed();
            }
        });
        root.addView(stopButton, matchWrap());

        root.addView(spacer(dp(12)));

        // 桌面（浅蓝底蓝字）
        Button desktopButton = makeSolidButton("以桌面网页打开", C_BLUE_BG, C_BLUE_TEXT);
        desktopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDsWeb(true);
            }
        });
        root.addView(desktopButton, matchWrap());

        root.addView(spacer(dp(12)));

        // 手机（浅蓝底蓝字）
        Button mobileButton = makeSolidButton("以手机网页打开", C_BLUE_BG, C_BLUE_TEXT);
        mobileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDsWeb(false);
            }
        });
        root.addView(mobileButton, matchWrap());

        root.addView(spacer(dp(20)));

        actionNote = new TextView(this);
        actionNote.setTextColor(C_LIGHT);
        actionNote.setTextSize(12);
        actionNote.setLineSpacing(dp(2), 1.25f);
        actionNote.setGravity(Gravity.CENTER);
        actionNote.setText("提示：首次使用请先点击上方「启动 DSH Web」并授予 Termux 命令权限。");
        root.addView(actionNote);

        // 外层 FrameLayout + 右下角装饰图（尺寸适中，贴右下边）
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(C_WHITE);
        frame.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView corner = new ImageView(this);
        corner.setImageResource(R.drawable.corner_img);
        corner.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // 原图 1280x1138，按比例：宽 200dp -> 高 178dp，紧贴右侧与下侧边框
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(200), dp(178));
        clp.gravity = Gravity.BOTTOM | Gravity.END;
        clp.setMargins(0, 0, 0, 0);
        frame.addView(corner, clp);

        setContentView(frame);
    }

    private TextView makeStatusLine(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(C_GRAY);
        l.setTextSize(15);
        row.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(C_GRAY);
        v.setTextSize(15);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.END);
        row.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        parent.addView(row);
        return v;
    }

    private void addDivider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(C_DIVIDER);
        parent.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    // 纯色按钮：圆角 + 点击变暗反馈
    private Button makeSolidButton(String text, int bgColor, int textColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(52));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(14));
        // 按压反馈：混合黑色变暗
        int pressed = blendDark(bgColor, 0.12f);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(pressed), bg, null);
        b.setBackground(ripple);
        return b;
    }

    private int blendDark(int color, float ratio) {
        int r = (int) (Color.red(color) * (1 - ratio));
        int g = (int) (Color.green(color) * (1 - ratio));
        int bl = (int) (Color.blue(color) * (1 - ratio));
        return Color.rgb(r, g, bl);
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, h));
        return v;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    // ---------- 逻辑 ----------

    private void onStartPressed() {
        if (!isTermuxInstalled()) {
            showTermuxGuide();
            return;
        }
        if (!hasRunCommandPermission()) {
            requestPermissions(new String[]{RUN_COMMAND_PERMISSION}, REQ_RUN_COMMAND);
            return;
        }
        doLaunch();
    }

    private void onStopPressed() {
        if (!isTermuxInstalled()) {
            showTermuxGuide();
            return;
        }
        if (!hasRunCommandPermission()) {
            requestPermissions(new String[]{RUN_COMMAND_PERMISSION}, REQ_RUN_COMMAND);
            return;
        }
        stopPending = true;
        sendStopCommand();
        actionNote.setText("正在停止 DSH Web 并清理 Termux…");
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(stopCheckRunnable, 3000);
        handler.postDelayed(stopCheckRunnable, 6000);
        handler.postDelayed(stopCheckRunnable, 9000);
    }

    private final Runnable stopCheckRunnable = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
        }
    };

    private void doLaunch() {
        commandAttempts = 0;
        stopPending = false;
        dialogShown = false;
        sendCommand();
        actionNote.setText("已发送启动命令，正在等待 dsh web 就绪…");
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(checkRetryRunnable, 2000);
    }

    private void sendCommand() {
        try {
            Intent i = new Intent(ACTION_RUN_COMMAND);
            i.setClassName(TERMUX_PKG, RUN_COMMAND_SERVICE);
            i.putExtra("com.termux.RUN_COMMAND_PATH", START_SCRIPT);
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            startService(i);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Intent b = new Intent(ACTION_RUN_COMMAND);
            b.setPackage(TERMUX_PKG);
            b.putExtra("com.termux.RUN_COMMAND_PATH", START_SCRIPT);
            b.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            sendBroadcast(b);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendStopCommand() {
        try {
            Intent i = new Intent(ACTION_RUN_COMMAND);
            i.setClassName(TERMUX_PKG, RUN_COMMAND_SERVICE);
            i.putExtra("com.termux.RUN_COMMAND_PATH", STOP_SCRIPT);
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            startService(i);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Intent b = new Intent(ACTION_RUN_COMMAND);
            b.setPackage(TERMUX_PKG);
            b.putExtra("com.termux.RUN_COMMAND_PATH", STOP_SCRIPT);
            b.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            sendBroadcast(b);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 启动结果检测：单一链，每 3 秒一次，最多 20 次（约 60 秒）
    // 注意：Socket 检测必须在子线程执行（主线程网络操作会抛 NetworkOnMainThreadException）
    private final Runnable checkRetryRunnable = new Runnable() {
        @Override
        public void run() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final boolean up = isWebUp();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (up) {
                                refreshStatus();
                                return;
                            }
                            commandAttempts++;
                            if (commandAttempts >= 20) {
                                if (!dialogShown) {
                                    dialogShown = true;
                                    actionNote.setText("DSH Web 启动较慢，仍在后台等待…");
                                    showWakeDialog();
                                }
                                handler.postDelayed(checkRetryRunnable, 5000);
                                return;
                            }
                            if (commandAttempts == 5 || commandAttempts == 11) {
                                sendCommand();
                            }
                            actionNote.setText("正在启动 DSH Web…（检测 " + commandAttempts + "/20）");
                            handler.postDelayed(checkRetryRunnable, 3000);
                        }
                    });
                }
            }).start();
        }
    };

    private void showWakeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("启动未成功")
                .setMessage("DSH Web 启动较慢或未成功。\n若稍后状态自动变为「运行中」可忽略本提示。\n若长时间未启动，可打开 Termux 查看 ~/.dsh/dsh-web.log。\n\n是否现在打开 Termux 检查？")
                .setPositiveButton("打开 Termux", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        openTermux();
                    }
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    private void openTermux() {
        try {
            Intent t = new Intent();
            t.setClassName(TERMUX_PKG, "com.termux.app.TermuxActivity");
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(t);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshStatus() {
        if (termuxStatus == null) return;
        termuxStatus.setText(isTermuxInstalled() ? "✓ 已安装 (v" + getTermuxVersion() + ")" : "✗ 未安装");
        permStatus.setText(hasRunCommandPermission() ? "✓ 已授权" : "✗ 未授权");
        webStatus.setText("检测中…");
        webStatus.setTextColor(C_LIGHT);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean up = isWebUp();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (up) {
                            webStatus.setText("✓ 运行中 (127.0.0.1:3080)");
                            webStatus.setTextColor(C_GREEN);
                            actionNote.setText("✓ DSH Web 运行中，可点击下方按钮打开网页");
                            stopPending = false;
                        } else {
                            webStatus.setText("○ 未运行");
                            webStatus.setTextColor(C_LIGHT);
                            if (stopPending) {
                                actionNote.setText("✓ DSH Web 已停止");
                                stopPending = false;
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private boolean isWebUp() {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", 3080), 1500);
            s.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTermuxInstalled() {
        try {
            getPackageManager().getPackageInfo(TERMUX_PKG, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getTermuxVersion() {
        try {
            return getPackageManager().getPackageInfo(TERMUX_PKG, 0).versionName;
        } catch (Exception e) {
            return "未知";
        }
    }

    private boolean hasRunCommandPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    private void openDsWeb(boolean desktop) {
        Intent i = new Intent(this, DsWebActivity.class);
        i.putExtra("desktop", desktop);
        startActivity(i);
    }

    private void showTermuxGuide() {
        new AlertDialog.Builder(this)
                .setTitle("未检测到 Termux")
                .setMessage("DSH 启动器需要 Termux 环境。请先安装 Termux 并配置好 DSH，再回到本应用。")
                .setPositiveButton("打开 F-Droid", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://f-droid.org/packages/com.termux/")));
                    }
                })
                .setNegativeButton("知道了", null)
                .show();
    }

    private void showPermissionGuide() {
        new AlertDialog.Builder(this)
                .setTitle("需要授权 Termux 命令权限")
                .setMessage("请在系统设置中授予「运行 Termux 命令」权限（可能位于「附加权限」中），授予后返回本应用再点「启动 DSH Web」。\n\n若设置中找不到该权限，请在 Termux 终端执行 termux-reload-settings 后重试。")
                .setPositiveButton("去系统设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        openAppSettings();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openAppSettings() {
        try {
            Intent s = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
