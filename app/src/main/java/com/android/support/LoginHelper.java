package com.android.support;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class LoginHelper {

    public interface Callback {
        void onLoginSuccess();
    }

    public interface CheckListener {
        void onChanged(boolean checked);
    }

    private static final String TAG = "LoginHelper";

    // ---- Palette (Obsidian & Gilt) ----
    private static final int COLOR_BG_1        = Color.parseColor("#0C0E14");
    // Card is now transparent (blends with menu bg)
    private static final int COLOR_CARD        = Color.TRANSPARENT;
    private static final int COLOR_CARD_TOP    = Color.TRANSPARENT;
    private static final int COLOR_BORDER      = Color.TRANSPARENT;
    // Input field: ash / dark gray
    private static final int COLOR_FIELD_BG    = Color.parseColor("#2A2D35");
    private static final int COLOR_FIELD_BG_HI = Color.parseColor("#33363F");
    private static final int COLOR_FIELD_BORD  = Color.parseColor("#3E424C");
    private static final int COLOR_ACCENT      = Color.parseColor("#D8B36C");
    private static final int COLOR_ACCENT_HI   = Color.parseColor("#F1DFAE");
    private static final int COLOR_ACCENT_DEEP = Color.parseColor("#C79C56");
    private static final int COLOR_SUCCESS     = Color.parseColor("#4FBA82");
    private static final int COLOR_DANGER      = Color.parseColor("#C25C56");
    private static final int COLOR_WARN        = Color.parseColor("#E0A94D");
    private static final int COLOR_TEXT        = Color.parseColor("#ECE8DF");
    private static final int COLOR_TEXT_MUTED  = Color.parseColor("#8D8F99");
    private static final int COLOR_HINT        = Color.parseColor("#7A7D87");
    private static final int COLOR_ON_ACCENT   = Color.parseColor("#14161F");
    private static final int COLOR_CHIP_BG     = withAlpha(COLOR_ACCENT, 0x20);

    private static final int COLOR_DIALOG_BG    = Color.parseColor("#1D2029");
    private static final int COLOR_DIALOG_BG_TOP= Color.parseColor("#242835");
    private static final int COLOR_DIALOG_TITLE = Color.parseColor("#ECE8DF");
    private static final int COLOR_DIALOG_SUB   = Color.parseColor("#A7A9B2");
    private static final int COLOR_DIALOG_SUB2  = Color.parseColor("#7D7F89");
    private static final int COLOR_DIALOG_BODY  = Color.parseColor("#C6C8D0");
    private static final int COLOR_CTA          = Color.parseColor("#D8B36C");
    private static final int COLOR_OUTLINE      = Color.parseColor("#3A3D49");

    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;

    private final Context ctx;
    private final Callback callback;
    private final SharedPreferences save;
    private final SharedPreferences KEY;

    private EditText editUser, editPass;
    private LinearLayout userBox, passBox;
    private ImageView userIconView, passIconView;
    private FrameLayout userChip, passChip;
    private CustomCheck rememberCb, showCb;
    private Button loginBtn;
    private TextView statusTxt;
    private boolean loginInProgress = false;
    private boolean keyExpiredDialogShowing = false;

    private Typeface tfRegular, tfMedium, tfBold;

    public LoginHelper(Context context, Callback cb) {
        this.ctx = context;
        this.callback = cb;
        this.save = context.getSharedPreferences("save", Context.MODE_PRIVATE);
        this.KEY = context.getSharedPreferences("KEY", Context.MODE_PRIVATE);
        this.tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
        this.tfMedium  = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        this.tfBold    = Typeface.create("sans-serif", Typeface.BOLD);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    // ================================================================
    // Build login view  (transparent card, ash input fields, no scroll)
    // ================================================================
    public View buildView() {
        FrameLayout wrapper = new FrameLayout(ctx);
        wrapper.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.setPadding(0, 0, 0, 0);

        final LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        // Top padding small so "Welcome Back" sits closer to the top
        card.setPadding(dp(14), dp(4), dp(14), dp(6));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        // No background, no border — transparent so it blends with menu bg
        card.setBackgroundColor(Color.TRANSPARENT);
        root.addView(card);

        // ---- TEXT HEADER (tight to top, no logo, no brand) ----
        card.addView(makeSimpleHeader());

        // ---- USERNAME FIELD (no label above) ----
        userBox = makeFieldContainer();
        userChip = makeIconChip(FieldIcon.USER);
        userIconView = (ImageView) userChip.getChildAt(0);
        userBox.addView(userChip);

        editUser = makeInput();
        editUser.setHint("Username");
        editUser.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        editUser.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams ueLp = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f);
        editUser.setLayoutParams(ueLp);
        enableRichTextInteraction(editUser);
        userBox.addView(editUser);
        userBox.addView(makePasteButton(editUser));

        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(44));
        uLp.setMargins(0, 0, 0, dp(8));
        userBox.setLayoutParams(uLp);
        card.addView(userBox);

        // ---- PASSWORD FIELD (no label above) ----
        passBox = makeFieldContainer();
        passChip = makeIconChip(FieldIcon.LOCK);
        passIconView = (ImageView) passChip.getChildAt(0);
        passBox.addView(passChip);

        editPass = makeInput();
        editPass.setHint("Password or license key");
        editPass.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
        LinearLayout.LayoutParams peLp = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f);
        editPass.setLayoutParams(peLp);
        enableRichTextInteraction(editPass);
        passBox.addView(editPass);
        passBox.addView(makePasteButton(editPass));

        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(44));
        pLp.setMargins(0, 0, 0, dp(6));
        passBox.setLayoutParams(pLp);
        card.addView(passBox);

        // ---- ATTACH FIELD FOCUS HANDLERS ----
        attachFieldFocus(userBox, editUser, userIconView, userChip);
        attachFieldFocus(passBox, editPass, passIconView, passChip);

        // ---- IME actions ----
        editUser.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    editPass.requestFocus();
                    editPass.setSelection(editPass.getText().length());
                    forceShowKeyboard(editPass);
                    return true;
                }
                return false;
            }
        });

        editPass.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    hideKeyboard(editPass);
                    performLogin();
                    return true;
                }
                return false;
            }
        });

        // ---- CHECKBOXES ----
        LinearLayout cbRow = new LinearLayout(ctx);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbRow.setGravity(Gravity.CENTER_VERTICAL);
        cbRow.setPadding(0, dp(2), 0, dp(2));

        rememberCb = new CustomCheck("Remember", false);
        rememberCb.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        showCb = new CustomCheck("Show", false);
        showCb.setListener(new CheckListener() {
            @Override
            public void onChanged(boolean checked) {
                int sel = editPass.getSelectionStart();
                if (checked) {
                    editPass.setTransformationMethod(
                            android.text.method.HideReturnsTransformationMethod.getInstance());
                } else {
                    editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
                if (sel >= 0 && sel <= editPass.getText().length()) {
                    editPass.setSelection(sel);
                }
            }
        });

        rememberCb.setListener(new CheckListener() {
            @Override
            public void onChanged(boolean checked) {
                if (checked) {
                    save.edit().putString("edittext1", editUser.getText().toString()).apply();
                    save.edit().putString("edittext2", editPass.getText().toString()).apply();
                } else {
                    save.edit().remove("edittext1").apply();
                    save.edit().remove("edittext2").apply();
                }
            }
        });

        cbRow.addView(rememberCb);
        cbRow.addView(showCb);
        card.addView(cbRow);

        // ---- LOGIN BUTTON ----
        loginBtn = new Button(ctx);
        loginBtn.setText("SIGN IN");
        loginBtn.setAllCaps(false);
        loginBtn.setTextColor(COLOR_ON_ACCENT);
        loginBtn.setTextSize(12.5f);
        loginBtn.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            loginBtn.setLetterSpacing(0.14f);
        }

        GradientDrawable lb = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_ACCENT_HI, COLOR_ACCENT, COLOR_ACCENT_DEEP});
        lb.setCornerRadius(dp(13));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(0x30000000), lb, null);
            loginBtn.setBackground(ripple);
            loginBtn.setElevation(dp(4));
        } else {
            loginBtn.setBackground(lb);
        }
        loginBtn.setMinHeight(0);
        loginBtn.setMinimumHeight(0);
        loginBtn.setMinWidth(0);
        loginBtn.setMinimumWidth(0);
        loginBtn.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(46));
        bLp.setMargins(0, dp(10), 0, 0);
        loginBtn.setLayoutParams(bLp);
        card.addView(loginBtn);

        // ---- STATUS TEXT ----
        statusTxt = new TextView(ctx);
        statusTxt.setText("");
        statusTxt.setTextColor(COLOR_TEXT_MUTED);
        statusTxt.setTextSize(10f);
        statusTxt.setTypeface(tfMedium);
        statusTxt.setGravity(Gravity.CENTER);
        statusTxt.setSingleLine(true);
        statusTxt.setAlpha(0f);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        sLp.setMargins(0, dp(6), 0, 0);
        statusTxt.setLayoutParams(sLp);
        card.addView(statusTxt);

        // ---- LOGIN BUTTON LISTENER ----
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(0.975f).scaleY(0.975f).setDuration(100)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                loginBtn.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(260)
                                        .setInterpolator(new OvershootInterpolator(1.3f))
                                        .start();
                            }
                        }).start();
                hideKeyboard(editUser);
                hideKeyboard(editPass);
                performLogin();
            }
        });

        // ---- RESTORE REMEMBERED CREDENTIALS ----
        String u = save.getString("edittext1", "");
        String p = save.getString("edittext2", "");
        if (!u.isEmpty() && !p.isEmpty()) {
            editUser.setText(u);
            editPass.setText(p);
            rememberCb.setChecked(true);
        }

        wrapper.addView(root, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // ---- ENTRANCE ANIMATION ----
        card.setAlpha(0f);
        card.setTranslationY(dp(10));
        card.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                card.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                card.animate().alpha(1f).translationY(0f).setDuration(340)
                        .setInterpolator(new DecelerateInterpolator(1.6f)).start();
            }
        });

        return wrapper;
    }

    // ================================================================
    // Text-only header  — "Welcome Back" pushed to top, no logo
    // ================================================================
    private View makeSimpleHeader() {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        // very small margin so title hugs the top
        hLp.setMargins(0, 0, 0, dp(10));
        header.setLayoutParams(hLp);

        TextView title = new TextView(ctx);
        title.setText("Welcome Back");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(20f);
        title.setTypeface(tfBold);
        title.setGravity(Gravity.CENTER);
        header.addView(title);

        TextView subtitle = new TextView(ctx);
        subtitle.setText("Sign in to continue to your account");
        subtitle.setTextColor(COLOR_TEXT_MUTED);
        subtitle.setTextSize(11.5f);
        subtitle.setTypeface(tfRegular);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        sLp.setMargins(0, dp(3), 0, 0);
        subtitle.setLayoutParams(sLp);
        header.addView(subtitle);

        return header;
    }

    // ================================================================
    // Field builders — ash / dark gray input box
    // ================================================================
    private LinearLayout makeFieldContainer() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        // Solid ash-gray background
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_FIELD_BG);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), COLOR_FIELD_BORD);
        box.setBackground(bg);
        box.setClipToPadding(false);
        return box;
    }

    private FrameLayout makeIconChip(int iconType) {
        FrameLayout chip = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
        lp.setMargins(dp(7), 0, dp(4), 0);
        chip.setLayoutParams(lp);

        // No circular chip background — just a bare icon for a cleaner look
        chip.setBackgroundColor(Color.TRANSPARENT);

        ImageView iv = new ImageView(ctx);
        // Thinner icon: darker muted tone for lighter feel
        iv.setImageDrawable(new FieldIcon(iconType, COLOR_TEXT_MUTED));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.CENTER);
        iv.setLayoutParams(ivLp);
        chip.addView(iv);
        return chip;
    }

    private EditText makeInput() {
        EditText e = new EditText(ctx);
        e.setHintTextColor(COLOR_HINT);
        e.setTextColor(COLOR_TEXT);
        e.setTextSize(13f);
        e.setTypeface(tfRegular);
        e.setSingleLine(true);
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        e.setClickable(true);
        e.setCursorVisible(true);
        e.setBackground(null);
        e.setIncludeFontPadding(false);
        e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        e.setPadding(dp(2), 0, dp(10), 0);
        e.setHighlightColor(withAlpha(COLOR_ACCENT, 0x55));
        return e;
    }

    /**
     * Enable long-press cut / copy / paste / select-all.
     * Uses the default ActionMode so the system toolbar always appears,
     * and forces it to show all standard menu items.
     */
    private void enableRichTextInteraction(final EditText e) {
        e.setLongClickable(true);
        e.setTextIsSelectable(true);              // allow selecting text
        e.setCursorVisible(true);
        e.setFocusableInTouchMode(true);

        // Custom callback that keeps every default menu item
        // (Cut / Copy / Paste / Select all / Share etc.)
        e.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Return true → keep the default menu items intact
                return true;
            }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                // Force Show As Action so nothing collapses into overflow
                for (int i = 0; i < menu.size(); i++) {
                    MenuItem it = menu.getItem(i);
                    it.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM
                            | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
                }
                return true;
            }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                // Let the framework handle the action
                return false;
            }
            @Override public void onDestroyActionMode(ActionMode mode) { }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            e.setCustomInsertionActionModeCallback(new ActionMode.Callback() {
                @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return true;
                }
                @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return true;
                }
                @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }
                @Override public void onDestroyActionMode(ActionMode mode) { }
            });
        }
    }

    private ImageView makePasteButton(final EditText target) {
        final ImageView btn = new ImageView(ctx);
        btn.setImageDrawable(new PasteIcon(COLOR_TEXT_MUTED));
        btn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        btn.setPadding(dp(7), dp(7), dp(7), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(32));
        lp.setMargins(dp(2), 0, dp(6), 0);
        btn.setLayoutParams(lp);
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setContentDescription("Paste");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable mask = new GradientDrawable();
            mask.setShape(GradientDrawable.OVAL);
            mask.setColor(Color.WHITE);
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(withAlpha(COLOR_ACCENT, 0x3A)), null, mask);
            btn.setBackground(ripple);
        }

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasteFromClipboard(target);
                v.animate().scaleX(0.82f).scaleY(0.82f).setDuration(90)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                btn.animate().scaleX(1f).scaleY(1f).setDuration(180)
                                        .setInterpolator(new OvershootInterpolator(2.2f)).start();
                            }
                        }).start();
            }
        });
        return btn;
    }

    private void pasteFromClipboard(EditText target) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                setStatus("Clipboard is empty", COLOR_WARN);
                return;
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                setStatus("Clipboard is empty", COLOR_WARN);
                return;
            }
            CharSequence pasted = clip.getItemAt(0).coerceToText(ctx);
            if (pasted == null || pasted.length() == 0) {
                setStatus("Clipboard is empty", COLOR_WARN);
                return;
            }
            String text = pasted.toString().trim();
            target.setText(text);
            target.setSelection(target.getText().length());
            target.requestFocus();
            forceShowKeyboard(target);
        } catch (Exception e) {
            Log.e(TAG, "pasteFromClipboard: " + e);
        }
    }

    // ================================================================
    // Field focus animation + keyboard trigger
    // ================================================================
    private void attachFieldFocus(final LinearLayout box, final EditText et,
                                   final ImageView icon, final FrameLayout chip) {
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                final GradientDrawable bg = (GradientDrawable) box.getBackground();
                int fromColor = hasFocus ? COLOR_FIELD_BORD : COLOR_ACCENT;
                int toColor   = hasFocus ? COLOR_ACCENT      : COLOR_FIELD_BORD;

                ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
                va.setDuration(200);
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        bg.setStroke(dp(hasFocus ? 1.5f : 1f), (Integer) a.getAnimatedValue());
                    }
                });
                va.start();

                FieldIcon fi = (FieldIcon) icon.getDrawable();
                if (fi != null) {
                    fi.animateColor(hasFocus ? COLOR_ACCENT_HI : COLOR_TEXT_MUTED, 200);
                }
            }
        });

        et.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.requestFocus();
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            forceShowKeyboard(et);
                        }
                    }, 80);
                }
                return false;
            }
        });

        et.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        forceShowKeyboard(et);
                    }
                }, 80);
            }
        });
    }

    // ================================================================
    // Keyboard helpers
    // ================================================================
    private void forceShowKeyboard(final EditText et) {
        if (et == null) return;
        try {
            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return;
            et.requestFocus();
            if (et.getText().length() > 0) {
                et.setSelection(et.getText().length());
            }
            boolean shown = imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
            if (!shown) imm.showSoftInput(et, InputMethodManager.SHOW_FORCED);
        } catch (Exception e) {
            Log.e(TAG, "forceShowKeyboard: " + e);
        }
    }

    private void hideKeyboard(EditText et) {
        try {
            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && et != null) {
                imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    // ================================================================
    // Status message
    // ================================================================
    private void setStatus(final String msg, final int color) {
        if (statusTxt == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                statusTxt.animate().cancel();
                statusTxt.animate().alpha(0f).setDuration(120)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                statusTxt.setText(msg);
                                statusTxt.setTextColor(color);
                                statusTxt.animate().alpha(1f).setDuration(220).start();
                            }
                        }).start();
            }
        });
    }

    private String getVersionName() {
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ================================================================
    // Login flow
    // ================================================================
    private void performLogin() {
        if (loginInProgress) return;

        final String inputUser = editUser.getText().toString().trim().toLowerCase();
        final String inputPass = editPass.getText().toString().trim();

        if (TextUtils.isEmpty(inputUser) || TextUtils.isEmpty(inputPass)) {
            setStatus("Please fill in all fields", COLOR_WARN);
            return;
        }

        loginInProgress = true;
        loginBtn.setEnabled(false);
        loginBtn.setText("SIGNING IN...");
        setStatus("Verifying credentials...", COLOR_ACCENT_HI);

        save.edit().putString("edittext1", inputUser).apply();
        save.edit().putString("edittext2", inputPass).apply();

        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject matched = ModFirebase.fetchUserByUsername(inputUser);
                final String userJson = (matched == null) ? "" : matched.toString();

                final String resultJson = SecurityNative.verifyLogin(inputUser, inputPass, userJson);

                boolean ok = false;
                String reason = "network";
                String token = "", userOut = "", statusOut = "", expiryOut = "";
                try {
                    JSONObject r = new JSONObject(resultJson);
                    ok = r.optBoolean("ok", false);
                    reason = r.optString("reason", "unknown");
                    token = r.optString("token", "");
                    userOut = r.optString("user", "");
                    statusOut = r.optString("status", "");
                    expiryOut = r.optString("expiry", "");
                } catch (Exception e) {
                    ok = false;
                    reason = "network";
                }

                if (!ok) {
                    final String fReason = reason;
                    loginInProgress = false;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            loginBtn.setEnabled(true);
                            loginBtn.setText("SIGN IN");
                            if ("expired".equals(fReason)) {
                                setStatus("Key expired", COLOR_DANGER);
                                showKeyExpiredDialog();
                            } else if ("blocked".equals(fReason)) {
                                setStatus("Account blocked", COLOR_DANGER);
                                showKeyExpiredDialog();
                            } else if ("no_match".equals(fReason)
                                    || "invalid_credentials".equals(fReason)) {
                                setStatus("Invalid username or password", COLOR_DANGER);
                            } else if ("network".equals(fReason)) {
                                setStatus("Connection failed", COLOR_DANGER);
                            } else {
                                setStatus("Login failed", COLOR_DANGER);
                            }
                        }
                    });
                    return;
                }

                try {
                    KEY.edit().putString("User",     userOut).apply();
                    KEY.edit().putString("Status",   statusOut).apply();
                    KEY.edit().putString("expiry",   expiryOut).apply();
                    KEY.edit().putString("token",    token).apply();
                } catch (Exception ignored) { }

                loginInProgress = false;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("SIGN IN");
                        setStatus("Welcome back!", COLOR_SUCCESS);
                        Toast.makeText(ctx, "Login Success", Toast.LENGTH_SHORT).show();
                        checkUpdateAfterLogin();
                    }
                });
            }
        }).start();
    }

    private void checkUpdateAfterLogin() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject updateJson = ModFirebase.fetchUpdate();
                if (updateJson == null) {
                    proceedToMenu();
                    return;
                }
                try {
                    JSONObject up = updateJson.optJSONObject("up");
                    if (up == null) {
                        proceedToMenu();
                        return;
                    }
                    String latest = up.optString("version", "");
                    String msg = up.optString("message", "");
                    String current = getVersionName();
                    if (!TextUtils.isEmpty(latest) && !current.equals(latest)) {
                        final String fv = latest, fm = msg;
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateDialog(fv, fm);
                            }
                        });
                    } else {
                        proceedToMenu();
                    }
                } catch (Exception e) {
                    proceedToMenu();
                }
            }
        }).start();
    }

    private void proceedToMenu() {
        final String token = KEY.getString("token", "");
        final String user = KEY.getString("User", "");
        final String pass = save.getString("edittext2", "");
        final String expiry = KEY.getString("expiry", "");

        if (token == null || token.isEmpty()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    setStatus("Session invalid", COLOR_DANGER);
                }
            });
            return;
        }
        if (!SecurityNative.verifySessionToken(token, user, pass, expiry)) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    setStatus("Session tampered", COLOR_DANGER);
                }
            });
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) callback.onLoginSuccess();
            }
        });
    }

    // ================================================================
    // Shared dialog chrome
    // ================================================================
    private LinearLayout newDialogCard() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(18));
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_DIALOG_BG_TOP, COLOR_DIALOG_BG});
        cardBg.setCornerRadius(dp(18));
        cardBg.setStroke(dp(1), COLOR_BORDER);
        card.setBackground(cardBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(14));
        }
        return card;
    }

    private FrameLayout newDialogIconHolder(int mainColor, Drawable glyph) {
        FrameLayout iconHolder = new FrameLayout(ctx);
        int outer = dp(54);
        LinearLayout.LayoutParams ihLp = new LinearLayout.LayoutParams(outer, outer);
        ihLp.setMargins(0, 0, 0, dp(12));
        iconHolder.setLayoutParams(ihLp);

        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setColor(withAlpha(mainColor, 0x2A));
        View glowView = new View(ctx);
        glowView.setBackground(glow);
        iconHolder.addView(glowView, new FrameLayout.LayoutParams(outer, outer, Gravity.CENTER));

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(mainColor);
        View circleView = new View(ctx);
        circleView.setBackground(circle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            circleView.setElevation(dp(5));
        }
        iconHolder.addView(circleView, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));

        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(glyph);
        iconHolder.addView(icon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));

        return iconHolder;
    }

    private Button newPrimaryDialogButton(String text) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(COLOR_ON_ACCENT);
        btn.setTextSize(12.5f);
        btn.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btn.setLetterSpacing(0.08f);
        }
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_ACCENT_HI, COLOR_CTA, COLOR_ACCENT_DEEP});
        bg.setCornerRadius(dp(24));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(0x2A000000), bg, null);
            btn.setBackground(ripple);
            btn.setElevation(dp(3));
        } else {
            btn.setBackground(bg);
        }
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(44));
        lp.setMargins(0, dp(16), 0, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button newSecondaryDialogButton(String text) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(COLOR_DIALOG_SUB);
        btn.setTextSize(11.5f);
        btn.setTypeface(tfMedium);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(dp(1.5f), COLOR_OUTLINE);
        bg.setCornerRadius(dp(24));
        btn.setBackground(bg);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(40));
        lp.setMargins(0, dp(8), 0, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void presentPremiumDialog(final android.app.AlertDialog[] ref, final LinearLayout card,
                                       boolean cancelable,
                                       final android.content.DialogInterface.OnCancelListener onCancel) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        final int screenW = dm.widthPixels;
        final int screenH = dm.heightPixels;

        MaxHeightScrollView scroll = new MaxHeightScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setMaxHeightPx((int) (screenH * 0.80f));
        scroll.addView(card, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        FrameLayout dialogRoot = new FrameLayout(ctx);
        int hPad = dp(16);
        dialogRoot.setPadding(hPad, hPad, hPad, hPad);
        dialogRoot.addView(scroll, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        card.setAlpha(0f);
        card.setScaleX(0.9f);
        card.setScaleY(0.9f);

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(ctx);
        b.setView(dialogRoot);
        final android.app.AlertDialog d = b.create();
        d.setCanceledOnTouchOutside(false);
        d.setCancelable(cancelable);
        if (cancelable && onCancel != null) {
            d.setOnCancelListener(onCancel);
        }
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            if (Build.VERSION.SDK_INT >= 26) d.getWindow().setType(2038);
            else d.getWindow().setType(2002);
        }
        ref[0] = d;
        d.show();

        if (d.getWindow() != null) {
            int maxWidthPx = dp(330);
            int wanted = Math.min(maxWidthPx, (int) (screenW * 0.9f));
            WindowManager.LayoutParams lp = d.getWindow().getAttributes();
            lp.width = wanted;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            d.getWindow().setAttributes(lp);
        }

        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(230)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
    }

    // ================================================================
    // Update dialog
    // ================================================================
    private void showUpdateDialog(String version, String msg) {
        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        final LinearLayout card = newDialogCard();

        card.addView(newDialogIconHolder(COLOR_CTA, new UpdateIcon(COLOR_ON_ACCENT)));

        TextView title = new TextView(ctx);
        title.setText("NEW UPDATE");
        title.setTextColor(COLOR_DIALOG_TITLE);
        title.setTextSize(15f);
        title.setTypeface(tfBold);
        title.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            title.setLetterSpacing(0.06f);
        }
        card.addView(title);

        TextView versionView = new TextView(ctx);
        versionView.setText("Version " + version + " is available now");
        versionView.setTextColor(COLOR_DIALOG_SUB);
        versionView.setTextSize(11.5f);
        versionView.setTypeface(tfRegular);
        versionView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        vLp.setMargins(0, dp(5), 0, 0);
        versionView.setLayoutParams(vLp);
        card.addView(versionView);

        TextView currentView = new TextView(ctx);
        currentView.setText("Your version: " + getVersionName());
        currentView.setTextColor(COLOR_DIALOG_SUB2);
        currentView.setTextSize(10f);
        currentView.setTypeface(tfRegular);
        currentView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams curLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        curLp.setMargins(0, dp(2), 0, 0);
        currentView.setLayoutParams(curLp);
        card.addView(currentView);

        if (msg != null && !msg.trim().isEmpty()) {
            TextView msgView = new TextView(ctx);
            msgView.setText(msg);
            msgView.setTextColor(COLOR_DIALOG_BODY);
            msgView.setTextSize(10.5f);
            msgView.setTypeface(tfRegular);
            msgView.setGravity(Gravity.CENTER);
            msgView.setMaxLines(4);
            msgView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            mLp.setMargins(0, dp(10), 0, 0);
            msgView.setLayoutParams(mLp);
            card.addView(msgView);
        }

        Button updateBtn = newPrimaryDialogButton("UPDATE NOW");
        card.addView(updateBtn);

        Button continueBtn = newSecondaryDialogButton("Continue for now");
        card.addView(continueBtn);

        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/kayesahmmedpro"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception e) {
                }
            }
        });

        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
                proceedToMenu();
            }
        });

        presentPremiumDialog(ref, card, false, null);
    }

    // ================================================================
    // Key expired dialog
    // ================================================================
    private void showKeyExpiredDialog() {
        if (keyExpiredDialogShowing) return;
        keyExpiredDialogShowing = true;

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        final LinearLayout card = newDialogCard();

        card.addView(newDialogIconHolder(COLOR_DANGER, new LockIcon(Color.WHITE)));

        TextView title = new TextView(ctx);
        title.setText("ACCESS EXPIRED");
        title.setTextColor(COLOR_DIALOG_TITLE);
        title.setTextSize(15f);
        title.setTypeface(tfBold);
        title.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            title.setLetterSpacing(0.06f);
        }
        card.addView(title);

        TextView body = new TextView(ctx);
        body.setText("Your subscription has ended or the account is blocked.\n\nContact the seller to renew access.");
        body.setTextColor(COLOR_DIALOG_BODY);
        body.setTextSize(11f);
        body.setTypeface(tfRegular);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        bLp.setMargins(0, dp(10), 0, 0);
        body.setLayoutParams(bLp);
        card.addView(body);

        Button contact = newPrimaryDialogButton("CONTACT SELLER");
        card.addView(contact);

        contact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                keyExpiredDialogShowing = false;
                if (ref[0] != null) ref[0].dismiss();
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/kayesahmmedpro"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception e) {
                }
            }
        });

        presentPremiumDialog(ref, card, true, new android.content.DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(android.content.DialogInterface di) {
                keyExpiredDialogShowing = false;
            }
        });
    }

    // ================================================================
    // MaxHeightScrollView
    // ================================================================
    private static class MaxHeightScrollView extends ScrollView {
        private int maxHeightPx = Integer.MAX_VALUE;

        MaxHeightScrollView(Context c) {
            super(c);
        }

        void setMaxHeightPx(int px) {
            this.maxHeightPx = px;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int hSpec = heightMeasureSpec;
            if (maxHeightPx > 0 && maxHeightPx < Integer.MAX_VALUE) {
                int size = View.MeasureSpec.getSize(heightMeasureSpec);
                int mode = View.MeasureSpec.getMode(heightMeasureSpec);
                if (mode == View.MeasureSpec.UNSPECIFIED || size > maxHeightPx) {
                    hSpec = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST);
                }
            }
            super.onMeasure(widthMeasureSpec, hSpec);
        }
    }

    // ================================================================
    // CustomCheck
    // ================================================================
    private class CustomCheck extends LinearLayout {
        private final GradientDrawable boxBg;
        private final View checkmark;
        private boolean checked;
        private CheckListener listener;

        CustomCheck(String label, boolean initial) {
            super(ctx);
            this.checked = initial;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0, 0, 0, 0);

            FrameLayout box = new FrameLayout(ctx);
            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(dp(16), dp(16));
            boxLp.setMargins(0, 0, dp(8), 0);
            box.setLayoutParams(boxLp);

            boxBg = new GradientDrawable();
            boxBg.setCornerRadius(dp(4));
            boxBg.setStroke(dp(1.5f), initial ? COLOR_ACCENT : COLOR_FIELD_BORD);
            boxBg.setColor(initial ? withAlpha(COLOR_ACCENT, 0x22) : Color.TRANSPARENT);
            box.setBackground(boxBg);

            checkmark = new View(ctx);
            FrameLayout.LayoutParams cmLp = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER);
            checkmark.setLayoutParams(cmLp);
            GradientDrawable cmBg = new GradientDrawable();
            cmBg.setColor(COLOR_ACCENT);
            cmBg.setCornerRadius(dp(2));
            checkmark.setBackground(cmBg);
            checkmark.setVisibility(initial ? View.VISIBLE : View.GONE);
            box.addView(checkmark);

            TextView labelView = new TextView(ctx);
            labelView.setText(label);
            labelView.setTextColor(COLOR_TEXT_MUTED);
            labelView.setTextSize(10f);
            labelView.setTypeface(tfRegular);

            addView(box);
            addView(labelView);

            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle();
                }
            });
        }

        void setListener(CheckListener l) {
            this.listener = l;
        }

        void setChecked(boolean value) {
            if (this.checked != value) toggle();
        }

        boolean isChecked() {
            return checked;
        }

        private void toggle() {
            checked = !checked;
            checkmark.setVisibility(checked ? View.VISIBLE : View.GONE);
            boxBg.setStroke(dp(1.5f), checked ? COLOR_ACCENT : COLOR_FIELD_BORD);
            boxBg.setColor(checked ? withAlpha(COLOR_ACCENT, 0x22) : Color.TRANSPARENT);
            if (listener != null) listener.onChanged(checked);
        }
    }

    // ================================================================
    // Utility
    // ================================================================
    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    // ================================================================
    // FieldIcon — thinner stroke (1.5 instead of 2.0)
    // ================================================================
    private static class FieldIcon extends Drawable {
        static final int USER = 0;
        static final int LOCK = 1;

        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private int currentColor;

        FieldIcon(int type, int color) {
            this.type = type;
            this.currentColor = color;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void animateColor(int toColor, int durationMs) {
            ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), currentColor, toColor);
            va.setDuration(durationMs);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    currentColor = (Integer) a.getAnimatedValue();
                    paint.setColor(currentColor);
                    invalidateSelf();
                }
            });
            va.start();
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;
            // ↓ Thinner icons (was 2.0)
            paint.setStrokeWidth(1.5f * s);

            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f,
                             b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);
            path.reset();

            if (type == USER) {
                canvas.drawCircle(12f, 8f, 3.9f, paint);
                path.moveTo(4.5f, 21f);
                path.cubicTo(4.5f, 15.5f, 8f, 13.8f, 12f, 13.8f);
                path.cubicTo(16f, 13.8f, 19.5f, 15.5f, 19.5f, 21f);
                canvas.drawPath(path, paint);
            } else {
                rect.set(5f, 10.5f, 19f, 21f);
                canvas.drawRoundRect(rect, 2.4f, 2.4f, paint);
                path.moveTo(8.5f, 10.5f);
                path.lineTo(8.5f, 7.5f);
                path.cubicTo(8.5f, 5.0f, 10.2f, 3f, 12f, 3f);
                path.cubicTo(13.8f, 3f, 15.5f, 5.0f, 15.5f, 7.5f);
                path.lineTo(15.5f, 10.5f);
                canvas.drawPath(path, paint);
                // Smaller keyhole so the icon feels lighter
                Paint.Style prevStyle = paint.getStyle();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12f, 15.2f, 1.2f, paint);
                path.reset();
                path.moveTo(12f, 15.9f);
                path.lineTo(12f, 17.6f);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawPath(path, paint);
                paint.setStyle(prevStyle);
            }
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================
    // PasteIcon — thinner stroke (1.5)
    // ================================================================
    private static class PasteIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        PasteIcon(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;
            // ↓ Thinner (was 1.8)
            paint.setStrokeWidth(1.4f * s);

            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f,
                             b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);

            rect.set(6f, 5.5f, 18f, 20.5f);
            canvas.drawRoundRect(rect, 2.4f, 2.4f, paint);

            rect.set(9.5f, 3.5f, 14.5f, 6.5f);
            canvas.drawRoundRect(rect, 1.4f, 1.4f, paint);

            path.reset();
            path.moveTo(9f, 11f);
            path.lineTo(15f, 11f);
            canvas.drawPath(path, paint);

            path.reset();
            path.moveTo(9f, 14.5f);
            path.lineTo(13f, 14.5f);
            canvas.drawPath(path, paint);

            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================
    // UpdateIcon
    // ================================================================
    private static class UpdateIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        UpdateIcon(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;
            paint.setStrokeWidth(2.2f * s);

            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f,
                             b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);
            path.reset();

            path.moveTo(12f, 3f);
            path.lineTo(12f, 15f);
            canvas.drawPath(path, paint);

            path.reset();
            path.moveTo(6f, 10f);
            path.lineTo(12f, 16f);
            path.lineTo(18f, 10f);
            canvas.drawPath(path, paint);

            path.reset();
            path.moveTo(4f, 20.5f);
            path.lineTo(20f, 20.5f);
            canvas.drawPath(path, paint);

            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================
    // LockIcon
    // ================================================================
    private static class LockIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        LockIcon(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;
            paint.setStrokeWidth(2.2f * s);

            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f,
                             b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);
            path.reset();

            rect.set(5f, 10.5f, 19f, 21f);
            canvas.drawRoundRect(rect, 2f, 2f, paint);
            path.moveTo(8.5f, 10.5f);
            path.lineTo(8.5f, 7.5f);
            path.cubicTo(8.5f, 5.0f, 10.2f, 3f, 12f, 3f);
            path.cubicTo(13.8f, 3f, 15.5f, 5.0f, 15.5f, 7.5f);
            path.lineTo(15.5f, 10.5f);
            canvas.drawPath(path, paint);
            canvas.drawCircle(12f, 15.5f, 1.2f, paint);

            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}