package com.tinyvpn;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "MainActivity";
    static Context mContext;

    //static Switch swiConnect;
    static Button btnLogin;
    static Button btnSubStart;
    static Button btnSubQuery;
    static Button btnSubLaunch;
    static EditText editname;
    static EditText editpasswd;

    //static TextView txtLog;
    static TextView txtUserStatus;
    static TextView txtToday;
    static TextView txtThisMonth;
    private SharedHelper sh;
    static int premium;
    //private SocketChannel webTunnel;
    static int out_of_quota = 0;
    static String g_androidId;
    static int g_day_limit=0;
    static int g_month_limit=0;
    static MyPurchasesUpdatedListener purchaseListener = new MyPurchasesUpdatedListener();

    static VPNConnectViewLayout vpnConnectViewLayout;
    //有没有点击过连接按钮，用来处理第一次进来的时候vpn下方的文字显示
    public static boolean isClickConnect;
    //当前主界面Btn的状态
    private static int mState;
    //判断是否是通过点击Connect按钮进行的设备激活
    private boolean isClickConnectActivate;
    //判断是否正在通过点击Connect进行激活(状态)
    private boolean isClickConnectActivating;

    public static final int BUTTON_CONNECTING = 1;
    public static final int BUTTON_CONNECTED = 2;
    public static final int BUTTON_DISCONNECTED = 3;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
    static Handler handler = new Handler() {
        public void show_traffic(int d, int m) {  // 100 M
            DecimalFormat df = new DecimalFormat("###,###");
            out_of_quota = 0;
            if (premium < 2 && g_day_limit != 0 && d > g_day_limit) {
                txtToday.setText(df.format(d) + " kB. No enough quota, pleaes upgrade to premium user.");
                //MyVPNService.isRun = false;
                if (MyVPNService.isRun == true) {
                    Intent intentStart = new Intent(mContext, MyVPNService.class);
                    intentStart.setAction(MyVPNService.ACTION_DISCONNECT);
                    mContext.startService(intentStart);
                }
                out_of_quota = 1;
            } else {
                if (premium < 2)
                    txtToday.setText(df.format(d) + " kB / " + df.format(g_day_limit) + " kB");
                else
                    txtToday.setText(df.format(d) + " kB");
            }
            if (premium >= 2 && g_month_limit != 0 && m > g_month_limit) {  // 10 G
                txtThisMonth.setText(df.format(m) + " kB. No enough quota for premium user.");
//                MyVPNService.isRun = false;
                if (MyVPNService.isRun == true) {
                    Intent intentStart = new Intent(mContext, MyVPNService.class);
                    intentStart.setAction(MyVPNService.ACTION_DISCONNECT);
                    mContext.startService(intentStart);
                }
                out_of_quota = 1;
            } else {
                if (premium >= 2)
                    txtThisMonth.setText(df.format(m) + " kB / " + df.format(g_month_limit) + " kB");
                else
                    txtThisMonth.setText(df.format(m) + " kB");
            }
        }
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0) {  // connect fail
                //swiConnect.setChecked(false);
                //swiConnect.setText("VPN OFF");

                setButtonState(BUTTON_DISCONNECTED);
                g_day_limit = 0;
                g_month_limit = 0;
            } else if (msg.what == 1) {  // return login info
                int ret1,ret2;
                Bundle data = msg.getData();
                ret1 = data.getInt("ret1");
                ret2 = data.getInt("ret2");
                if (ret1 == 0) {
                    if (ret2 == 1) {
                        txtUserStatus.setText("basic user login ok.");
                    } else if (ret2==2) {
                        txtUserStatus.setText("premium user login ok.");
                    }
                    premium = ret2;
                    editname.setEnabled(false);
                    editpasswd.setEnabled(false);
                    btnLogin.setText("Logout");
                    MyVPNService.userName = editname.getText().toString();
                    MyVPNService.userPassword = editpasswd.getText().toString();
                    btnSubLaunch.setEnabled(true);
                } else {
                    txtUserStatus.setText("login fail.");
                    editname.setText("");
                    editpasswd.setText("");
                }
                g_day_limit = data.getInt("day_limit");
                g_month_limit = data.getInt("month_limit");
                MyVPNService.day_traffic = data.getInt("day_traffic");
                MyVPNService.month_traffic = data.getInt("month_traffic");
                //MyVPNService.current_traffic = 0;
                show_traffic(data.getInt("day_traffic"), data.getInt("month_traffic"));
            } else if (msg.what == 2) {  //return traffic info from web
                Bundle data = msg.getData();
                g_day_limit = data.getInt("day_limit");
                g_month_limit = data.getInt("month_limit");
                show_traffic(data.getInt("day_traffic"), data.getInt("month_traffic"));
            }else if (msg.what == 3) {   // update traffic info
                Bundle data = msg.getData();
                show_traffic(data.getInt("day_traffic"), data.getInt("month_traffic"));
            }else if (msg.what == 4) {  // return billing info
                purchaseListener.launch();
            }else if (msg.what == 5) {  // connect successful
                //swiConnect.setChecked(true);
                //swiConnect.setText("VPN ON");
                setButtonState(BUTTON_CONNECTED);
            }

        }
    };

    public static String getCountryZipCode(Context context)
    {
        String CountryZipCode = "";
        Locale locale = context.getResources().getConfiguration().locale;
        CountryZipCode = locale.getCountry();

        return CountryZipCode;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (premium != 0) {
            editname.setEnabled(false);
            editpasswd.setEnabled(false);
            btnLogin.setText("Logout");
        } else {
            editname.setEnabled(true);
            editpasswd.setEnabled(true);
            btnLogin.setText("Login");
        }
        if (MyVPNService.isRun) {
            //swiConnect.setChecked(true);
            //swiConnect.setText("VPN ON");
            setButtonState(BUTTON_CONNECTED);
        } else {
            //swiConnect.setChecked(false);
            //swiConnect.setText("VPN OFF");
            setButtonState(BUTTON_DISCONNECTED);
        }
        Map<String,String> data = sh.read();
        editname.setText(data.get("username"));
        editpasswd.setText(data.get("passwd"));
        Log.i(TAG, "read user info ok.");
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

 //       Window win = getWindow();
   //     win.requestFeature(Window.FEATURE_LEFT_ICON);
     //   win.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.mipmap.ic_launcher);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.mipmap.ic_launcher);

        mContext = MainActivity.this;
        //txtLog = (TextView)findViewById(R.id.textViewLog);
        txtToday = (TextView)findViewById(R.id.textCurrentDay);
        txtThisMonth = (TextView)findViewById(R.id.textCurrentMonth);
        txtUserStatus = (TextView)findViewById(R.id.textViewStaus);
        vpnConnectViewLayout = findViewById(R.id.vpn_connect_view);
        vpnConnectViewLayout.setConnectionListener(new VPNConnectView.ConnectionListener() {
            @Override
            public void connect() {
                if (mState == BUTTON_CONNECTING)
                    return;
                setButtonState(BUTTON_CONNECTING);
                if(MyVPNService.isRun){
                    setButtonState(BUTTON_DISCONNECTED);
                    return;
                }

                Intent intent = VpnService.prepare(getApplicationContext());  //MainActivity.this
                if (intent != null) {
                    intent.setAction(MyVPNService.ACTION_CONNECT);
                    startActivityForResult(intent, 0);
                } else {
                    onActivityResult(0, RESULT_OK, null);
                }
                //Toast.makeText(mContext,"test...",Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Click button");
            }
            @Override
            public void cancel() {
     //           setButtonState(BUTTON_DISCONNECTED);
            }
            @Override
            public void disConnect() {
                if (mState != BUTTON_CONNECTED)
                    return;
                //setButtonState(BUTTON_DISCONNECTED);
                if(!MyVPNService.isRun){
                    return;
                }
                disconnect();
            }
        });

        txtUserStatus.setText("unregisted user. using your real email to register.");
        editname = (EditText)findViewById(R.id.editName);
        editpasswd = (EditText)findViewById(R.id.editPassword);
        sh = new SharedHelper(mContext);
        premium = 0;

        bindViews();
        File[] externalStorageVolumes =
                ContextCompat.getExternalFilesDirs(getApplicationContext(), null);
        File primaryExternalStorage = externalStorageVolumes[0];
        //txtLog.setText(primaryExternalStorage.getPath());
        initFromJNI(primaryExternalStorage.getPath());
        MyVPNService.g_country = getCountryZipCode(mContext);
        Log.i(TAG, "country:" + MyVPNService.g_country);
        g_androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        g_androidId = "And." + g_androidId;
        Log.i(TAG, "serial:" + g_androidId);
        purchaseListener.activity = this;
        purchaseListener.start();
    }
    /**
     * 设置主界面Connect按钮的UI
     */
    public static void setButtonState(final int state1) {
        mState = state1;
        Activity a = (Activity)mContext;
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setVpnConnectModeView(state1);
            }
         });
    }
    //改变vpn状态
    public static void setVpnConnectModeView(int state) {
        vpnConnectViewLayout.setVPNConnectMode(state);
    }

    private void bindViews(){
//        swiConnect = (Switch)findViewById(R.id.switch1);
//        swiConnect.setOnCheckedChangeListener(this);

        btnLogin = (Button) findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(this);
        btnSubStart = (Button)findViewById(R.id.btnSubStart);
        btnSubStart.setOnClickListener(this);
        btnSubQuery = (Button)findViewById(R.id.btnSubQuery);
        btnSubQuery.setOnClickListener(this);
        btnSubLaunch = (Button)findViewById(R.id.btnSubLaunch);
        btnSubLaunch.setOnClickListener(this);
    }
    Runnable login_runnable = new Runnable() {
        private int login() throws IOException {
            SocketChannel webTunnel;
            String web_ip = MyVPNService.g_web_ip;
            Log.i(TAG, "web ip:" + web_ip);
            SocketAddress server = new InetSocketAddress(web_ip, 60315);
            ByteBuffer packet = ByteBuffer.allocate(128);
            try {
                Log.i(TAG, "open web socket.");
                webTunnel = SocketChannel.open();
                webTunnel.connect(server);
                Log.i(TAG, "connect web server ok.");
                String strName = editname.getText().toString();
                String strPassword = editpasswd.getText().toString();
                ByteBuffer b;
                ByteBuffer b2 = ByteBuffer.allocate(128);
                b2.put((byte) 1);  // login
                b = ByteBuffer.wrap(g_androidId.getBytes("US-ASCII"));
                b2.put(b);
                b2.put((byte) '\n');
                b = ByteBuffer.wrap(strName.getBytes("US-ASCII"));
                b2.put(b);
                b2.put((byte) '\n');
                b = ByteBuffer.wrap(strPassword.getBytes("US-ASCII"));
                b2.put(b);
                b2.flip();
                webTunnel.write(b2);
                // Read private_ip
                int length = webTunnel.read(packet);
                //String recv_ip = new String(packet.array(), 0, length);
                //String recv_data[] = recv_ip.split(",");
                packet.flip();
                int ret1 = (packet.get() & 0xff);
                int ret2 = packet.get() & 0xff;
                int day_traffic = packet.getInt();
                int month_traffic = packet.getInt();
                int day_limit = packet.getInt();
                int month_limit = packet.getInt();
                Log.i(TAG, "recv login:" + ret1 + "," + ret2 + "," + day_traffic + "，" + month_traffic);

                webTunnel.close();
                Message msg = new Message();
                msg.what = 1;
                Bundle data = new Bundle();
                data.putInt("ret1", ret1);
                data.putInt("ret2", ret2);
                if (ret1 == 0) {   // login ok
                    data.putInt("day_traffic", day_traffic);
                    data.putInt("month_traffic", month_traffic);
                    data.putInt("day_limit", day_limit);
                    data.putInt("month_limit", month_limit);
                } else {
                    data.putInt("day_traffic", 0);
                    data.putInt("month_traffic", 0);
                    data.putInt("day_limit", 0);
                    data.putInt("month_limit", 0);
                }
                msg.setData(data);
                MainActivity.handler.sendMessage(msg);
            } catch (SocketException e) {
                Log.e(TAG, "Cannot use socket", e);
            }
            return 0;
        }
        @Override
        public void run() {
            try {
                login();
            } catch (Exception e){
                Log.e(TAG, "error:" , e);
            } finally {
            }

        }
    };
    static Runnable charge_runnable = new Runnable() {
        private int login() throws IOException{
            SocketChannel webTunnel;
            String web_ip = MyVPNService.g_web_ip;
            Log.i(TAG, "web ip:"+ web_ip);
            SocketAddress server = new InetSocketAddress(web_ip, 60315);
            ByteBuffer packet = ByteBuffer.allocate(128);
            try {
                Log.i(TAG, "open web socket.");
                webTunnel = SocketChannel.open();
                webTunnel.connect(server);
                Log.i(TAG, "connect web server ok.");
                String strName = editname.getText().toString();
                String strPassword = editpasswd.getText().toString();
                ByteBuffer b;
                ByteBuffer b2 = ByteBuffer.allocate(128);
                b2.put((byte)2);  // charge ok
                b2.put((byte)2);  // premium
                b = ByteBuffer.wrap(strName.getBytes("US-ASCII"));
                b2.put(b);
                b2.flip();
                webTunnel.write(b2);
                //----------------------------
                // Read private_ip
                int length = webTunnel.read(packet);
                //String recv_ip = new String(packet.array(), 0, length);
                //String recv_data[] = recv_ip.split(",");
                packet.flip();
                int ret1 = (packet.get()& 0xff);
                int ret2 = packet.get()&0xff;
                int day_traffic = packet.getInt();
                int month_traffic = packet.getInt();
                int day_limit = packet.getInt();
                int month_limit = packet.getInt();
                Log.i(TAG, "recv login:" + ret1 +"," +ret2 +","+day_traffic +"，"+ month_traffic);

                webTunnel.close();
                Message msg = new Message();
                msg.what = 1;
                Bundle data = new Bundle();
                data.putInt("ret1", ret1);
                data.putInt("ret2", ret2);
                if (ret1 == 0) {   // login ok
                    data.putInt("day_traffic", day_traffic);
                    data.putInt("month_traffic", month_traffic);
                    data.putInt("day_limit", day_limit);
                    data.putInt("month_limit", month_limit);
                } else {
                    data.putInt("day_traffic", 0);
                    data.putInt("month_traffic", 0);
                    data.putInt("day_limit", 0);
                    data.putInt("month_limit", 0);
                }
                msg.setData(data);
                MainActivity.handler.sendMessage(msg);
            } catch (SocketException e) {
                Log.e(TAG, "Cannot use socket", e);
            }
            return 0;
        }
        @Override
        public void run() {
            try {
                login();
            } catch (Exception e){
                Log.e(TAG, "error:" , e);
            } finally {
            }

        }
    };

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        switch (compoundButton.getId()) {
/*            case R.id.switch1:
                if (compoundButton.isChecked()) {
                    if(MyVPNService.isRun){
                        break;
                    }

                    Intent intent = VpnService.prepare(getApplicationContext());  //MainActivity.this
                    if (intent != null) {
                        intent.setAction(MyVPNService.ACTION_CONNECT);
                        startActivityForResult(intent, 0);
                    } else {
                        onActivityResult(0, RESULT_OK, null);
                    }
                    //Toast.makeText(mContext,"test...",Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Click button");
                    swiConnect.setText("Connecting");
                } else {
                    if(!MyVPNService.isRun){
                        break;
                    }
                    if (MyVPNService.isRun == true) {
                        Intent intentStart = new Intent(this, MyVPNService.class);
                        intentStart.setAction(MyVPNService.ACTION_DISCONNECT);
                        startService(intentStart);
                    } else {
                    }
                }
                break;*/
        }
    }
    public void disconnect() {
        Intent intentStart = new Intent(this, MyVPNService.class);
        intentStart.setAction(MyVPNService.ACTION_DISCONNECT);
        startService(intentStart);
    }
    @Override
    public void onClick(View v)  {
        switch (v.getId()){
/*            case R.id.btnConnect:
                if (out_of_quota == 1) {
                    txtUserStatus.setText("No enough quota.");
                    break;
                }
                btnConnect.setEnabled(false);
                btnDisconnect.setEnabled(true);
                Intent intent = VpnService.prepare(getApplicationContext());  //MainActivity.this
                if (intent != null) {
                    intent.setAction(MyVPNService.ACTION_CONNECT);
                    startActivityForResult(intent, 0);
                } else {
                    onActivityResult(0, RESULT_OK, null);
                }
                //Toast.makeText(mContext,"test...",Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Click button");
                break;
            case R.id.btnDisconnect:
                if (MyVPNService.isRun == true) {
                    Intent intentStart = new Intent(this, MyVPNService.class);
                    intentStart.setAction(MyVPNService.ACTION_DISCONNECT);
                    startService(intentStart);
                } else {
                }
                break;*/
            case R.id.btnLogin:
                if (premium == 0) {
                    String strname = editname.getText().toString();
                    String strpasswd = editpasswd.getText().toString();
                    sh.save(strname, strpasswd);
                    Log.i(TAG, "save user info ok.");
                    new Thread(login_runnable).start();
                } else {
                    editname.setEnabled(true);
                    editpasswd.setEnabled(true);
                    btnLogin.setText("Login");
                    btnSubLaunch.setEnabled(false);
                    txtUserStatus.setText("unregisted user. using your real email to register.");
                    premium = 0;

                    if (MyVPNService.isRun == true) {
                        Intent intentStart = new Intent(this, MyVPNService.class);
                        intentStart.setAction(MyVPNService.ACTION_DISCONNECT);
                        startService(intentStart);
                    } else {
//                        btnConnect.setEnabled(true);
  //                      btnDisconnect.setEnabled(false);
                    }
                }
                break;
            case R.id.btnSubStart:
//                purchaseListener.start();
                break;
            case R.id.btnSubQuery:
//                purchaseListener.query();
                break;
            case R.id.btnSubLaunch:
                purchaseListener.query();
                break;
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Intent intentStart = new Intent(this, MyVPNService.class);
            //intentStart.putExtra(TAG, (Bundle)this);
            intentStart.setAction(MyVPNService.ACTION_CONNECT);
            startService(intentStart);
        }
    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
    public native int initFromJNI(String log_path);
}
