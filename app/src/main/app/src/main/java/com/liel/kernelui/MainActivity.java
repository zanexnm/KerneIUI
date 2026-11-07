package com.liel.kernelui;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.io.File;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 300, 50, 50);
        
        Button btn = new Button(this);
        btn.setText("一键启动内核灵动岛");
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), 1001);
                } else {
                    startAll();
                }
            }
        });
        layout.addView(btn);
        setContentView(layout);
    }

    private void startAll() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    File f = new File(getFilesDir().getAbsolutePath() + "/native_daemon");
                    if (f.exists()) {
                        Runtime.getRuntime().exec(new String[]{"su", "-c", "chmod 755 " + f.getAbsolutePath() + " && nohup " + f.getAbsolutePath() + " > /dev/null 2>&1 &"});
                    }
                } catch (Exception e) {}
            }
        }).start();

        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1001 && Settings.canDrawOverlays(this)) startAll();
    }
}

class OverlayService extends Service {
    private WindowManager wm;
    private KernelUIView view;
    private boolean isRunning = true;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        String channelId = "overlay_ch";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(channelId, "底层通道", NotificationManager.IMPORTANCE_LOW));
            startForeground(1, new Notification.Builder(this, channelId).setContentTitle("灵动岛运行中").setSmallIcon(android.R.drawable.ic_menu_info_details).build());
        }

        view = new KernelUIView(this, new Runnable() {
            public void run() { view.toggle(); }
        });
        
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        wm.addView(view, lp);

        new Thread(new Runnable() {
            public void run() {
                LocalSocket socket = null;
                try {
                    socket = new LocalSocket();
                    socket.connect(new LocalSocketAddress("/data/local/tmp/kernel_ui.sock", LocalSocketAddress.Namespace.FILESYSTEM));
                    java.io.InputStream is = socket.getInputStream();
                    byte[] buf = new byte[64];
                    while (isRunning) {
                        int r = is.read(buf);
                        if (r > 0) {
                            final String msg = new String(buf, 0, r).trim();
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    if ("COLLAPSE".equals(msg)) view.collapse();
                                    else if ("EXPAND".equals(msg)) view.expand();
                                }
                            });
                        }
                    }
                } catch (Exception e) {}
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (view != null) wm.removeView(view);
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}

class KernelUIView extends FrameLayout {
    private boolean isExpanded = false;
    private LinearLayout islandView, mainPanel;

    public KernelUIView(Context context, final Runnable onClick) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        
        islandView = new LinearLayout(context);
        islandView.setOrientation(LinearLayout.HORIZONTAL);
        islandView.setGravity(Gravity.CENTER);
        GradientDrawable bg1 = new GradientDrawable();
        bg1.setColor(Color.parseColor("#1A1A1A"));
        bg1.setCornerRadius(dp(24));
        islandView.setBackground(bg1);
        
        LayoutParams lp1 = new LayoutParams(dp(130), dp(36));
        lp1.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp1.topMargin = dp(40);
        islandView.setLayoutParams(lp1);

