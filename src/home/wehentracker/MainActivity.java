package home.wehentracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Wehentracker: ein grosser Knopf. Tippen = Wehe beginnt, nochmal tippen = Wehe vorbei,
 * dann Staerke waehlen. Zeigt alle Wehen des heutigen Tages. Alles lokal gespeichert.
 *
 * Zustand wird aus den gespeicherten Daten abgeleitet:
 *   runningStart > 0                      -> Wehe laeuft
 *   letzte Wehe hat Staerke -1            -> Staerke wird abgefragt
 *   sonst                                 -> bereit
 */
public class MainActivity extends Activity {

    private static final String PREFS = "wehen";
    private static final String K_RECORDS = "records";
    private static final String K_RUNNING = "running";

    private static final int BG = Color.parseColor("#10151C");
    private static final int TEXT = Color.parseColor("#F2F4F7");
    private static final int MUTED = Color.parseColor("#9AA5B1");
    private static final int GREEN = Color.parseColor("#2E9E6B");
    private static final int RED = Color.parseColor("#D6483E");
    private static final int C_LIGHT = Color.parseColor("#4CAF7A");
    private static final int C_MID = Color.parseColor("#E39A2D");
    private static final int C_STRONG = Color.parseColor("#D6483E");

    private static final String[] STRENGTH = {"leicht", "mittel", "stark"};

    private SharedPreferences prefs;
    // Jeder Eintrag: {start, ende, staerke (-1 = noch nicht gewaehlt, sonst 0..2)}
    private final List<long[]> records = new ArrayList<long[]>();
    private long runningStart = 0;

