package com.nelavpeluffo.contador;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Pantalla dividida en dos:
 *  - Arriba: cronómetro del tiempo total de la rutina.
 *  - Abajo: temporizador de intervalos (15 / 25 / 30 s) que se repite solo,
 *    alterna entre "Ejercicio A" y "Ejercicio B" y cuenta las rondas.
 */
public class MainActivity extends Activity {

    private static final long TICK_MS = 50;

    private static final int TOTAL_ACCENT = 0xFF60A5FA;
    private static final int ACCENT_A = 0xFF2DD4BF;
    private static final int ACCENT_B = 0xFFFBBF24;
    private static final int BG_A = 0xFF0E2A28;
    private static final int BG_B = 0xFF2D230E;
    private static final int DARK_TEXT = 0xFF0B0E13;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int OUTLINE = 0x55FFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            if (totalRunning || intervalRunning) {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    // --- Estado: tiempo total ---
    private boolean totalRunning;
    private long totalAccumMs;
    private long totalStartedAt;

    // --- Estado: intervalos ---
    private int intervalSec = 30;
    private boolean intervalRunning;
    private long intervalRemainingMs = 30_000;
    private long intervalEndsAt;
    private int round = 1;
    private boolean sideA = true;
    private long lastBeepSecond = -1;

    // --- Vistas ---
    private TextView totalTime, totalStart, totalReset;
    private TextView intervalTime, intervalStart, intervalReset, sideLabel, roundLabel;
    private TextView sel15, sel25, sel30;
    private View bottomHalf;
    private ProgressBar intervalProgress;

    private ToneGenerator tone;
    private Vibrator vibrator;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        density = getResources().getDisplayMetrics().density;

        totalTime = findViewById(R.id.totalTime);
        totalStart = findViewById(R.id.totalStart);
        totalReset = findViewById(R.id.totalReset);
        intervalTime = findViewById(R.id.intervalTime);
        intervalStart = findViewById(R.id.intervalStart);
        intervalReset = findViewById(R.id.intervalReset);
        sideLabel = findViewById(R.id.sideLabel);
        roundLabel = findViewById(R.id.roundLabel);
        sel15 = findViewById(R.id.sel15);
        sel25 = findViewById(R.id.sel25);
        sel30 = findViewById(R.id.sel30);
        bottomHalf = findViewById(R.id.bottomHalf);
        intervalProgress = findViewById(R.id.intervalProgress);

        totalStart.setOnClickListener(v -> toggleTotal());
        totalTime.setOnClickListener(v -> toggleTotal());
        totalReset.setOnClickListener(v -> resetTotal());

        intervalStart.setOnClickListener(v -> toggleInterval());
        intervalTime.setOnClickListener(v -> toggleInterval());
        intervalReset.setOnClickListener(v -> resetInterval());

        sel15.setOnClickListener(v -> selectInterval(15));
        sel25.setOnClickListener(v -> selectInterval(25));
        sel30.setOnClickListener(v -> selectInterval(30));

        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (RuntimeException e) {
            tone = null;
        }
        vibrator = getSystemService(Vibrator.class);

        if (savedInstanceState != null) {
            restore(savedInstanceState);
        }

        totalStart.setBackground(pill(TOTAL_ACCENT, 0));
        totalStart.setTextColor(DARK_TEXT);
        totalReset.setBackground(pill(0, OUTLINE));

        applyIntervalColors();
        render();
        ensureTicking();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureTicking();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (tone != null) {
            tone.release();
            tone = null;
        }
        super.onDestroy();
    }

    // ================= Tiempo total =================

    private void toggleTotal() {
        long now = SystemClock.elapsedRealtime();
        if (totalRunning) {
            totalAccumMs += now - totalStartedAt;
            totalRunning = false;
        } else {
            totalStartedAt = now;
            totalRunning = true;
        }
        ensureTicking();
        render();
    }

    private void resetTotal() {
        totalRunning = false;
        totalAccumMs = 0;
        render();
    }

    private long totalElapsed(long now) {
        return totalAccumMs + (totalRunning ? now - totalStartedAt : 0);
    }

    // ================= Intervalos =================

    private void toggleInterval() {
        long now = SystemClock.elapsedRealtime();
        if (intervalRunning) {
            intervalRemainingMs = Math.max(0, intervalEndsAt - now);
            intervalRunning = false;
        } else {
            intervalEndsAt = now + intervalRemainingMs;
            intervalRunning = true;
            // Al arrancar los intervalos, arranca también el tiempo total.
            if (!totalRunning) {
                totalStartedAt = now;
                totalRunning = true;
            }
        }
        ensureTicking();
        render();
    }

    private void resetInterval() {
        intervalRunning = false;
        intervalRemainingMs = intervalSec * 1000L;
        round = 1;
        sideA = true;
        lastBeepSecond = -1;
        applyIntervalColors();
        render();
    }

