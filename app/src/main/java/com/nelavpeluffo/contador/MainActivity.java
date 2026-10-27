package com.nelavpeluffo.contador;

import android.app.Activity;
import android.content.SharedPreferences;
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
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Pantalla dividida en dos contadores sincronizados:
 *  - Arriba: tiempo total de la rutina.
 *  - Abajo: bloques de 15 / 25 / 30 s que se repiten solos.
 *
 * Con 1 ejercicio, cada bloque alterna el fondo turquesa / verde oscuro.
 * Con 2 a 5 ejercicios, cada bloque es el ejercicio siguiente (cada uno con su color)
 * y una ronda se completa al terminar el último.
 */
public class MainActivity extends Activity {

    private static final long TICK_MS = 50;

    private static final int IDLE = 0, RUNNING = 1, PAUSED = 2, FINISHED = 3;

    // Fondo y color de texto de cada bloque. 0 = turquesa, 1 = verde oscuro.
    private static final int[] BLOCK_BG = {0xFF2DD4BF, 0xFF0F5132, 0xFFFBBF24, 0xFFFB7185, 0xFF7C3AED};
    private static final int[] BLOCK_FG = {0xFF042F2B, 0xFFFFFFFF, 0xFF2B1B00, 0xFF3B0A14, 0xFFFFFFFF};

    private static final int TOTAL_ACCENT = 0xFF60A5FA;
    private static final int DARK = 0xFF0B0E13;
    private static final int WHITE = 0xFFFFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            if (state == RUNNING) {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    // --- Configuración ---
    private int intervalSec = 30;
    private int exercises = 1;

    // --- Estado compartido ---
    private int state = IDLE;
    private long totalAccumMs;
    private long totalStartedAt;
    private long blockRemainingMs = 30_000;
    private long blockEndsAt;
    private int blocksDone;
    private long lastBeepSecond = -1;

    // --- Vistas ---
    private View topRunning, topSummary, bottomHalf, configBox;
    private TextView totalTime, intervalTime, exerciseLabel, roundLabel;
    private TextView summaryTotal, summaryInterval, detailTotal, detailRounds, detailConfig;
    private TextView captionSeconds, captionExercises;
    private TextView[] secChips;
    private final TextView[] exCircles = new TextView[5];
    private ImageView totalPlay, totalStop, intervalPlay, intervalStop, intervalReset;
    private ProgressBar intervalProgress;

    private int renderedColor = -1;
    private int renderedState = -1;
    private boolean playShowsPause;

    private ToneGenerator tone;
    private Vibrator vibrator;
    private float density;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        density = getResources().getDisplayMetrics().density;

        prefs = getSharedPreferences("config", MODE_PRIVATE);
        intervalSec = prefs.getInt("intervalSec", 30);
        exercises = prefs.getInt("exercises", 1);
        blockRemainingMs = intervalSec * 1000L;

        topRunning = findViewById(R.id.topRunning);
        topSummary = findViewById(R.id.topSummary);
        bottomHalf = findViewById(R.id.bottomHalf);
        configBox = findViewById(R.id.configBox);
        totalTime = findViewById(R.id.totalTime);
        intervalTime = findViewById(R.id.intervalTime);
        exerciseLabel = findViewById(R.id.exerciseLabel);
        roundLabel = findViewById(R.id.roundLabel);
        summaryTotal = findViewById(R.id.summaryTotal);
        summaryInterval = findViewById(R.id.summaryInterval);
        detailTotal = findViewById(R.id.detailTotal);
        detailRounds = findViewById(R.id.detailRounds);
        detailConfig = findViewById(R.id.detailConfig);
        captionSeconds = findViewById(R.id.captionSeconds);
        captionExercises = findViewById(R.id.captionExercises);
        totalPlay = findViewById(R.id.totalPlay);
        totalStop = findViewById(R.id.totalStop);
        intervalPlay = findViewById(R.id.intervalPlay);
        intervalStop = findViewById(R.id.intervalStop);
        intervalReset = findViewById(R.id.intervalReset);
        intervalProgress = findViewById(R.id.intervalProgress);
        secChips = new TextView[]{findViewById(R.id.sel15), findViewById(R.id.sel25), findViewById(R.id.sel30)};

        buildExerciseCircles();

        // Los dos contadores comparten play/pausa y finalizar.
        totalPlay.setOnClickListener(v -> togglePlay());
        intervalPlay.setOnClickListener(v -> togglePlay());
        totalTime.setOnClickListener(v -> togglePlay());
        intervalTime.setOnClickListener(v -> togglePlay());
        totalStop.setOnClickListener(v -> finishRoutine(true));
        intervalStop.setOnClickListener(v -> finishRoutine(true));
        intervalReset.setOnClickListener(v -> resetBlock());

        int[] options = {15, 25, 30};
        for (int i = 0; i < 3; i++) {
            final int sec = options[i];
            secChips[i].setOnClickListener(v -> selectSeconds(sec));
        }

        totalPlay.setBackground(pill(TOTAL_ACCENT, 0));
        totalPlay.setImageTintList(ColorStateList.valueOf(DARK));
        totalStop.setBackground(pill(0, 0x55FFFFFF));
        totalStop.setImageTintList(ColorStateList.valueOf(WHITE));

        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (RuntimeException e) {
            tone = null;
        }
        vibrator = getSystemService(Vibrator.class);

        if (savedInstanceState != null) {
            restore(savedInstanceState);
        }

        render();
        ensureTicking();
    }

