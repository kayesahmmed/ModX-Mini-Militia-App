package com.android.support;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.view.View;

public class ESPView extends View {

    private Paint strokePaint;
    private Paint boxPaint;
    private Paint textPaint;
    private Paint fillPaint;
    private int FrameDelay = 16; // ~60 FPS

    public ESPView(Context context) {
        super(context);

        strokePaint = new Paint();
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setAntiAlias(true);

        boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setAntiAlias(true);

        fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);

        setFocusableInTouchMode(false);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);

                // ~60 FPS invalidate loop — MUST be inside Handler.postDelayed
                    final Handler frameHandler = new Handler();
                    frameHandler.postDelayed(new Runnable() {
                @Override
            public void run() {
    invalidate();
    frameHandler.postDelayed(this, FrameDelay);
    }
    }, FrameDelay);
        }

        @Override
    protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    Menu.Draw(this, canvas); // native call
    }

                         // ===== Called from native Draw() =====

        public void DrawLine(Canvas cvs, int a, int r, int g, int b,
        float strokewidth, float fromX, float fromY,
    float toX, float toY) {
    strokePaint.setColor(Color.argb(a, r, g, b));
    strokePaint.setStrokeWidth(strokewidth);
                         cvs.drawLine(fromX, fromY, toX, toY, strokePaint);
        }

        public void DrawRect(Canvas cvs, int a, int r, int g, int b,
    float strokewidth, float x, float y, float w, float h) {
    boxPaint.setColor(Color.argb(a, r, g, b));
    boxPaint.setStrokeWidth(strokewidth);
                               cvs.drawRect(x, y, x + w, y + h, boxPaint);
        }

    public void DrawFilledRect(Canvas cvs, int a, int r, int g, int b,
    float x, float y, float w, float h) {
    fillPaint.setColor(Color.argb(a, r, g, b));
                         cvs.drawRect(x, y, x + w, y + h, fillPaint);
        }

        public void DrawText(Canvas cvs, String text, float x, float y,
    int a, int r, int g, int b, float size) {
    textPaint.setColor(Color.argb(a, r, g, b));
    textPaint.setTextSize(size);
                           cvs.drawText(text, x, y, textPaint);
        }

        public void DrawCircle(Canvas cvs, int a, int r, int g, int b,
    float strokewidth, float cx, float cy, float radius) {
strokePaint.setColor(Color.argb(a, r, g, b));
        strokePaint.setStrokeWidth(strokewidth);
        cvs.drawCircle(cx, cy, radius, strokePaint);
    }
}
