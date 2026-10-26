package com.dsh.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class DsWebActivity extends Activity {

    private static final String DSH_URL = "http://127.0.0.1:3080/";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String DESKTOP_VIEWPORT =
            "width=1280, initial-scale=1.0, maximum-scale=10.0, user-scalable=yes";
    private static final int MAX_RETRIES = 20;

    private WebView webView;
    private boolean desktopMode;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private boolean loaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean desktop = getIntent().getBooleanExtra("desktop", false);
        desktopMode = desktop;
        buildUi(desktop);
        goFullscreen();
        startLoading();
    }

    private void buildUi(boolean desktop) {
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        // 桌面比例渲染：wide viewport + overview 自动缩放，支持双指放大
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        if (desktop) {
            s.setUserAgentString(DESKTOP_UA);
        }
        // 默认浅色模式：禁止系统深色模式强制网页变暗
        if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(WebSettings.FORCE_DARK_OFF);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(false);
        }
        webView.setBackgroundColor(Color.WHITE);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (desktopMode) {
                    injectDesktopViewport(view);
                }
                if (url != null && (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost"))) {
                    loaded = true;
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    onLoadFailed();
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          android.os.Message resultMsg) {
                WebView.HitTestResult hr = view.getHitTestResult();
                String url = hr != null ? hr.getExtra() : null;
                if (url != null) {
                    view.loadUrl(url);
                }
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(DsWebActivity.this)
                        .setMessage(message)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                result.confirm();
                            }
                        })
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface d) {
                                try {
                                    result.cancel();
                                } catch (Exception ignored) {
                                }
                            }
                        })
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(DsWebActivity.this)
                        .setMessage(message)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                result.confirm();
                            }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                result.cancel();
                            }
                        })
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface d) {
                                try {
                                    result.cancel();
                                } catch (Exception ignored) {
                                }
                            }
                        })
                        .show();
                return true;
            }
        });
        setContentView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void goFullscreen() {
        // 沉浸式全屏：隐藏系统状态栏与导航栏（API 19+，兼容所有目标设备）
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        // 刘海屏（API 28+）：窗口布局延伸到显示缺口区域，内容真正全屏
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
    }

    // 覆盖页面自带的 viewport meta，强制按 1280px 桌面宽度渲染，
    // 配合 overview 模式自动缩小到屏幕宽度，达到与电脑端一致的比例
    private void injectDesktopViewport(WebView view) {
        try {
            final String js = "(function(){" +
                    "var m=document.querySelector('meta[name=viewport]');" +
                    "var c='" + DESKTOP_VIEWPORT + "';" +
                    "if(m){m.setAttribute('content',c);}" +
                    "else{var n=document.createElement('meta');n.name='viewport';n.content=c;" +
                    "var h=document.head||document.documentElement;h.insertBefore(n,h.firstChild);}" +
                    "window.scrollTo(0,0);" +
                    "})();";
            view.evaluateJavascript(js, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startLoading() {
        if (loaded) {
            return;
        }
        webView.loadUrl(DSH_URL);
    }

    private void onLoadFailed() {
        if (loaded) {
            return;
        }
        retryCount++;
        if (retryCount > MAX_RETRIES) {
            return; // 静默停止重试，用户手势返回控制台后可重新打开
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startLoading();
            }
        }, 2000);
    }

    @Override
    public void onBackPressed() {
        // 全面屏手势返回：有历史先回退页面，否则退出回到控制台
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