    private void buildExerciseCircles() {
        LinearLayout row = findViewById(R.id.exerciseRow);
        int size = Math.round(40 * density);
        for (int i = 0; i < 5; i++) {
            final int n = i + 1;
            TextView c = new TextView(this);
            c.setText(String.valueOf(n));
            c.setGravity(Gravity.CENTER);
            c.setTextSize(16);
            c.setTypeface(c.getTypeface(), android.graphics.Typeface.BOLD);
            c.setClickable(true);
            c.setFocusable(true);
            c.setContentDescription(n == 1 ? "1 ejercicio" : n + " ejercicios");
            c.setOnClickListener(v -> selectExercises(n));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, size, 1f);
            lp.leftMargin = Math.round(3 * density);
            lp.rightMargin = Math.round(3 * density);
            row.addView(c, lp);
            exCircles[i] = c;
        }
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

    // ================= Acciones =================

    /** Play/pausa: siempre afecta a los dos contadores juntos. */
    private void togglePlay() {
        long now = SystemClock.elapsedRealtime();
        if (state == RUNNING) {
            totalAccumMs += now - totalStartedAt;
            blockRemainingMs = Math.max(0, blockEndsAt - now);
            state = PAUSED;
        } else {
            if (state == IDLE || state == FINISHED) {
                // Rutina nueva desde cero.
                totalAccumMs = 0;
                blocksDone = 0;
                blockRemainingMs = intervalSec * 1000L;
                lastBeepSecond = -1;
            }
            totalStartedAt = now;
            blockEndsAt = now + blockRemainingMs;
            state = RUNNING;
        }
        ensureTicking();
        render();
    }

    /** Finalizar: detiene los dos contadores y muestra el resumen. */
    private void finishRoutine(boolean withSound) {
        if (state != RUNNING && state != PAUSED) return;
        long now = SystemClock.elapsedRealtime();
        if (state == RUNNING) {
            totalAccumMs += now - totalStartedAt;
            blockRemainingMs = Math.max(0, blockEndsAt - now);
        }
        state = FINISHED;
        handler.removeCallbacks(ticker);
        if (withSound) {
            beep(ToneGenerator.TONE_PROP_ACK, 400);
        }
        render();
    }

    /** Reiniciar solo el bloque de abajo; el tiempo total sigue. */
    private void resetBlock() {
        if (state != RUNNING && state != PAUSED) return;
        blockRemainingMs = intervalSec * 1000L;
        if (state == RUNNING) {
            blockEndsAt = SystemClock.elapsedRealtime() + blockRemainingMs;
        }
        lastBeepSecond = -1;
        render();
    }

