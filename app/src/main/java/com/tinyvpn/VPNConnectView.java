package com.tinyvpn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

class VPNConnectView extends View {
    public static final int BUTTON_CONNECTING = 1;
    public static final int BUTTON_CONNECTED = 2;
    public static final int BUTTON_DISCONNECTED = 3;

    // 当前的模式
    private int currentMode = BUTTON_DISCONNECTED;
    // view的最终大小
    private int mMeasureSize;
    // 中心点
    private int mCenter;
    // 是否是basic模式
    private boolean mIsBasic = true;

    private BgCircle bgCircle;
    private ConnectCircle connectCircle;
    private static final String TAG = "aaaaa";

    public VPNConnectView(Context context) {
        super(context);
        init();
    }

    public VPNConnectView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VPNConnectView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgCircle = new BgCircle();
        bgCircle.init();
        connectCircle = new ConnectCircle();
        connectCircle.init();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = 800;
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        if (widthMode == MeasureSpec.EXACTLY) {
            mMeasureSize = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            mMeasureSize = Math.min(desiredWidth, widthSize);
        } else {
            //Be whatever you want
            mMeasureSize = desiredWidth;
        }
        mCenter = mMeasureSize / 2;
        bgCircle.measure();
        connectCircle.measure();
        setMeasuredDimension(mMeasureSize, mMeasureSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        bgCircle.draw(canvas);
        connectCircle.draw(canvas);

    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.i(TAG, "onTouchEvent: ACTION_DOWN");
                break;
            case MotionEvent.ACTION_UP:
                Log.i(TAG, "onTouchEvent: ACTION_UP");
                switch (currentMode) {
                    case BUTTON_DISCONNECTED:
                        if (connectionListener != null)
                            connectionListener.connect();
                        break;
                    case BUTTON_CONNECTING:
                        if (connectionListener != null)
                            connectionListener.cancel();
                        break;
                    case BUTTON_CONNECTED:
                        if (connectionListener != null)
                            connectionListener.disConnect();
                        break;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                Log.i(TAG, "onTouchEvent: ACTION_MOVE");
                break;
        }
        return true;
    }

    public void setVPNConnectMode(int mode) {
        this.currentMode = mode;
        invalidate();
    }

    public void setVpnConnectIsBasic(boolean isBasic) {
        this.mIsBasic = isBasic;
        invalidate();
    }

    /**
     * 背景
     */
    private class BgCircle {
        private Paint paint;
        private float radius;
        private float mExternalDottedLineRadius;
        private float mInsideDottedLineRadius;
        // 刻度段宽度
        private float mArcWidth;
        // 刻度线段长度
        private float mDottedLineWidth;
        private int dottedLineCount = 80;

        private void init() {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.TRANSPARENT);
//            paint.setStrokeWidth(20);
            paint.setStyle(Paint.Style.FILL);
        }

        private void measure() {
            radius = mMeasureSize / 2;
            mArcWidth = dp2px(2.0f);
            mDottedLineWidth = dp2px(8.0f);

            // 内部虚线的外部半径
            mExternalDottedLineRadius = radius - mArcWidth / 2;
            // 内部虚线的内部半径
            mInsideDottedLineRadius = mExternalDottedLineRadius - mDottedLineWidth;
        }

        private void draw(Canvas canvas) {
            switch (currentMode) {
                case BUTTON_DISCONNECTED:
                    // 未链接
//                    paint.setColor(Color.parseColor("#80ffffff"));
//                    canvas.drawCircle(mCenter, mCenter, radius - 10, paint);
                    break;
                case BUTTON_CONNECTING:
                    // 正在连接
                    paint.setColor(Color.TRANSPARENT);
                    canvas.drawCircle(mCenter, mCenter, radius, paint);
                    drawDottedLineArc(canvas);
                    break;
                case BUTTON_CONNECTED:
                    // 已连接
                    //paint.setColor(Color.parseColor("#14ffffff"));
                        paint.setColor(getResources().getColor(R.color.blue_light));
                    canvas.drawCircle(mCenter, mCenter, radius - 10, paint);
                    break;
            }
        }