        View dot = new View(context);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotLp.setMarginEnd(dp(8));
        dot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor("#00FF00"));
        dot.setBackground(dotBg);

        TextView tvIsland = new TextView(context);
        tvIsland.setText("Vulkan 60FPS");
        tvIsland.setTextColor(Color.WHITE);
        tvIsland.setTextSize(12f);

        islandView.addView(dot);
        islandView.addView(tvIsland);
        islandView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });

        mainPanel = new LinearLayout(context);
        mainPanel.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams lp2 = new LayoutParams(dp(370), LayoutParams.WRAP_CONTENT);
        lp2.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp2.topMargin = dp(90);
        mainPanel.setLayoutParams(lp2);
        
        LinearLayout left = new LinearLayout(context);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(dp(130), LinearLayout.LayoutParams.WRAP_CONTENT));
        GradientDrawable lbg = new GradientDrawable(); lbg.setColor(Color.parseColor("#F2F2F2")); lbg.setCornerRadius(dp(16)); lbg.setStroke(dp(1), Color.parseColor("#C0C0C0"));
        left.setBackground(lbg);
        left.setPadding(dp(10), dp(15), dp(10), dp(15));
        
        View avatar = new View(context);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(50), dp(50));
        avLp.gravity = Gravity.CENTER_HORIZONTAL;
        avatar.setLayoutParams(avLp);
        GradientDrawable avBg = new GradientDrawable(); avBg.setShape(GradientDrawable.OVAL); avBg.setColor(Color.parseColor("#FFB6C1"));
        avatar.setBackground(avBg);
        left.addView(avatar);

        TextView name = new TextView(context); name.setText("绪山真寻"); name.setTextSize(12f); name.setTextColor(Color.BLACK); name.setGravity(Gravity.CENTER); name.setPadding(0, dp(5), 0, 0);
        left.addView(name);
        String[] tabs = {"主页", "绘制", "瞄准", "设置"};
        for (String tab : tabs) {
            TextView tv = new TextView(context); tv.setText(tab); tv.setTextSize(13f); tv.setGravity(Gravity.CENTER); tv.setPadding(0, dp(12), 0, dp(12)); tv.setTextColor(Color.BLACK);
            left.addView(tv);
        }

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(230), LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMarginStart(dp(10));
        right.setLayoutParams(rlp);
        GradientDrawable rbg = new GradientDrawable(); rbg.setColor(Color.WHITE); rbg.setCornerRadius(dp(16)); rbg.setStroke(dp(1), Color.parseColor("#C0C0C0"));
        right.setBackground(rbg);
        right.setPadding(dp(15), dp(15), dp(15), dp(15));

        String[] tops = {"通用", "功能", "录屏", "调试"};
        LinearLayout nav = new LinearLayout(context);
        for (String t : tops) {
            TextView tv = new TextView(context); tv.setText(t); tv.setTextSize(12f); tv.setGravity(Gravity.CENTER); tv.setPadding(0, dp(6), 0, dp(6)); tv.setTextColor(Color.BLACK);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            nav.addView(tv);
        }
        right.addView(nav);
        
        TextView tvItem = new TextView(context); tvItem.setText("自定义录制秒数 55"); tvItem.setTextSize(11f); tvItem.setTextColor(Color.BLACK); tvItem.setPadding(0, dp(20), 0, dp(10));
        right.addView(tvItem);

        mainPanel.addView(left);
        mainPanel.addView(right);

        mainPanel.setTranslationY(-dp(600));
        mainPanel.setAlpha(0f);
        mainPanel.setVisibility(INVISIBLE);
        
        addView(islandView);
        addView(mainPanel);

        islandView.setOnTouchListener(new OnTouchListener() {
            float sx, sy; int px, py;
            public boolean onTouch(View v, MotionEvent event) {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) KernelUIView.this.getLayoutParams();
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    sx = event.getRawX(); sy = event.getRawY(); px = lp.x; py = lp.y; return false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(event.getRawX() - sx) > 10 || Math.abs(event.getRawY() - sy) > 10) {
                        lp.x = px + (int)(event.getRawX() - sx); lp.y = py + (int)(event.getRawY() - sy);
                        ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE)).updateViewLayout(KernelUIView.this, lp);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    public void expand() {
        if (isExpanded) return; isExpanded = true;
        mainPanel.setVisibility(VISIBLE);
        mainPanel.animate().translationY(0f).alpha(1f).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();
    }

    public void collapse() {
        if (!isExpanded) return; isExpanded = false;
        mainPanel.animate().translationY(-dp(600)).alpha(0f).setDuration(300).setInterpolator(new AccelerateInterpolator()).withEndAction(new Runnable() {
            public void run() { mainPanel.setVisibility(INVISIBLE); }
        }).start();
    }

    public void toggle() { if (isExpanded) collapse(); else expand(); }
}