    private void selectSeconds(int sec) {
        if (state == RUNNING || state == PAUSED) return;
        intervalSec = sec;
        blockRemainingMs = sec * 1000L;
        prefs.edit().putInt("intervalSec", sec).apply();
        renderedColor = -1;
        render();
    }

    private void selectExercises(int n) {
        if (state == RUNNING || state == PAUSED) return;
        exercises = n;
        prefs.edit().putInt("exercises", n).apply();
        renderedColor = -1;
        render();
    }

    // ================= Loop =================

    private void ensureTicking() {
        handler.removeCallbacks(ticker);
        if (state == RUNNING) {
            handler.post(ticker);
        }
    }

    private void tick() {
        if (state != RUNNING) return;
        long now = SystemClock.elapsedRealtime();
        long remaining = blockEndsAt - now;
        if (remaining <= 0) {
            long len = intervalSec * 1000L;
            boolean roundDone = false;
            while (remaining <= 0) {
                blockEndsAt += len;
                remaining += len;
                blocksDone++;
                if (blocksDone % exercises == 0) roundDone = true;
            }
            lastBeepSecond = -1;
            signalBlockEnd(roundDone && exercises > 1);
        } else {
            long sec = (remaining + 999) / 1000;
            if (sec <= 3 && sec != lastBeepSecond) {
                lastBeepSecond = sec;
                beep(ToneGenerator.TONE_PROP_BEEP, 150);
            }
        }
        render();
    }

    // ================= Pantalla =================

    private boolean active() {
        return state == RUNNING || state == PAUSED;
    }

    /** Índice de color del bloque actual. */
    private int colorIndex() {
        if (!active()) return 0;
        if (exercises == 1) return blocksDone % 2;
        return blocksDone % exercises;
    }

    private void render() {
        long now = SystemClock.elapsedRealtime();
        boolean active = active();

        // --- Arriba ---
        long total = totalAccumMs + (state == RUNNING ? now - totalStartedAt : 0);
        if (state != renderedState) {
            topRunning.setVisibility(state == FINISHED ? View.GONE : View.VISIBLE);
            topSummary.setVisibility(state == FINISHED ? View.VISIBLE : View.GONE);
            configBox.setVisibility(active ? View.GONE : View.VISIBLE);
            setEnabled(totalStop, active);
            setEnabled(intervalStop, active);
            setEnabled(intervalReset, active);
            if (state == FINISHED) fillSummary();
        }
        totalTime.setText(formatTime(total));

        boolean showPause = state == RUNNING;
        if (showPause != playShowsPause || state != renderedState) {
            playShowsPause = showPause;
            int icon = showPause ? R.drawable.ic_pause : R.drawable.ic_play;
            totalPlay.setImageResource(icon);
            intervalPlay.setImageResource(icon);
        }

        // --- Abajo ---
        long len = intervalSec * 1000L;
        long remaining = state == RUNNING ? Math.max(0, blockEndsAt - now)
                : (active ? blockRemainingMs : len);
        intervalTime.setText(String.valueOf((remaining + 999) / 1000));
        intervalProgress.setProgress((int) (1000 * remaining / len));

        if (active) {
            int ex = blocksDone % exercises + 1;
            int round = blocksDone / exercises + 1;
            exerciseLabel.setText(exercises > 1
                    ? String.format(Locale.US, "EJERCICIO %d/%d", ex, exercises)
                    : String.format(Locale.US, "BLOQUE %d", blocksDone + 1));
            roundLabel.setText(String.format(Locale.US, "RONDA %d", round));
        } else {
            exerciseLabel.setText(state == FINISHED ? "NUEVA RUTINA" : "PREPARADO");
            roundLabel.setText("");
        }

        int color = colorIndex();
        if (color != renderedColor || state != renderedState) {
            applyBottomColors(color);
            renderedColor = color;
        }
        renderedState = state;
    }