        private void drawDottedLineArc(Canvas canvas) {
            paint.setColor(Color.BLUE);
            // 360 * Math.PI / 180
            float evenryDegrees = (float) (2.0f * Math.PI / dottedLineCount);

//            float startDegress = (float) (135 * Math.PI / 180);
//            float endDegress = (float) (225 * Math.PI / 180);

            for (int i = 0; i < dottedLineCount; i++) {
                float degrees = i * evenryDegrees;
                // 过滤底部90度的弧长
//                if (degrees > startDegress && degrees < endDegress) {
//                    continue;
//                }

                float startX = mCenter + (float) Math.sin(degrees) * mInsideDottedLineRadius;
                float startY = mCenter - (float) Math.cos(degrees) * mInsideDottedLineRadius;

                float stopX = mCenter + (float) Math.sin(degrees) * mExternalDottedLineRadius;
                float stopY = mCenter - (float) Math.cos(degrees) * mExternalDottedLineRadius;


                canvas.drawLine(startX, startY, stopX, stopY, paint);
            }
        }
    }


    /**
     * 连接
     */
    private class ConnectCircle {


        private Paint mPaint;
        private Paint textPaint;
        private float radius;
        private int textColor;
        private int arcColor;

        private void init() {
            arcColor = getResources().getColor(R.color.sky_text_blue);
            textColor = getResources().getColor(R.color.sky_text_blue);

            mPaint = new Paint();
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(textColor);
            textPaint.setTextSize(dp2px(20.0F));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }


        private void measure() {
            radius = mMeasureSize / 2 - dp2px(25.0F);
        }

        private void draw(Canvas canvas) {
            switch (currentMode) {
                case BUTTON_DISCONNECTED:
                    // 未链接
                    if (mIsBasic) {
                        //resetPaint(Color.parseColor("#14ffffff"), Paint.Style.STROKE, dp2px(8.0f));
                        resetPaint(getResources().getColor(R.color.blue_light), Paint.Style.STROKE, dp2px(8.0f));
                        canvas.drawCircle(mCenter, mCenter, radius, mPaint);

                        resetPaint(Color.WHITE, Paint.Style.FILL, 0);
                        canvas.drawCircle(mCenter, mCenter, radius - dp2px(3.0f), mPaint);

                        resetPaint(arcColor, Paint.Style.STROKE, dp2px(2.0f));
                        canvas.drawCircle(mCenter, mCenter, radius - dp2px(8.0f), mPaint);

                        textPaint.setColor(getResources().getColor(R.color.sky_text_blue));
                        canvas.drawText(getResources().getString(R.string.sky_connect), mCenter, mCenter + dp2px(8.0f), textPaint);
                    } else {
                        resetPaint(Color.parseColor("#e7b613"), Paint.Style.FILL, 0);
                        SweepGradient sweepGradient = new SweepGradient(0, 0, Color.parseColor("#f2c134"), Color.parseColor("#e7b613"));
                        mPaint.setShader(sweepGradient);
                        canvas.drawCircle(mCenter, mCenter, radius, mPaint);

                        resetPaint(Color.WHITE, Paint.Style.STROKE, dp2px(4.0f));
                        canvas.drawCircle(mCenter, mCenter, radius - dp2px(8.0f), mPaint);

                        textPaint.setColor(Color.WHITE);
                        canvas.drawText(getResources().getString(R.string.sky_connect), mCenter, mCenter + dp2px(8.0f), textPaint);
                    }
                    // 正在连接
                    break;
                case BUTTON_CONNECTING:
                    if (mIsBasic) {
                        //resetPaint(Color.parseColor("#14ffffff"), Paint.Style.STROKE, dp2px(8.0f));
                        resetPaint(getResources().getColor(R.color.blue_light), Paint.Style.STROKE, dp2px(8.0f));
                        canvas.drawCircle(mCenter, mCenter, radius, mPaint);

                        resetPaint(Color.WHITE, Paint.Style.FILL, 0);
                        canvas.drawCircle(mCenter, mCenter, radius - dp2px(3.0f), mPaint);

                        resetPaint(arcColor, Paint.Style.STROKE, dp2px(2.0f));
                        canvas.drawCircle(mCenter, mCenter, radius - dp2px(8.0f), mPaint);

                        textPaint.setColor(getResources().getColor(R.color.sky_text_blue));
                        canvas.drawText(getResources().getString(R.string.sky_cancel), mCenter, mCenter + dp2px(8.0f), textPaint);
                    } else {
                        resetPaint(Color.parseColor("#e7b613"), Paint.Style.FILL, 0);
                        SweepGradient sweepGradient = new SweepGradient(0, 0, Color.parseColor("#f2c134"), Color.parseColor("#e7b613"));
                        mPaint.setShader(sweepGradient);
                        canvas.drawCircle(mCenter, mCenter, radius, mPaint);

                        resetPaint(Color.WHITE, Paint.Style.STROKE, dp2px(4.0f));
                        canvas.drawCircle(mCenter, mCenter, radius - dp2px(8.0f), mPaint);

                        textPaint.setColor(Color.WHITE);
                        canvas.drawText(getResources().getString(R.string.sky_cancel), mCenter, mCenter + dp2px(8.0f), textPaint);
                    }
                    break;
                case BUTTON_CONNECTED:
                    // 已连接
                    resetPaint(Color.WHITE, Paint.Style.STROKE, dp2px(3));
                    canvas.drawCircle(mCenter, mCenter, radius, mPaint);
                    resetPaint(getResources().getColor(R.color.sky_text_blue), Paint.Style.FILL, 0);
                    //resetPaint(getResources().getColor(R.color.sky_green), Paint.Style.FILL, 0);

                    //canvas.drawCircle(mCenter, mCenter, radius - dp2px(3), mPaint);
                    canvas.drawCircle(mCenter, mCenter, radius, mPaint);

                    textPaint.setColor(Color.WHITE);
                    canvas.drawText(getResources().getString(R.string.sky_disconncet), mCenter, mCenter + dp2px(8.0f), textPaint);
                    break;
            }
        }

        private void resetPaint(int color, Paint.Style style, float strokeWidth) {
            mPaint.reset();
            mPaint.setAntiAlias(true);
            mPaint.setColor(color);
            mPaint.setStrokeWidth(strokeWidth);
            mPaint.setStyle(style);
        }
    }

    private float dp2px(float dp) {
        final float scale = getResources().getDisplayMetrics().density;
        return dp * scale + 0.5f;
    }

    private ConnectionListener connectionListener;

    public void setConnectionListener(ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    public interface ConnectionListener {
        void connect();

        void cancel();

        void disConnect();
    }
}
