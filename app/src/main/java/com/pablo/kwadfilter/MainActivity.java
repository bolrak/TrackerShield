package com.pablo.kwadfilter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQ_VPN = 1;

    // Logo palette
    private static final int PAGE   = Color.parseColor("#081324");
    private static final int CARD   = Color.parseColor("#16283C");
    private static final int CARD_ON= Color.parseColor("#1B3A5C");
    private static final int ACCENT = Color.parseColor("#4CB2FF");
    private static final int ACCENT2= Color.parseColor("#2E7FD6");
    private static final int OFFGRAY= Color.parseColor("#24384F");
    private static final int WHITE  = Color.parseColor("#FFFFFF");
    private static final int MUTED  = Color.parseColor("#9BB0C5");
    private static final int GREEN  = Color.parseColor("#57D08A");

    private static final int[] LV_DOT = {GREEN, ACCENT, Color.parseColor("#FF7A6B")};
    private String[] lvName;
    private String[] lvDesc;

    private SharedPreferences prefs;
    private TextView bigBtn, status;
    private LinearLayout[] cards = new LinearLayout[3];
    private TextView[] cardTitle = new TextView[3];

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable round(int fill, int radius, int strokeW, int strokeC) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (strokeW > 0) g.setStroke(dp(strokeW), strokeC);
        return g;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(DnsVpnService.PREFS, MODE_PRIVATE);
        lvName = new String[]{ getString(R.string.level_soft), getString(R.string.level_recommended), getString(R.string.level_aggressive) };
        lvDesc = new String[]{ getString(R.string.level_soft_desc), getString(R.string.level_recommended_desc), getString(R.string.level_aggressive_desc) };

        // Quitar la barra de título y que el fondo llegue hasta arriba
        try { if (getActionBar() != null) getActionBar().hide(); } catch (Exception ignore) {}
        getWindow().setStatusBarColor(PAGE);
        getWindow().setNavigationBarColor(PAGE);
        getWindow().getDecorView().setSystemUiVisibility(0);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PAGE);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(28));
        scroll.addView(root);

        // Logo
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120), dp(120));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        logo.setLayoutParams(lp);
        root.addView(logo);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(10), 0, 0);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText(getString(R.string.tagline));
        sub.setTextColor(MUTED);
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(28));
        root.addView(sub);

        // Big ON/OFF button
        bigBtn = new TextView(this);
        bigBtn.setGravity(Gravity.CENTER);
        bigBtn.setTextColor(WHITE);
        bigBtn.setTextSize(17);
        bigBtn.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        bigBtn.setLayoutParams(bp);
        bigBtn.setClickable(true);
        bigBtn.setOnClickListener(v -> onToggle());
        root.addView(bigBtn);

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(14);
        status.setPadding(0, dp(14), 0, dp(30));
        root.addView(status);

        // Section: level
        root.addView(sectionLabel(getString(R.string.section_level)));
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.bottomMargin = dp(10);
            card.setLayoutParams(cp);
            card.setClickable(true);
            card.setOnClickListener(v -> selectLevel(idx));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            View dot = new View(this);
            LinearLayout.LayoutParams dpp = new LinearLayout.LayoutParams(dp(10), dp(10));
            dpp.rightMargin = dp(10);
            dot.setLayoutParams(dpp);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(LV_DOT[i]);
            dot.setBackground(dg);
            head.addView(dot);

            TextView t = new TextView(this);
            t.setText(lvName[i]);
            t.setTextColor(WHITE);
            t.setTextSize(16);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            head.addView(t);
            cardTitle[i] = t;
            card.addView(head);

            TextView d = new TextView(this);
            d.setText(lvDesc[i]);
            d.setTextColor(MUTED);
            d.setTextSize(13);
            d.setPadding(dp(20), dp(6), 0, 0);
            card.addView(d);

            cards[i] = card;
            root.addView(card);
        }

        // Section: options
        root.addView(sectionLabel(getString(R.string.section_options)));
        LinearLayout notifRow = new LinearLayout(this);
        notifRow.setOrientation(LinearLayout.HORIZONTAL);
        notifRow.setGravity(Gravity.CENTER_VERTICAL);
        notifRow.setPadding(dp(16), dp(12), dp(12), dp(12));
        notifRow.setBackground(round(CARD, 16, 0, 0));
        LinearLayout.LayoutParams nrp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        notifRow.setLayoutParams(nrp);

        LinearLayout txtCol = new LinearLayout(this);
        txtCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txtCol.setLayoutParams(tcp);
        TextView nT = new TextView(this);
        nT.setText(getString(R.string.opt_notif_title));
        nT.setTextColor(WHITE);
        nT.setTextSize(15);
        TextView nD = new TextView(this);
        nD.setText(getString(R.string.opt_notif_desc));
        nD.setTextColor(MUTED);
        nD.setTextSize(12);
        txtCol.addView(nT);
        txtCol.addView(nD);
        notifRow.addView(txtCol);

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean("notif", true));
        sw.setOnCheckedChangeListener((CompoundButton bv, boolean on) -> {
            prefs.edit().putBoolean("notif", on).apply();
            reloadIfRunning();
        });
        notifRow.addView(sw);
        root.addView(notifRow);

        // Footer
        TextView foot = new TextView(this);
        foot.setText(getString(R.string.footer));
        foot.setTextColor(Color.parseColor("#6E839A"));
        foot.setTextSize(12);
        foot.setPadding(dp(4), dp(24), dp(4), 0);
        root.addView(foot);

        setContentView(scroll);
        refresh();
        handleExtras(getIntent());
    }

    // Permite arrancar/parar por intent (p.ej. adb: am start -n .../.MainActivity --ez start true)
    private void handleExtras(Intent it) {
        if (it == null) return;
        if (it.getBooleanExtra("start", false) && !DnsVpnService.RUNNING) {
            if (VpnService.prepare(this) == null) {
                startService(new Intent(this, DnsVpnService.class));
                bigBtn.postDelayed(this::refresh, 600);
            }
        } else if (it.getBooleanExtra("stop", false) && DnsVpnService.RUNNING) {
            Intent i = new Intent(this, DnsVpnService.class);
            i.setAction(DnsVpnService.ACTION_STOP);
            startService(i);
            bigBtn.postDelayed(this::refresh, 600);
        }
    }

    private TextView sectionLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(ACCENT);
        t.setTextSize(12);
        t.setLetterSpacing(0.12f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(6), 0, dp(12));
        return t;
    }

    private void selectLevel(int idx) {
        prefs.edit().putInt("level", idx).apply();
        refresh();
        reloadIfRunning();
    }

    private void reloadIfRunning() {
        if (DnsVpnService.RUNNING) {
            Intent i = new Intent(this, DnsVpnService.class);
            i.setAction(DnsVpnService.ACTION_RELOAD);
            startService(i);
        }
    }

    private void onToggle() {
        if (DnsVpnService.RUNNING) {
            Intent i = new Intent(this, DnsVpnService.class);
            i.setAction(DnsVpnService.ACTION_STOP);
            startService(i);
            bigBtn.postDelayed(this::refresh, 600);
        } else {
            Intent prep = VpnService.prepare(this);
            if (prep != null) startActivityForResult(prep, REQ_VPN);
            else onActivityResult(REQ_VPN, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_VPN && res == RESULT_OK) {
            startService(new Intent(this, DnsVpnService.class));
            bigBtn.postDelayed(this::refresh, 600);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean on = DnsVpnService.RUNNING;
        int level = prefs.getInt("level", 1);

        bigBtn.setText(on ? getString(R.string.btn_disable) : getString(R.string.btn_enable));
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                on ? new int[]{ACCENT, ACCENT2} : new int[]{OFFGRAY, OFFGRAY});
        g.setCornerRadius(dp(16));
        bigBtn.setBackground(g);

        status.setText((on ? "● " : "○ ") + getString(on ? R.string.status_on : R.string.status_off));
        status.setTextColor(on ? GREEN : MUTED);

        for (int i = 0; i < 3; i++) {
            boolean sel = i == level;
            cards[i].setBackground(round(sel ? CARD_ON : CARD, 16, sel ? 2 : 0, ACCENT));
            cardTitle[i].setTextColor(sel ? ACCENT : WHITE);
        }
    }
}