    private final Handler handler = new Handler();
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            handler.postDelayed(this, 500);
        }
    };

    private TextView statusText;
    private TextView timerText;
    private LinearLayout mainArea;
    private LinearLayout listArea;
    private ScrollView listScroll;
    private TextView listHeader;
    private Button undoButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        load();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    // ---------- Daten ----------

    private void load() {
        records.clear();
        runningStart = prefs.getLong(K_RUNNING, 0);
        String raw = prefs.getString(K_RECORDS, "");
        if (raw.length() == 0) return;
        for (String part : raw.split(",")) {
            String[] f = part.split(":");
            if (f.length != 3) continue;
            try {
                records.add(new long[]{Long.parseLong(f[0]), Long.parseLong(f[1]), Long.parseLong(f[2])});
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            long[] r = records.get(i);
            if (i > 0) sb.append(',');
            sb.append(r[0]).append(':').append(r[1]).append(':').append(r[2]);
        }
        // commit() statt apply(): Daten sollen sofort auf dem Speicher sein
        prefs.edit().putString(K_RECORDS, sb.toString()).putLong(K_RUNNING, runningStart).commit();
    }

    private boolean askingStrength() {
        return runningStart == 0 && !records.isEmpty() && records.get(records.size() - 1)[2] < 0;
    }

    // ---------- Aktionen ----------

    private void onMainTap() {
        long now = System.currentTimeMillis();
        if (runningStart == 0) {
            runningStart = now;
        } else {
            records.add(new long[]{runningStart, now, -1});
            runningStart = 0;
        }
        save();
        render();
    }

    private void onStrength(int s) {
        if (records.isEmpty()) return;
        records.get(records.size() - 1)[2] = s;
        save();
        render();
    }

    private void onUndo() {
        final boolean running = runningStart != 0;
        String msg = running ? "Laufende Wehe verwerfen?" : "Letzte Wehe löschen?";
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setNegativeButton("Nein", null)
                .setPositiveButton("Ja", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (running) {
                            runningStart = 0;
                        } else if (!records.isEmpty()) {
                            records.remove(records.size() - 1);
                        }
                        save();
                        render();
                    }
                })
                .show();
    }

    // ---------- UI ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(28), dp(20), dp(16));

        statusText = new TextView(this);
        statusText.setTextColor(TEXT);
        statusText.setTextSize(26);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(statusText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        timerText = new TextView(this);
        timerText.setTextColor(TEXT);
        timerText.setTextSize(56);
        timerText.setGravity(Gravity.CENTER);
        timerText.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(timerText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mainArea = new LinearLayout(this);
        mainArea.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f);
        mp.topMargin = dp(12);
        root.addView(mainArea, mp);

        TextView header = new TextView(this);
        listHeader = header;
        header.setText("Heute");
        header.setTextColor(MUTED);
        header.setTextSize(15);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(16);
        root.addView(header, hp);

        listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);
        listScroll = new ScrollView(this);
        listScroll.addView(listArea, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(listScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 2f));

        undoButton = new Button(this);
        undoButton.setAllCaps(false);
        undoButton.setTextSize(15);
        undoButton.setTextColor(MUTED);
        undoButton.setBackgroundColor(Color.TRANSPARENT);
        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUndo();
            }
        });
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        up.topMargin = dp(8);
        root.addView(undoButton, up);

        setContentView(root);
    }

    private Button bigButton(String text, int color, int sizeSp, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(sizeSp);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(28));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), bg, null));
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    private void render() {
        mainArea.removeAllViews();

        if (runningStart != 0) {
            statusText.setText("Wehe läuft");
            timerText.setVisibility(View.VISIBLE);
            addFill(bigButton("Wehe vorbei", RED, 34, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onMainTap();
                }
            }), 0);
            undoButton.setText("Abbrechen");
            undoButton.setVisibility(View.VISIBLE);
        } else if (askingStrength()) {
            statusText.setText("Wie stark war die Wehe?");
            timerText.setVisibility(View.GONE);
            int[] colors = {C_LIGHT, C_MID, C_STRONG};
            for (int i = 0; i < 3; i++) {
                final int s = i;
                String label = STRENGTH[i].substring(0, 1).toUpperCase(Locale.GERMAN) + STRENGTH[i].substring(1);
                addFill(bigButton(label, colors[i], 30, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onStrength(s);
                    }
                }), i == 0 ? 0 : dp(12));
            }
            undoButton.setText("Letzte Wehe löschen");
            undoButton.setVisibility(View.VISIBLE);
        } else {
            statusText.setText("Bereit");
            timerText.setVisibility(View.GONE);
            addFill(bigButton("Wehe beginnt", GREEN, 34, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onMainTap();
                }
            }), 0);
            undoButton.setText("Letzte Wehe löschen");
            undoButton.setVisibility(records.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }

        renderList();
        updateTimer();
    }

    private void addFill(Button b, int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        p.topMargin = topMargin;
        mainArea.addView(b, p);
    }

    private void renderList() {
        listArea.removeAllViews();
        int n = records.size();
        long dayStart = startOfToday();
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (records.get(i)[0] >= dayStart) count++;
        }
        listHeader.setText(count == 1 ? "Heute: 1 Wehe" : "Heute: " + count + " Wehen");
        listScroll.scrollTo(0, 0);
        if (count == 0) {
            TextView none = new TextView(this);
            none.setText("Heute noch keine Wehe erfasst");
            none.setTextColor(MUTED);
            none.setTextSize(18);
            none.setPadding(0, dp(8), 0, dp(8));
            listArea.addView(none);
            return;
        }
        for (int idx = n - 1; idx >= 0 && records.get(idx)[0] >= dayStart; idx--) {
            long[] r = records.get(idx);

            TextView line1 = new TextView(this);
            String strength = r[2] >= 0 && r[2] <= 2 ? STRENGTH[(int) r[2]] : "Stärke offen";
            line1.setText(fmtClock(r[0]) + "  ·  " + strength);
            line1.setTextColor(TEXT);
            line1.setTextSize(22);
            line1.setTypeface(Typeface.DEFAULT_BOLD);
            line1.setPadding(0, dp(8), 0, 0);
            listArea.addView(line1);

            String detail = "Dauer " + fmtDur(r[1] - r[0]);
            if (idx > 0) {
                detail += "  ·  Abstand " + fmtDur(r[0] - records.get(idx - 1)[0]);
            }
            TextView line2 = new TextView(this);
            line2.setText(detail);
            line2.setTextColor(MUTED);
            line2.setTextSize(16);
            listArea.addView(line2);
        }
    }

    private void updateTimer() {
        if (runningStart != 0) {
            timerText.setText(fmtDur(System.currentTimeMillis() - runningStart));
        }
    }

    // ---------- Formatierung ----------

    private long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Uhrzeit; mit Datum davor, falls nicht heute. */
    private String fmtClock(long t) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTime(new Date(t));
        boolean today = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        String time = new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(t));
        if (today) return time + " Uhr";
        return new SimpleDateFormat("dd.MM.", Locale.GERMANY).format(new Date(t)) + " " + time + " Uhr";
    }

    /** m:ss, bei mehr als einer Stunde weiter in Minuten (z.B. 75:10). */
    private String fmtDur(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long m = s / 60;
        long sec = s % 60;
        return m + ":" + (sec < 10 ? "0" : "") + sec + " Min";
    }
}
