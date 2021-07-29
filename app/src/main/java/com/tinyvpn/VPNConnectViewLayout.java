package com.tinyvpn;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;

class VPNConnectViewLayout extends FrameLayout {
    public static final int BUTTON_CONNECTING = 1;
    public static final int BUTTON_CONNECTED = 2;
    public static final int BUTTON_DISCONNECTED = 3;

    private Context mContext;
    // 链接的view
    private VPNConnectView vpnConnectView;
    // 旋转动画的图标
    private ImageView imageView;
    // 旋转动画
    private Animation rotateAnim;

    public VPNConnectViewLayout(Context context) {
        super(context);
        init(context);
    }


    public VPNConnectViewLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VPNConnectViewLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        this.mContext = context;
        vpnConnectView = new VPNConnectView(context);
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.gravity = Gravity.CENTER;
        vpnConnectView.setLayoutParams(layoutParams);
        addView(vpnConnectView);

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.round_point);
        LayoutParams progress = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        progress.leftMargin = (int) dp2px(12.0f);
        progress.rightMargin = (int) dp2px(12.0f);
        layoutParams.gravity = Gravity.CENTER;
        imageView.setLayoutParams(progress);
        rotateAnim = AnimationUtils.loadAnimation(mContext, R.anim.anim_rotate_vpn);

        setVPNConnectMode(BUTTON_DISCONNECTED);
        addView(imageView);
    }

    public void setVPNConnectMode(int mode) {
        vpnConnectView.setVPNConnectMode(mode);
        switch (mode) {
            case BUTTON_DISCONNECTED:
                imageView.setVisibility(GONE);
                imageView.clearAnimation();
                break;
            case BUTTON_CONNECTING:
                imageView.setVisibility(VISIBLE);
                imageView.startAnimation(rotateAnim);
                break;
            case BUTTON_CONNECTED:
                imageView.setVisibility(GONE);
                imageView.clearAnimation();
                break;
        }
    }

    public void setVpnConnectIsBasic(boolean isBasic) {
        vpnConnectView.setVpnConnectIsBasic(isBasic);
    }

    public void setConnectionListener(VPNConnectView.ConnectionListener listener) {
        vpnConnectView.setConnectionListener(listener);
    }

    private float dp2px(float dp) {
        final float scale = getResources().getDisplayMetrics().density;
        return dp * scale + 0.5f;
    }
}
