package com.hejun.floatingclock;

import android.Manifest;
import android.app.Activity;
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
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_NOTIFICATIONS = 41;

    private BackdropView backdrop;
    private ClockView preview;
    private ToggleSwitch hundredthsSwitch;
    private LinearLayout permissionCard;
    private LinearLayout content;
    private TextView startButton;
    private TextView stopButton;
    private TextView statusChip;
    private TextView kicker;
    private View brandDot;
    private final TextView[] themeChips = new TextView[Prefs.ACCENTS.length];

    private int accent;
    private boolean pendingStart;
    private boolean autoStartInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        accent = Prefs.accent(this);
        buildUi();
        applyAccent(accent, false);
        updatePermissionUi();
        updateRunningUi();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(0xFF090C11);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        backdrop = new BackdropView(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(18), dp(22), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildHeader();
        buildPreview();
        buildPermissionCard();
        buildThemeSection();
        buildDisplaySection();
        buildActions();
        buildFooter();

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                // Deprecated methods still report the system bars correctly and avoid
                // referencing API-30-only classes on Android 8/9.
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                content.setPadding(dp(22), dp(18) + top, dp(22), dp(28) + bottom);
                return insets;
            }
        });
        setContentView(root);
    }

    private void buildHeader() {
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brandDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotLp.rightMargin = dp(8);
        brand.addView(brandDot, dotLp);

        kicker = label("FLOATING CLOCK", 10.5f, accent, Typeface.BOLD);
        kicker.setLetterSpacing(0.2f);
        brand.addView(kicker);

        Space space = new Space(this);
        brand.addView(space, new LinearLayout.LayoutParams(0, 1, 1f));

        statusChip = label("●  悬浮中", 10.5f, accent, Typeface.BOLD);
        statusChip.setGravity(Gravity.CENTER);
        statusChip.setPadding(dp(10), dp(5), dp(10), dp(5));
        statusChip.setLetterSpacing(0.04f);
        statusChip.setVisibility(View.GONE);
        brand.addView(statusChip);
        content.addView(brand);

        TextView title = label("悬浮时钟", 32, 0xFFF4F8FB, Typeface.BOLD);
        title.setLetterSpacing(-0.01f);
        addWithTop(title, dp(13));

        TextView subtitle = label("无广告的极简悬浮时钟，抢票时始终置顶。\n可拖动位置，轻点收起，长按操作。", 13, 0xFF8B96A6,
                Typeface.NORMAL);
        subtitle.setLineSpacing(dp(5), 1f);
        addWithTop(subtitle, dp(9));
    }

    private void buildPreview() {
        int width = Math.min(dp(300), getResources().getDisplayMetrics().widthPixels - dp(50));
        preview = new ClockView(this);
        preview.setHundredths(Prefs.hundredths(this));
        preview.setContentDescription("悬浮时钟实时预览");
        FrameLayout wrap = new FrameLayout(this);
        FrameLayout.LayoutParams previewLp = new FrameLayout.LayoutParams(width, dp(132), Gravity.CENTER);
        wrap.addView(preview, previewLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
        lp.topMargin = dp(20);
        content.addView(wrap, lp);
    }

    private void buildPermissionCard() {
        permissionCard = new LinearLayout(this);
        permissionCard.setOrientation(LinearLayout.HORIZONTAL);
        permissionCard.setGravity(Gravity.CENTER_VERTICAL);
        permissionCard.setPadding(dp(16), dp(13), dp(10), dp(13));
        permissionCard.setBackground(rounded(0x1AFFC46B, dp(18), 0x38FFC46B, dp(1)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("还差一个悬浮权限", 13.5f, 0xFFFFD79A, Typeface.BOLD);
        TextView body = label("允许「显示在其他应用上层」后\n时钟才能盖在购票页面之上", 11, 0xFFB7A27E, Typeface.NORMAL);
        body.setLineSpacing(dp(2), 1f);
        texts.addView(title);
        texts.addView(body);
        permissionCard.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView grant = label("去开启", 12.5f, 0xFF1A1206, Typeface.BOLD);
        grant.setGravity(Gravity.CENTER);
        grant.setPadding(dp(16), dp(9), dp(16), dp(9));
        grant.setBackground(rounded(0xFFFFC46B, dp(999)));
        grant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestOverlayPermission();
            }
        });
        permissionCard.addView(grant);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        content.addView(permissionCard, lp);
    }

    private void buildThemeSection() {
        addSectionTitle("主题色", "选一个顺眼的", dp(22));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < Prefs.ACCENTS.length; i++) {
            final int index = i;
            SpannableString text = new SpannableString("●  " + Prefs.ACCENT_NAMES[i]);
            text.setSpan(new ForegroundColorSpan(Prefs.ACCENTS[i]), 0, 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView chip = label(text, 12, 0xFFAAB4C1, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(10), dp(9), dp(10), dp(9));
            chip.setLetterSpacing(0.02f);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyAccent(Prefs.ACCENTS[index], true);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.leftMargin = dp(6);
            }
            chips.addView(chip, lp);
            themeChips[i] = chip;
        }
        content.addView(chips);
    }

    private void buildDisplaySection() {
        addSectionTitle("显示", "干净，但不多余", dp(24));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(4), dp(16), dp(4));
        card.setBackground(rounded(0x0FFFFFFF, dp(20), 0x1AFFFFFF, dp(1)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label("百分秒", 14, 0xFFF0F4F8, Typeface.BOLD));
        TextView desc = label("额外显示 0.01 秒，抢票更从容", 11, 0xFF7E8998, Typeface.NORMAL);
        desc.setPadding(0, dp(3), 0, 0);
        texts.addView(desc);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        hundredthsSwitch = new ToggleSwitch(this);
        hundredthsSwitch.setChecked(Prefs.hundredths(this), false);
        hundredthsSwitch.setContentDescription("显示百分秒");
        hundredthsSwitch.setOnCheckedChange(new Runnable() {
            @Override
            public void run() {
                boolean value = hundredthsSwitch.isChecked();
                Prefs.setHundredths(MainActivity.this, value);
                preview.setHundredths(value);
            }
        });
        row.addView(hundredthsSwitch, new LinearLayout.LayoutParams(dp(46), dp(28)));
        card.addView(row);

        View divider = new View(this);
        divider.setBackgroundColor(0x14FFFFFF);
        card.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout hintRow = new LinearLayout(this);
        hintRow.setOrientation(LinearLayout.VERTICAL);
        hintRow.setPadding(0, dp(12), 0, dp(13));
        hintRow.addView(label("操作方式", 13, 0xFFD5DCE5, Typeface.BOLD));
        TextView hint = label("拖动移动位置 · 轻点收起 / 展开 · 长按显示操作", 11, 0xFF7E8998,
                Typeface.NORMAL);
        hint.setPadding(0, dp(3), 0, 0);
        hintRow.addView(hint);
        card.addView(hintRow);

        content.addView(card);
    }

    private void buildActions() {
        startButton = label("开启悬浮时钟", 16, 0xFF07120E, Typeface.BOLD);
        startButton.setGravity(Gravity.CENTER);
        startButton.setLetterSpacing(0.02f);
        startButton.setClickable(true);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ClockService.isRunning()) {
                    moveTaskToBack(true);
                } else {
                    beginStartFlow();
                }
            }
        });
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        startLp.topMargin = dp(26);
        content.addView(startButton, startLp);

        stopButton = label("停止悬浮", 13, 0xFF8792A1, Typeface.BOLD);
        stopButton.setGravity(Gravity.CENTER);
        stopButton.setPadding(0, dp(12), 0, dp(12));
        stopButton.setVisibility(View.GONE);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, ClockService.class));
                startButton.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateRunningUi();
                    }
                }, 260);
            }
        });
        content.addView(stopButton);
    }

    private void buildFooter() {
        TextView footer = label("无广告 · 无需联网 · 不收集任何数据", 10.5f, 0xFF5C6775,
                Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(22);
        content.addView(footer, lp);

        TextView sync = label("时间取自系统，请保持手机「自动设置时间」开启", 10, 0xFF434D5A,
                Typeface.NORMAL);
        sync.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams syncLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        syncLp.topMargin = dp(6);
        content.addView(sync, syncLp);
    }

    private void beginStartFlow() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        } else {
            startFloatingClock();
        }
    }

    private void requestOverlayPermission() {
        pendingStart = true;
        Toast.makeText(this, "请在列表中选择「悬浮时钟」并允许", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }
    }

    private void startFloatingClock() {
        if (autoStartInFlight) {
            return;
        }
        autoStartInFlight = true;
        Intent service = new Intent(this, ClockService.class);
        try {
            startForegroundService(service);
            Toast.makeText(this, "已开启，拖动到顺手的位置吧", Toast.LENGTH_SHORT).show();
            startButton.postDelayed(new Runnable() {
                @Override
                public void run() {
                    autoStartInFlight = false;
                    updateRunningUi();
                    moveTaskToBack(true);
                }
            }, 420);
        } catch (Exception e) {
            autoStartInFlight = false;
            Toast.makeText(this, "启动失败，请检查悬浮权限后重试", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            // The clock works even if the user hides its (optional) notification.
            startFloatingClock();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If the permission was revoked while the clock was running, the window may
        // already be gone; stop the orphaned foreground service as well.
        if (!Settings.canDrawOverlays(this) && ClockService.isRunning()) {
            stopService(new Intent(this, ClockService.class));
        }
        updatePermissionUi();
        updateRunningUi();
        if (pendingStart) {
            if (Settings.canDrawOverlays(this)) {
                pendingStart = false;
                beginStartFlow();
            } else {
                pendingStart = false;
            }
        }
    }

    private void updatePermissionUi() {
        if (permissionCard == null) {
            return;
        }
        boolean granted = Settings.canDrawOverlays(this);
        permissionCard.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private void updateRunningUi() {
        boolean running = ClockService.isRunning();
        if (statusChip != null) {
            statusChip.setVisibility(running ? View.VISIBLE : View.GONE);
        }
        if (startButton != null) {
            startButton.setText(running ? "悬浮中 · 返回桌面" : "开启悬浮时钟");
        }
        if (stopButton != null) {
            stopButton.setVisibility(running ? View.VISIBLE : View.GONE);
        }
    }

    private void applyAccent(int color, boolean persist) {
        accent = color;
        if (persist) {
            Prefs.setAccent(this, color);
        }
        if (backdrop != null) {
            backdrop.setAccent(color);
        }
        if (preview != null) {
            preview.setAccent(color);
        }
        if (hundredthsSwitch != null) {
            hundredthsSwitch.setAccent(color);
        }
        if (brandDot != null) {
            brandDot.setBackground(rounded(color, dp(999)));
        }
        if (kicker != null) {
            kicker.setTextColor(color);
        }
        if (statusChip != null) {
            statusChip.setTextColor(color);
            statusChip.setBackground(rounded(ClockView.withAlpha(color, 24), dp(999),
                    ClockView.withAlpha(color, 80), dp(1)));
        }
        if (startButton != null) {
            startButton.setBackground(primaryBackground(color));
            startButton.setTextColor(0xFF06110D);
        }
        updateThemeChips();
    }

    private void updateThemeChips() {
        for (int i = 0; i < themeChips.length; i++) {
            TextView chip = themeChips[i];
            if (chip == null) {
                continue;
            }
            boolean selected = Prefs.ACCENTS[i] == accent;
            if (selected) {
                chip.setBackground(rounded(ClockView.withAlpha(accent, 30), dp(999), accent, dp(1)));
                chip.setTextColor(0xFFF6FAFC);
            } else {
                chip.setBackground(rounded(0x0DFFFFFF, dp(999), 0x1AFFFFFF, dp(1)));
                chip.setTextColor(0xFFAAB4C1);
            }
        }
    }

    private void addSectionTitle(String title, String hint, int topMargin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        TextView main = label(title, 12.5f, 0xFFE9EEF4, Typeface.BOLD);
        main.setLetterSpacing(0.06f);
        row.addView(main);
        TextView sub = label(hint, 10.5f, 0xFF5F6A79, Typeface.NORMAL);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.leftMargin = dp(8);
        row.addView(sub, subLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        lp.bottomMargin = dp(10);
        content.addView(row, lp);
    }

    private void addWithTop(View view, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        content.addView(view, lp);
    }

    private TextView label(CharSequence text, float sizeSp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable rounded(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(999));
        return drawable;
    }

    private GradientDrawable rounded(int fill, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable rounded(int fill, float radius, int strokeColor, float strokeWidth) {
        GradientDrawable drawable = rounded(fill, radius);
        drawable.setStroke(Math.round(strokeWidth), strokeColor);
        return drawable;
    }

    private RippleDrawable primaryBackground(int color) {
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color, ClockView.mix(color, Color.WHITE, 0.28f)});
        fill.setCornerRadius(dp(18));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(18));
        return new RippleDrawable(ColorStateList.valueOf(0x33000000), fill, mask);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "MainActivity(%s)", getPackageName());
    }
}