    private void selectInterval(int seconds) {
        intervalSec = seconds;
        intervalRemainingMs = seconds * 1000L;
        lastBeepSecond = -1;
        if (intervalRunning) {
            intervalEndsAt = SystemClock.elapsedRealtime() + intervalRemainingMs;
        }
        applyIntervalColors();
        render();
    }

    // ================= Loop =================

    private void ensureTicking() {
        handler.removeCallbacks(ticker);
        if (totalRunning || intervalRunning) {
            handler.post(ticker);
        }
    }

    private void tick() {
        long now = SystemClock.elapsedRealtime();
        if (intervalRunning) {
            long remaining = intervalEndsAt - now;
            if (remaining <= 0) {
                long len = intervalSec * 1000L;
                while (remaining <= 0) {
                    intervalEndsAt += len;
                    remaining += len;
                    round++;
                    sideA = !sideA;
                }
                lastBeepSecond = -1;
                signalIntervalEnd();
                applyIntervalColors();
            } else {
                long sec = (remaining + 999) / 1000;
                if (sec <= 3 && sec != lastBeepSecond) {
                    lastBeepSecond = sec;
                    beep(ToneGenerator.TONE_PROP_BEEP, 150);
                }
            }
        }
        render();
    }

    private void render() {
        long now = SystemClock.elapsedRealtime();

        long total = totalElapsed(now);
        totalTime.setText(formatTotal(total));
        totalStart.setText(totalRunning ? "PAUSA" : (total > 0 ? "SEGUIR" : "INICIAR"));

        long remaining = intervalRunning ? Math.max(0, intervalEndsAt - now) : intervalRemainingMs;
        long len = intervalSec * 1000L;
        intervalTime.setText(String.valueOf((remaining + 999) / 1000));
        intervalProgress.setProgress((int) (1000 * remaining / len));
        boolean fresh = !intervalRunning && remaining == len && round == 1;
        intervalStart.setText(intervalRunning ? "PAUSA" : (fresh ? "INICIAR" : "SEGUIR"));
        sideLabel.setText(sideA ? "EJERCICIO A" : "EJERCICIO B");
        roundLabel.setText(String.format(Locale.US, "RONDA %d", round));
    }

    /** Colores que dependen del lado (A/B) y del intervalo elegido. */
    private void applyIntervalColors() {
        int accent = sideA ? ACCENT_A : ACCENT_B;
        bottomHalf.setBackgroundColor(sideA ? BG_A : BG_B);
        sideLabel.setTextColor(accent);
        intervalProgress.setProgressTintList(ColorStateList.valueOf(accent));
        intervalProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));

        intervalStart.setBackground(pill(accent, 0));
        intervalStart.setTextColor(DARK_TEXT);
        intervalReset.setBackground(pill(0, OUTLINE));

        styleChip(sel15, intervalSec == 15, accent);
        styleChip(sel25, intervalSec == 25, accent);
        styleChip(sel30, intervalSec == 30, accent);
    }

    private void styleChip(TextView chip, boolean selected, int accent) {
        chip.setBackground(selected ? pill(0x33FFFFFF, accent) : pill(0, OUTLINE));
        chip.setTextColor(selected ? accent : WHITE);
    }

    // ================= Avisos =================

    private void signalIntervalEnd() {
        beep(ToneGenerator.TONE_PROP_BEEP2, 600);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(450, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void beep(int toneType, int durationMs) {
        if (tone != null) {
            try {
                tone.startTone(toneType, durationMs);
            } catch (RuntimeException ignored) {
            }
        }
    }

    // ================= Utilidades =================

    private static String formatTotal(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }

    private Drawable pill(int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(100 * density);
        shape.setColor(fill);
        if (stroke != 0) {
            shape.setStroke(Math.round(2 * density), stroke);
        }
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), shape, null);
    }

    // ================= Guardar / restaurar =================

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("totalRunning", totalRunning);
        out.putLong("totalAccumMs", totalAccumMs);
        out.putLong("totalStartedAt", totalStartedAt);
        out.putInt("intervalSec", intervalSec);
        out.putBoolean("intervalRunning", intervalRunning);
        out.putLong("intervalRemainingMs", intervalRemainingMs);
        out.putLong("intervalEndsAt", intervalEndsAt);
        out.putInt("round", round);
        out.putBoolean("sideA", sideA);
    }

    private void restore(Bundle in) {
        totalRunning = in.getBoolean("totalRunning");
        totalAccumMs = in.getLong("totalAccumMs");
        totalStartedAt = in.getLong("totalStartedAt");
        intervalSec = in.getInt("intervalSec", 30);
        intervalRunning = in.getBoolean("intervalRunning");
        intervalRemainingMs = in.getLong("intervalRemainingMs", intervalSec * 1000L);
        intervalEndsAt = in.getLong("intervalEndsAt");
        round = in.getInt("round", 1);
        sideA = in.getBoolean("sideA", true);
    }
}