    private void applyBottomColors(int idx) {
        int bg = BLOCK_BG[idx];
        int fg = BLOCK_FG[idx];
        int fgSoft = withAlpha(fg, 0x66);

        bottomHalf.setBackgroundColor(bg);
        intervalTime.setTextColor(fg);
        exerciseLabel.setTextColor(fg);
        roundLabel.setTextColor(withAlpha(fg, 0xCC));
        captionSeconds.setTextColor(withAlpha(fg, 0xB3));
        captionExercises.setTextColor(withAlpha(fg, 0xB3));
        intervalProgress.setProgressTintList(ColorStateList.valueOf(fg));
        intervalProgress.setProgressBackgroundTintList(ColorStateList.valueOf(withAlpha(fg, 0x33)));

        intervalPlay.setBackground(pill(fg, 0));
        intervalPlay.setImageTintList(ColorStateList.valueOf(bg));
        intervalStop.setBackground(pill(0, fgSoft));
        intervalStop.setImageTintList(ColorStateList.valueOf(fg));
        intervalReset.setBackground(pill(0, fgSoft));
        intervalReset.setImageTintList(ColorStateList.valueOf(fg));

        int[] options = {15, 25, 30};
        for (int i = 0; i < 3; i++) {
            styleOption(secChips[i], options[i] == intervalSec, bg, fg, fgSoft, false);
        }
        for (int i = 0; i < 5; i++) {
            styleOption(exCircles[i], i + 1 == exercises, bg, fg, fgSoft, true);
        }
    }

    private void styleOption(TextView v, boolean selected, int bg, int fg, int fgSoft, boolean circle) {
        Drawable d = selected ? shape(fg, 0, circle) : shape(0, fgSoft, circle);
        v.setBackground(d);
        v.setTextColor(selected ? bg : fg);
    }

    private void fillSummary() {
        String total = formatTime(totalAccumMs);
        int rounds = blocksDone / exercises;
        int extra = blocksDone % exercises;

        summaryTotal.setText(total);
        summaryInterval.setText(String.valueOf((blockRemainingMs + 999) / 1000));
        detailTotal.setText(total);
        String r = String.valueOf(rounds);
        if (extra > 0) {
            r += extra == 1 ? " + 1 ejercicio" : " + " + extra + " ejercicios";
        }
        detailRounds.setText(r);
        detailConfig.setText(String.format(Locale.US, "%d %s de %d s · %d %s completos",
                exercises, exercises == 1 ? "ejercicio" : "ejercicios", intervalSec,
                blocksDone, blocksDone == 1 ? "bloque" : "bloques"));
    }

    private void setEnabled(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.3f);
    }

    // ================= Avisos =================

    private void signalBlockEnd(boolean roundDone) {
        beep(ToneGenerator.TONE_PROP_BEEP2, 600);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (roundDone) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 150, 300}, -1));
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
            }
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

    private static String formatTime(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private Drawable shape(int fill, int stroke, boolean circle) {
        GradientDrawable g = new GradientDrawable();
        if (circle) {
            g.setShape(GradientDrawable.OVAL);
        } else {
            g.setCornerRadius(100 * density);
        }
        g.setColor(fill);
        if (stroke != 0) {
            g.setStroke(Math.round(2 * density), stroke);
        }
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), g, null);
    }

    private Drawable pill(int fill, int stroke) {
        return shape(fill, stroke, false);
    }

    // ================= Guardar / restaurar =================

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("state", state);
        out.putLong("totalAccumMs", totalAccumMs);
        out.putLong("totalStartedAt", totalStartedAt);
        out.putLong("blockRemainingMs", blockRemainingMs);
        out.putLong("blockEndsAt", blockEndsAt);
        out.putInt("blocksDone", blocksDone);
    }

    private void restore(Bundle in) {
        state = in.getInt("state", IDLE);
        totalAccumMs = in.getLong("totalAccumMs");
        totalStartedAt = in.getLong("totalStartedAt");
        blockRemainingMs = in.getLong("blockRemainingMs", intervalSec * 1000L);
        blockEndsAt = in.getLong("blockEndsAt");
        blocksDone = in.getInt("blocksDone");
    }
}
