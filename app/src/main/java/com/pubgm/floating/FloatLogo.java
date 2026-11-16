package com.pubgm.floating;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Java-only purple/cyan floating layout based on the Elite Tool visual reference.
 * Purely visual update: existing toggle preference keys, JNI IDs and behavior
 * are retained. FPS selector and performance meter are intentionally removed.
 */
public class FloatLogo extends Service {
    private static final int PURPLE = 0xffa02dff;
    private static final int VIOLET = 0xffcb6bff;
    private static final int BLUE = 0xff329bff;
    private static final int CYAN = 0xff24e5ff;
    private static final int WHITE = 0xfff4f4ff;
    private static final int MUTED = 0xffa6b2d4;
    private static final int BORDER = 0xff2d3b77;
    private static final int DARK = 0xff080d22;
    private static final int SURFACE = 0xff0b1430;
    private static final int ROW = 0xff101b37;

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private FrameLayout floatingRoot;
    private LinearLayout panel, sidebar, itemContainer;
    private ScrollView optionsScroll, sidebarScroll;
    private int tabHeightDp = 45;
    private View bubble;
    private SharedPreferences prefs;
    private View[] categoryRows = new View[4];
    private boolean espOverlayRunning = false;
    private boolean libraryReady = false;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout vertical() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout horizontal() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout.LayoutParams ll(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private FrameLayout.LayoutParams fl(int w, int h) { return new FrameLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private GradientDrawable bg(int color, int outline, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (outline != 0) d.setStroke(dp(1), outline); return d;
    }
    private GradientDrawable gradient(int a, int b, int stroke, int r) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {a, b});
        d.setCornerRadius(dp(r)); if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size); t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setIncludeFontPadding(false); return t;
    }
    private void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l), dp(t), dp(r), dp(b)); }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("espValue", Context.MODE_PRIVATE);
        try { System.loadLibrary("client"); libraryReady = true; } catch (Throwable ignored) { libraryReady = false; }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { stopSelf(); return; }
        try { installWindow(); restoreNativeState(); } catch (Exception e) { android.util.Log.e("FloatLogo", "Floating UI failed", e); stopSelf(); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (Build.VERSION.SDK_INT >= 26) {
            final String channel = "floating_ui_neon";
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(channel, "Floating Menu", NotificationManager.IMPORTANCE_LOW));
            Notification n = new Notification.Builder(this, channel)
                .setSmallIcon(com.pubgm.R.drawable.ic_logo)
                .setContentTitle("Elite Tool Floating Menu")
                .setOngoing(true).build();
            startForeground(2, n);
        }
        return START_STICKY;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void installWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) throw new IllegalStateException("WindowManager unavailable");
        floatingRoot = new FrameLayout(this);
        floatingRoot.setClipChildren(false); floatingRoot.setClipToPadding(false);
        makePanel(); makeBubble();
        bubble.setVisibility(View.GONE);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        windowParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowParams.x = dp(12); windowParams.y = dp(24);
        windowManager.addView(floatingRoot, windowParams);
    }

    // Use real pixels when calculating the remaining area. The old 235dp minimum
    // was larger than some landscape screens and pushed the bottom of the UI off-screen.
    private int availableWidth() {
        DisplayMetrics dm = new DisplayMetrics(); windowManager.getDefaultDisplay().getMetrics(dm);
        return Math.max(dp(210), Math.min(dp(535), dm.widthPixels - dp(24)));
    }
    private int availableHeight() {
        DisplayMetrics dm = new DisplayMetrics(); windowManager.getDefaultDisplay().getMetrics(dm);
        return Math.max(dp(145), Math.min(dp(373), dm.heightPixels - dp(44)));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void makePanel() {
        // A 2dp purple-to-cyan rim creates the same luminous border treatment
        // as the reference without any XML, PNG or additional dependency.
        panel = vertical();
        panel.setBackground(gradient(PURPLE, CYAN, 0, 16));
        panel.setElevation(dp(12));
        pad(panel, 2, 2, 2, 2);
        floatingRoot.addView(panel, new FrameLayout.LayoutParams(availableWidth(), availableHeight()));

        LinearLayout inside = vertical();
        inside.setBackground(bg(DARK, 0xff3e448d, 14));
        pad(inside, 8, 8, 8, 8);
        panel.addView(inside, ll(-1, -1));

        LinearLayout header = horizontal();
        header.setBackground(gradient(0xff131735, 0xff07172e, 0xff514eb7, 11));
        pad(header, 9, 4, 9, 4);
        int headerHeight = availableHeight() < dp(270) ? 49 : 65;
        LinearLayout.LayoutParams hp = ll(-1, headerHeight);
        hp.bottomMargin = dp(7);
        inside.addView(header, hp);

        ImageView kdHeaderIcon = new ImageView(this);
        kdHeaderIcon.setImageResource(com.pubgm.R.drawable.ic_elite_symbol);
        kdHeaderIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(kdHeaderIcon, ll(48, 44));
        LinearLayout titleArea = vertical(); titleArea.setGravity(Gravity.CENTER_VERTICAL);
        boolean compact = availableWidth() < dp(425);
        TextView name = text("Elite Tool", compact ? 17 : 24, WHITE, true);
        SpannableString brand = new SpannableString("Elite Tool");
        brand.setSpan(new ForegroundColorSpan(PURPLE), 0, 2, 0);
        brand.setSpan(new ForegroundColorSpan(CYAN), 3, brand.length(), 0);
        name.setText(brand);
        name.setSingleLine(true);
        name.setShadowLayer(dp(4), 0, 0, 0xff542c97);
        titleArea.addView(name, ll(-1, 30));
        TextView subtitle = text("P R E M I U M   T O O L S", 8, 0xffbaacf3, true);
        subtitle.setSingleLine(true);
        titleArea.addView(subtitle, ll(-1, 14));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -1, 1);
        tp.leftMargin = dp(3); header.addView(titleArea, tp);

        TextView close = text("✕", 26, 0xffc5d2fd, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(gradient(0xff22204d, 0xff0e1a36, PURPLE, 11));
        header.addView(close, ll(43, 43));
        close.setOnClickListener(v -> collapse(true));
        // The title area is the drag handle; close remains reliably clickable.
        installDrag(titleArea);

        LinearLayout body = horizontal(); body.setGravity(Gravity.TOP);
        inside.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        int sidebarWidth = availableWidth() <= dp(410) ? 107 : 142;
        tabHeightDp = calculateTabHeight();
        sidebarScroll = new ScrollView(this);
        sidebarScroll.setFillViewport(true);
        sidebarScroll.setVerticalScrollBarEnabled(false);
        sidebarScroll.setBackground(bg(0xff0b1530, BORDER, 10));
        body.addView(sidebarScroll, ll(sidebarWidth, -1));
        sidebar = vertical();
        pad(sidebar, 1, 1, 1, 1);
        sidebarScroll.addView(sidebar, new ScrollView.LayoutParams(-1, -2));

        LinearLayout rightPanel = vertical();
        rightPanel.setBackground(bg(0xff0a1128, BORDER, 10));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1);
        rp.leftMargin = dp(8); body.addView(rightPanel, rp);
        optionsScroll = new ScrollView(this);
        optionsScroll.setFillViewport(false);
        optionsScroll.setVerticalScrollBarEnabled(false);
        optionsScroll.setClipToPadding(false);
        pad(optionsScroll, 9, 10, 9, 8);
        rightPanel.addView(optionsScroll, ll(-1, -1));
        itemContainer = vertical();
        optionsScroll.addView(itemContainer, new ScrollView.LayoutParams(-1, -2));

        addTab(0, "ESP", 0);
        addTab(1, "ITEMS", 4);
        addTab(2, "AIMBOT", 2);
        addTab(3, "SETTING", 3);
        selectTab(0);
    }

    private int calculateTabHeight() {
        // outer padding 16dp + header 59dp + header gap 6dp + safety 10dp
        int bodyDp = Math.round(availableHeight() / getResources().getDisplayMetrics().density) - 100;
        return Math.min(53, Math.max(29, (bodyDp - 4 * 4) / 4));
    }

    private void resizeSidebarTabs() {
        tabHeightDp = calculateTabHeight();
        for (View tab : categoryRows) {
            if (tab == null) continue;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tab.getLayoutParams();
            params.height = dp(tabHeightDp);
            tab.setLayoutParams(params);
        }
    }

    private void addTab(int index, String name, int symbol) {
        LinearLayout tab = horizontal();
        pad(tab, 8, 0, 3, 0);
        LinearLayout.LayoutParams lp = ll(-1, tabHeightDp);
        lp.bottomMargin = dp(4); sidebar.addView(tab, lp);
        categoryRows[index] = tab;
        Glyph icon = new Glyph(this, symbol, (index % 2 == 0) ? VIOLET : CYAN);
        tab.addView(icon, ll(27, 27));
        TextView title = text(name, 12, MUTED, true); title.setSingleLine(true);
        LinearLayout.LayoutParams t = new LinearLayout.LayoutParams(0, -1, 1);
        t.leftMargin = dp(7); tab.addView(title, t);
        tab.setTag(title);
        tab.setOnClickListener(v -> selectTab(index));
    }

    private void selectTab(int index) {
        for (int i = 0; i < categoryRows.length; i++) {
            LinearLayout b = (LinearLayout) categoryRows[i];
            if (b == null) continue;
            boolean active = i == index;
            b.setBackground(gradient(active ? 0xff2a145c : 0xff0d1831,
                    active ? 0xff20113e : 0xff081326, active ? PURPLE : BORDER, 10));
            ((TextView) b.getTag()).setTextColor(active ? WHITE : MUTED);
            b.setElevation(active ? dp(4) : 0);
        }
        itemContainer.removeAllViews(); optionsScroll.scrollTo(0, 0);
        if (index == 0) {
            // No FPS selector or performance widget: requested to remove them.
            addToggle("ESP Active", "EnableEsp", 0, -1);
            addToggle("ESP Box", "Box", 4, 1);
            addToggle("ESP Line", "Line", 5, 2);
            addToggle("ESP Skeleton", "Skeleton", 6, 8);
            addToggle("ESP Name", "Name", 0, 5);
            addToggle("ESP Distance", "Distance", 5, 3);
            addToggle("ESP Health", "Health", 0, 4);
            addToggle("ESP Head Circle", "HeadCircle", 2, 6);
            addToggle("360 Alert", "Alert360", 2, 7);
        } else if (index == 1) {
            addToggle("Vehicles", "Vehicles", 4, 15);
            addToggle("Loot Box", "LootItems", 4, 11);
            addToggle("Grenade", "Grenade", 4, 9);
            addToggle("Weapon", "Weapon", 5, 10);
        } else if (index == 2) {
            addToggle("Aimbot", "Aimbot", 2, -2);
            addTarget();
            addSlider("FOV Size", "fov", 0, 500, 0);
            addSlider("Aimbot Range", "range", 1, 100, 1);
            addSlider("Recoil", "recoil", 0, 100, 2);
        } else {
            addSlider("iPad View", "ipad", 1, 100, 3);
        }
    }

    private void addToggle(String title, String key, int glyph, int nativeCode) {
        LinearLayout row = horizontal();
        row.setBackground(gradient(0xff131b3b, 0xff0e1931, BORDER, 10));
        pad(row, 10, 0, 10, 0);
        int iconColor = (glyph == 4 || glyph == 6) ? VIOLET : CYAN;
        Glyph icon = new Glyph(this, glyph, iconColor);
        row.addView(icon, ll(29, 29));
        TextView label = text(title, 12, WHITE, false);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.leftMargin = dp(9); row.addView(label, lp);
        final NeonSwitch toggle = new NeonSwitch(this, prefs.getBoolean(key, false));
        row.addView(toggle, ll(49, 31));
        View.OnClickListener action = v -> {
            boolean state = !toggle.checked;
            toggle.setChecked(state, true);
            prefs.edit().putBoolean(key, state).apply();
            applySwitchNative(nativeCode, state);
        };
        row.setOnClickListener(action);
        toggle.setOnClickListener(action);
        LinearLayout.LayoutParams p = ll(-1, 49);
        p.bottomMargin = dp(7);
        itemContainer.addView(row, p);
    }

    private void addTarget() {
        TextView caption = text("TARGET", 11, VIOLET, true);
        LinearLayout.LayoutParams c = ll(-1, 24); c.topMargin = dp(5); itemContainer.addView(caption, c);
        RadioGroup group = new RadioGroup(this); group.setOrientation(RadioGroup.HORIZONTAL);
        String[] labels = {"Head", "Chest", "Body"};
        for (int i = 0; i < 3; i++) {
            final int value = i + 1;
            RadioButton button = new RadioButton(this); button.setText(labels[i]); button.setTextSize(10);
            button.setTextColor(WHITE); button.setButtonTintList(ColorStateList.valueOf(PURPLE));
            group.addView(button, ll(-2, 38));
            button.setChecked(prefs.getInt("aim_target", 1) == value);
            button.setOnClickListener(v -> { prefs.edit().putInt("aim_target", value).apply(); if (libraryReady) try { Target(value); } catch (UnsatisfiedLinkError ignored) {} });
        }
        itemContainer.addView(group, ll(-1, 40));
    }

    private void addSlider(String label, String key, int def, int maximum, int nativeAction) {
        LinearLayout holder = vertical(); holder.setBackground(bg(ROW, BORDER, 9));
        pad(holder, 12, 8, 12, 5);
        LinearLayout heading = horizontal();
        TextView title = text(label, 12, WHITE, false); heading.addView(title, new LinearLayout.LayoutParams(0, dp(23), 1));
        TextView value = text("", 12, CYAN, true); heading.addView(value, ll(-2, 23));
        holder.addView(heading);
        SeekBar bar = new SeekBar(this); bar.setMax(maximum);
        int stored = prefs.getInt(key, def); bar.setProgress(stored);
        value.setText(stored + ("recoil".equals(key) ? "%" : ""));
        bar.setProgressTintList(ColorStateList.valueOf(PURPLE));
        bar.setThumbTintList(ColorStateList.valueOf(CYAN));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff25284a));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                value.setText(p + ("recoil".equals(key) ? "%" : ""));
                if (fromUser) { prefs.edit().putInt(key, p).apply(); applySliderNative(nativeAction, p); }
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        holder.addView(bar, ll(-1, 30));
        LinearLayout.LayoutParams hp = ll(-1, 69); hp.bottomMargin = dp(7);
        itemContainer.addView(holder, hp);
    }

    private void applySwitchNative(int command, boolean state) {
        if (command == -1) {
            if (state) drawEsp(); else stopEsp();
            return;
        }
        if (!libraryReady) return;
        try { if (command == -2) ToggleAim(state); else SettingValue(command, state); }
        catch (UnsatisfiedLinkError e) { android.util.Log.w("FloatLogo", "Native toggle missing", e); }
    }
    private void applySliderNative(int action, int value) {
        if (!libraryReady) return;
        try {
            if (action == 0) Range(value);
            else if (action == 1) distances(value);
            else if (action == 2) recoil(value);
            else if (action == 3) WideView(value);
        } catch (UnsatisfiedLinkError e) { android.util.Log.w("FloatLogo", "Native slider missing", e); }
    }
    private void drawEsp() {
        if (espOverlayRunning) return;
        try { startService(new Intent(this, Overlay.class)); espOverlayRunning = true; }
        catch (Exception e) { android.util.Log.w("FloatLogo", "Overlay service failed", e); }
    }
    private void stopEsp() {
        try { stopService(new Intent(this, Overlay.class)); } catch (Exception ignored) {}
        espOverlayRunning = false;
    }
    private void restoreNativeState() {
        if (prefs.getBoolean("EnableEsp", false)) drawEsp();
        String[] keys = {"Box", "Line", "Skeleton", "Name", "Distance", "Health", "HeadCircle", "Alert360", "Vehicles", "LootItems", "Grenade", "Weapon"};
        int[] ids = {1, 2, 8, 5, 3, 4, 6, 7, 15, 11, 9, 10};
        for (int i = 0; i < keys.length; i++) if (prefs.getBoolean(keys[i], false)) applySwitchNative(ids[i], true);
        if (libraryReady) {
            applySwitchNative(-2, prefs.getBoolean("Aimbot", false));
            applySliderNative(0, prefs.getInt("fov", 0));
            applySliderNative(1, prefs.getInt("range", 1));
            applySliderNative(2, prefs.getInt("recoil", 0));
            applySliderNative(3, prefs.getInt("ipad", 1));
            try { Target(prefs.getInt("aim_target", 1)); SettingValue(17, false); }
            catch (UnsatisfiedLinkError ignored) {}
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void makeBubble() {
        ImageView b = new ImageView(this);
        b.setImageResource(com.pubgm.R.drawable.ic_elite_symbol);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setBackground(gradient(0xff48227b, DARK, CYAN, 18));
        b.setPadding(dp(2), dp(2), dp(2), dp(2));
        b.setElevation(dp(9));
        floatingRoot.addView(b, fl(58, 58));
        bubble = b;
        b.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int initialX, initialY; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        initialX = windowParams.x; initialY = windowParams.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - downX) > dp(5) || Math.abs(event.getRawY() - downY) > dp(5)) moved = true;
                        if (moved) moveWindow(initialX + (int)(event.getRawX() - downX), initialY + (int)(event.getRawY() - downY));
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) collapse(false); return true;
                }
                return false;
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void installDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int initialX, initialY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY();
                    initialX = windowParams.x; initialY = windowParams.y; return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    moveWindow(initialX + (int)(e.getRawX()-downX), initialY + (int)(e.getRawY()-downY)); return true;
                }
                return e.getActionMasked() == MotionEvent.ACTION_UP;
            }
        });
    }
    private void moveWindow(int x, int y) {
        if (windowManager == null || windowParams == null || floatingRoot == null) return;
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        int visibleWidth = (panel != null && panel.getVisibility() == View.VISIBLE) ? availableWidth() : dp(58);
        int visibleHeight = (panel != null && panel.getVisibility() == View.VISIBLE) ? availableHeight() : dp(58);
        windowParams.x = Math.max(0, Math.min(x, Math.max(0, dm.widthPixels - visibleWidth)));
        windowParams.y = Math.max(0, Math.min(y, Math.max(0, dm.heightPixels - visibleHeight)));
        try { windowManager.updateViewLayout(floatingRoot, windowParams); } catch (IllegalArgumentException ignored) {}
    }
    private void collapse(boolean small) {
        if (panel == null || bubble == null) return;
        panel.setVisibility(small ? View.GONE : View.VISIBLE);
        bubble.setVisibility(small ? View.VISIBLE : View.GONE);
        if (windowManager != null) {
            moveWindow(windowParams.x, windowParams.y);
        }
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (panel != null) {
            ViewGroup.LayoutParams p = panel.getLayoutParams();
            p.width = availableWidth(); p.height = availableHeight(); panel.setLayoutParams(p);
            resizeSidebarTabs();
            moveWindow(windowParams.x, windowParams.y);
        }
    }
    @Override public void onDestroy() {
        if (windowManager != null && floatingRoot != null) {
            try { windowManager.removeView(floatingRoot); } catch (Exception ignored) {}
        }
        stopEsp();
        if (libraryReady) try { ToggleAim(false); } catch (UnsatisfiedLinkError ignored) {}
        super.onDestroy();
    }

    /** Bright-purple track and white circular thumb from the approved screenshot. */
    private class NeonSwitch extends View {
        boolean checked;
        float position;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ValueAnimator running;
        NeonSwitch(Context c, boolean on) {
            super(c);
            checked = on;
            position = on ? 1f : 0f;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        void setChecked(boolean on, boolean animate) {
            if (checked == on) return;
            checked = on;
            if (running != null) running.cancel();
            if (!animate) { position = on ? 1f : 0f; invalidate(); return; }
            running = ValueAnimator.ofFloat(position, on ? 1f : 0f);
            running.setDuration(190);
            running.addUpdateListener(a -> {
                position = (Float) a.getAnimatedValue(); invalidate();
            });
            running.start();
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight(), cy = h / 2f;
            float l = dp(1), t = dp(3), r = w - dp(1), b = h - dp(3);
            p.clearShadowLayer();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(l, 0, r, 0,
                    position > .1f ? 0xff7925e8 : 0xff2b3156,
                    position > .1f ? 0xffb128fa : 0xff26385a, Shader.TileMode.CLAMP));
            c.drawRoundRect(l, t, r, b, (b - t) / 2f, (b - t) / 2f, p);
            p.setShader(null);
            float thumbR = (b - t) / 2f - dp(1);
            float cx = l + dp(2) + thumbR + (r - l - dp(4) - 2 * thumbR) * position;
            p.setColor(position > .5f ? WHITE : 0xff8193b7);
            if (position > .1f) p.setShadowLayer(dp(3), 0, 0, 0xff8836ee);
            c.drawCircle(cx, cy, thumbR, p);
            p.clearShadowLayer();
        }
        @Override protected void onDetachedFromWindow() {
            if (running != null) running.cancel();
            super.onDetachedFromWindow();
        }
    }

    /** Vector icons beside EVERY toggle; cyan/purple, no XML or image assets. */
    private class Glyph extends View {
        private final int type;
        private final int color;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(Context c, int type, int color) {
            super(c); this.type = type; this.color = color;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth()/2f, cy = getHeight()/2f;
            float r = Math.min(getWidth(), getHeight()) * .34f;
            p.setShader(null);
            p.setColor(color); p.setStrokeWidth(dp(1.8f));
            p.setStyle(Paint.Style.STROKE); p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            if (type == 0) { // eye
                Path eye = new Path();
                eye.moveTo(cx-r*1.15f, cy);
                eye.quadTo(cx,cy-r*.95f,cx+r*1.15f,cy);
                eye.quadTo(cx,cy+r*.95f,cx-r*1.15f,cy);
                canvas.drawPath(eye,p);
                canvas.drawCircle(cx,cy,r*.35f,p);
            } else if (type == 1) { // calendar
                canvas.drawRoundRect(cx-r*.8f,cy-r*.65f,cx+r*.8f,cy+r*.85f,dp(2),dp(2),p);
                canvas.drawLine(cx-r*.47f,cy-r*.9f,cx-r*.47f,cy-r*.47f,p);
                canvas.drawLine(cx+r*.47f,cy-r*.9f,cx+r*.47f,cy-r*.47f,p);
                canvas.drawLine(cx-r*.8f,cy-r*.16f,cx+r*.8f,cy-r*.16f,p);
            } else if (type == 2) { // crosshair
                canvas.drawCircle(cx,cy,r*.72f,p);
                canvas.drawLine(cx-r*1.15f,cy,cx-r*.38f,cy,p);
                canvas.drawLine(cx+r*.38f,cy,cx+r*1.15f,cy,p);
                canvas.drawLine(cx,cy-r*1.15f,cx,cy-r*.38f,p);
                canvas.drawLine(cx,cy+r*.38f,cx,cy+r*1.15f,p);
            } else if (type == 3) { // gear
                canvas.drawCircle(cx,cy,r*.65f,p);
                canvas.drawCircle(cx,cy,r*.27f,p);
                for (int i=0; i<8; i++) {
                    double a=i*Math.PI/4;
                    canvas.drawLine(cx+(float)Math.cos(a)*r*.73f,cy+(float)Math.sin(a)*r*.73f,
                        cx+(float)Math.cos(a)*r*1.14f,cy+(float)Math.sin(a)*r*1.14f,p);
                }
            } else if (type == 4) { // isometric cube
                Path hex = new Path();
                hex.moveTo(cx,cy-r);
                hex.lineTo(cx+r*.85f,cy-r*.48f);
                hex.lineTo(cx+r*.85f,cy+r*.49f);
                hex.lineTo(cx,cy+r);
                hex.lineTo(cx-r*.85f,cy+r*.49f);
                hex.lineTo(cx-r*.85f,cy-r*.48f);
                hex.close(); canvas.drawPath(hex,p);
                canvas.drawLine(cx-r*.85f,cy-r*.48f,cx,cy,p);
                canvas.drawLine(cx+r*.85f,cy-r*.48f,cx,cy,p);
                canvas.drawLine(cx,cy,cx,cy+r,p);
            } else if (type == 5) { // slanted ESP line
                p.setStrokeWidth(dp(2.8f));
                canvas.drawLine(cx-r*.87f,cy+r*.83f,cx+r*.88f,cy-r*.82f,p);
            } else if (type == 6) { // skeleton / figure
                canvas.drawCircle(cx,cy-r*.77f,r*.22f,p);
                canvas.drawLine(cx,cy-r*.47f,cx,cy+r*.37f,p);
                canvas.drawLine(cx-r*.54f,cy-r*.12f,cx+r*.54f,cy-r*.12f,p);
                canvas.drawLine(cx-r*.54f,cy-r*.12f,cx-r*.7f,cy+r*.21f,p);
                canvas.drawLine(cx+r*.54f,cy-r*.12f,cx+r*.7f,cy+r*.21f,p);
                canvas.drawLine(cx,cy+r*.37f,cx-r*.47f,cy+r*1.12f,p);
                canvas.drawLine(cx,cy+r*.37f,cx+r*.47f,cy+r*1.12f,p);
            }
        }
    }

    /** Gradient triangle mark in the header, sized to match the design. */
    private class NeonMark extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        NeonMark(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            Path body = new Path();
            body.moveTo(w*.17f,h*.08f);
            body.lineTo(w*.92f,h*.50f);
            body.lineTo(w*.14f,h*.93f);
            body.lineTo(w*.31f,h*.51f);
            body.close();
            paint.setShader(new LinearGradient(0,0,w,h,PURPLE,CYAN,Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.FILL);
            c.drawPath(body,paint);
            paint.setShader(null);
            Path cut = new Path();
            cut.moveTo(w*.35f,h*.35f);
            cut.lineTo(w*.65f,h*.50f);
            cut.lineTo(w*.35f,h*.66f);
            cut.close();
            paint.setColor(DARK);
            c.drawPath(cut,paint);
        }
    }

    // Names and signatures retained for the native JNI library in the original source.
    public native void SettingValue(int code, boolean value);
    public native void Range(int range);
    public native void distances(int distance);
    public native void recoil(int recoil);
    public native void WideView(int view);
    public native void Target(int target);
    public native void ToggleAim(boolean value);
}
