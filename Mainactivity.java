package com.coinflip.flipit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // ── Views ──
    private FrameLayout coinContainer;
    private FrameLayout coinFace;
    private TextView tvCoinEmoji;
    private TextView tvCoinLabel;
    private TextView tvResult;
    private TextView tvStreak;
    private TextView btnFlip;
    private TextView tvTotalFlips;
    private TextView tvHeadsCount;
    private TextView tvTailsCount;
    private View statBarHeads;
    private View statBarTails;
    private LinearLayout historyContainer;
    private TextView tvNoHistory;
    private TextView btnClearHistory;

    // ── State ──
    private boolean isFlipping = false;
    private boolean lastWasHeads = true; // tracks current coin face shown
    private final Random random = new Random();

    private int headsCount = 0;
    private int tailsCount = 0;
    private int streakCount = 0;
    private boolean streakIsHeads = false;

    private final List<FlipResult> history = new ArrayList<>();
    private static final int MAX_HISTORY = 20;

    // ── Prefs ──
    private SharedPreferences prefs;
    private static final String PREF_NAME    = "flipit_prefs";
    private static final String KEY_HEADS    = "heads_count";
    private static final String KEY_TAILS    = "tails_count";
    private static final String KEY_HISTORY  = "history_json";

    // ── Date format ──
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    // ────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        loadState();
        setupClickListeners();
        updateStats();
        renderHistory();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }

    // ────────────────────────────────────────────────
    // View binding
    // ────────────────────────────────────────────────

    private void bindViews() {
        coinContainer   = findViewById(R.id.coinContainer);
        coinFace        = findViewById(R.id.coinFace);
        tvCoinEmoji     = findViewById(R.id.tvCoinEmoji);
        tvCoinLabel     = findViewById(R.id.tvCoinLabel);
        tvResult        = findViewById(R.id.tvResult);
        tvStreak        = findViewById(R.id.tvStreak);
        btnFlip         = findViewById(R.id.btnFlip);
        tvTotalFlips    = findViewById(R.id.tvTotalFlips);
        tvHeadsCount    = findViewById(R.id.tvHeadsCount);
        tvTailsCount    = findViewById(R.id.tvTailsCount);
        statBarHeads    = findViewById(R.id.statBarHeads);
        statBarTails    = findViewById(R.id.statBarTails);
        historyContainer= findViewById(R.id.historyContainer);
        tvNoHistory     = findViewById(R.id.tvNoHistory);
        btnClearHistory = findViewById(R.id.btnClearHistory);
    }

    // ────────────────────────────────────────────────
    // Click listeners
    // ────────────────────────────────────────────────

    private void setupClickListeners() {
        View.OnClickListener flipListener = v -> {
            if (!isFlipping) startFlip();
        };
        btnFlip.setOnClickListener(flipListener);
        coinContainer.setOnClickListener(flipListener);

        btnClearHistory.setOnClickListener(v -> clearHistory());
    }

    // ────────────────────────────────────────────────
    // Flip animation + logic
    // ────────────────────────────────────────────────

    private void startFlip() {
        isFlipping = true;
        btnFlip.setText(getString(R.string.flipping));
        btnFlip.setEnabled(false);
        tvResult.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

        // Determine result
        final boolean isHeads = random.nextBoolean();

        // Number of half-rotations for the "spin" effect (odd = flip face, even = same face)
        // We do several quick spins then land on result
        doSpinThenLand(isHeads, 5); // 5 half-flips before landing
    }

    private void doSpinThenLand(boolean finalIsHeads, int remainingSpins) {
        // Scale X 1→0 (first half of flip)
        ObjectAnimator scaleXOut = ObjectAnimator.ofFloat(coinFace, "scaleX", 1f, 0f);
        ObjectAnimator scaleYSqueeze = ObjectAnimator.ofFloat(coinFace, "scaleY", 1f, 0.9f);
        scaleXOut.setDuration(remainingSpins > 0 ? 120 : 180);
        scaleYSqueeze.setDuration(remainingSpins > 0 ? 120 : 180);
        scaleXOut.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet outSet = new AnimatorSet();
        outSet.playTogether(scaleXOut, scaleYSqueeze);

        outSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Mid-flip: swap face if odd remaining (alternating)
                if (remainingSpins == 0) {
                    // Final landing face
                    applyFace(finalIsHeads);
                } else {
                    // Intermediate spin — show opposite face briefly
                    boolean showHeads = (remainingSpins % 2 == 0) == finalIsHeads;
                    applyFace(showHeads);
                }

                // Scale X 0→1 (second half of flip)
                ObjectAnimator scaleXIn = ObjectAnimator.ofFloat(coinFace, "scaleX", 0f, 1f);
                ObjectAnimator scaleYRestore = ObjectAnimator.ofFloat(coinFace, "scaleY", 0.9f, 1f);
                long dur = remainingSpins > 0 ? 120 : 220;
                scaleXIn.setDuration(dur);
                scaleYRestore.setDuration(dur);
                if (remainingSpins == 0) {
                    scaleXIn.setInterpolator(new BounceInterpolator());
                } else {
                    scaleXIn.setInterpolator(new AccelerateDecelerateInterpolator());
                }

                AnimatorSet inSet = new AnimatorSet();
                inSet.playTogether(scaleXIn, scaleYRestore);

                inSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (remainingSpins > 0) {
                            doSpinThenLand(finalIsHeads, remainingSpins - 1);
                        } else {
                            onFlipLanded(finalIsHeads);
                        }
                    }
                });
                inSet.start();
            }
        });

        outSet.start();
    }

    private void applyFace(boolean isHeads) {
        if (isHeads) {
            coinFace.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_coin_heads));
            tvCoinEmoji.setText("🪙");
            tvCoinLabel.setText(getString(R.string.heads));
            tvCoinLabel.setTextColor(ContextCompat.getColor(this, R.color.gold_dark));
            coinContainer.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_coin_glow));
            setOuterCoinColor(R.color.gold_dark);
        } else {
            coinFace.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_coin_tails));
            tvCoinEmoji.setText("🌙");
            tvCoinLabel.setText(getString(R.string.tails));
            tvCoinLabel.setTextColor(ContextCompat.getColor(this, R.color.silver_dark));
            coinContainer.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_coin_glow_silver));
            setOuterCoinColor(R.color.silver_dark);
        }
        lastWasHeads = isHeads;
    }

    /** Tints the outer ring FrameLayout's background drawable. */
    private void setOuterCoinColor(int colorRes) {
        // The first child of coinContainer is the outer ring FrameLayout
        if (coinContainer.getChildCount() > 0) {
            View outerRing = coinContainer.getChildAt(0);
            int color = ContextCompat.getColor(this, colorRes);
            if (outerRing.getBackground() instanceof GradientDrawable) {
                GradientDrawable bg = (GradientDrawable) outerRing.getBackground().mutate();
                bg.setColor(color);
            }
        }
    }

    private void onFlipLanded(boolean isHeads) {
        isFlipping = false;
        btnFlip.setText(getString(R.string.flip));
        btnFlip.setEnabled(true);

        // Update counts
        if (isHeads) {
            headsCount++;
        } else {
            tailsCount++;
        }

        // Update streak
        if (history.isEmpty() || isHeads == streakIsHeads) {
            streakCount++;
            streakIsHeads = isHeads;
        } else {
            streakCount = 1;
            streakIsHeads = isHeads;
        }

        // Result text
        String resultText = isHeads ? getString(R.string.result_heads) : getString(R.string.result_tails);
        tvResult.setText(resultText);
        int resultColor = isHeads
                ? ContextCompat.getColor(this, R.color.gold_primary)
                : ContextCompat.getColor(this, R.color.silver_primary);
        tvResult.setTextColor(resultColor);

        // Animate result text
        tvResult.setAlpha(0f);
        tvResult.setTranslationY(20f);
        tvResult.animate().alpha(1f).translationY(0f).setDuration(250).start();

        // Streak text
        if (streakCount >= 2) {
            tvStreak.setText(streakCount + "× " + (isHeads ? "Heads" : "Tails") + " in a row!");
            tvStreak.setVisibility(View.VISIBLE);
            tvStreak.setTextColor(isHeads
                    ? ContextCompat.getColor(this, R.color.gold_primary)
                    : ContextCompat.getColor(this, R.color.silver_primary));
        } else {
            tvStreak.setVisibility(View.INVISIBLE);
        }

        // Add to history
        FlipResult result = new FlipResult(
                isHeads,
                headsCount + tailsCount,
                timeFormat.format(new Date())
        );
        history.add(0, result); // newest first
        if (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }

        updateStats();
        renderHistory();
        saveState();
    }

    // ────────────────────────────────────────────────
    // Stats
    // ────────────────────────────────────────────────

    private void updateStats() {
        int total = headsCount + tailsCount;
        tvTotalFlips.setText(total + (total == 1 ? " flip" : " flips"));
        tvHeadsCount.setText(String.valueOf(headsCount));
        tvTailsCount.setText(String.valueOf(tailsCount));

        // Animate stat bars
        statBarHeads.post(() -> {
            int parentWidth = ((ViewGroup) statBarHeads.getParent()).getWidth();
            if (parentWidth == 0 || total == 0) {
                statBarHeads.getLayoutParams().width = 0;
                statBarTails.getLayoutParams().width = 0;
            } else {
                int headsWidth = (int) ((float) headsCount / total * parentWidth);
                int tailsWidth = (int) ((float) tailsCount / total * parentWidth);

                // Animate heads bar
                android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(
                        statBarHeads.getWidth(), headsWidth);
                anim.setDuration(400);
                anim.addUpdateListener(va -> {
                    statBarHeads.getLayoutParams().width = (int) va.getAnimatedValue();
                    statBarHeads.requestLayout();
                });
                anim.start();

                // Animate tails bar
                android.animation.ValueAnimator animT = android.animation.ValueAnimator.ofInt(
                        statBarTails.getWidth(), tailsWidth);
                animT.setDuration(400);
                animT.addUpdateListener(va -> {
                    statBarTails.getLayoutParams().width = (int) va.getAnimatedValue();
                    statBarTails.requestLayout();
                });
                animT.start();
            }
            statBarHeads.requestLayout();
            statBarTails.requestLayout();
        });
    }

    // ────────────────────────────────────────────────
    // History rendering
    // ────────────────────────────────────────────────

    private void renderHistory() {
        historyContainer.removeAllViews();

        if (history.isEmpty()) {
            tvNoHistory.setVisibility(View.VISIBLE);
            return;
        }

        tvNoHistory.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (FlipResult result : history) {
            View itemView = inflater.inflate(R.layout.item_history, historyContainer, false);

            FrameLayout badge       = itemView.findViewById(R.id.historyBadge);
            TextView badgeText      = itemView.findViewById(R.id.tvHistoryBadgeText);
            TextView tvResultText   = itemView.findViewById(R.id.tvHistoryResult);
            TextView tvFlipNumber   = itemView.findViewById(R.id.tvHistoryFlipNumber);
            TextView tvTime         = itemView.findViewById(R.id.tvHistoryTime);

            if (result.isHeads) {
                badge.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_history_badge_heads));
                badgeText.setText(getString(R.string.heads_label));
                badgeText.setTextColor(ContextCompat.getColor(this, R.color.history_heads_text));
                tvResultText.setText(getString(R.string.heads));
                tvResultText.setTextColor(ContextCompat.getColor(this, R.color.history_heads_text));
            } else {
                badge.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_history_badge_tails));
                badgeText.setText(getString(R.string.tails_label));
                badgeText.setTextColor(ContextCompat.getColor(this, R.color.history_tails_text));
                tvResultText.setText(getString(R.string.tails));
                tvResultText.setTextColor(ContextCompat.getColor(this, R.color.history_tails_text));
            }

            tvFlipNumber.setText("#" + result.flipNumber);
            tvTime.setText(result.time);

            historyContainer.addView(itemView);
        }
    }

    private void clearHistory() {
        history.clear();
        headsCount = 0;
        tailsCount = 0;
        streakCount = 0;

        tvResult.setText(getString(R.string.tap_to_flip));
        tvResult.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvStreak.setVisibility(View.INVISIBLE);

        updateStats();
        renderHistory();
        saveState();
    }

    // ────────────────────────────────────────────────
    // Persistence
    // ────────────────────────────────────────────────

    private void saveState() {
        if (prefs == null) prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String historyJson = new Gson().toJson(history);
        prefs.edit()
                .putInt(KEY_HEADS, headsCount)
                .putInt(KEY_TAILS, tailsCount)
                .putString(KEY_HISTORY, historyJson)
                .apply();
    }

    private void loadState() {
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        headsCount = prefs.getInt(KEY_HEADS, 0);
        tailsCount = prefs.getInt(KEY_TAILS, 0);

        String historyJson = prefs.getString(KEY_HISTORY, null);
        if (historyJson != null) {
            Type type = new TypeToken<List<FlipResult>>() {}.getType();
            List<FlipResult> saved = new Gson().fromJson(historyJson, type);
            if (saved != null) history.addAll(saved);
        }

        // Recalculate streak from history
        if (!history.isEmpty()) {
            streakIsHeads = history.get(0).isHeads;
            for (FlipResult r : history) {
                if (r.isHeads == streakIsHeads) streakCount++;
                else break;
            }
        }
    }

    // ────────────────────────────────────────────────
    // Data model
    // ────────────────────────────────────────────────

    static class FlipResult {
        boolean isHeads;
        int flipNumber;
        String time;

        FlipResult(boolean isHeads, int flipNumber, String time) {
            this.isHeads = isHeads;
            this.flipNumber = flipNumber;
            this.time = time;
        }
    }
}
