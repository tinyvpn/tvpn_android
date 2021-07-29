package com.tinyvpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

public class MyVPNService extends VpnService implements Runnable   {
    private static final String TAG = MyVPNService.class.getSimpleName();
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
//    private static final long IDLE_INTERVAL_MS = TimeUnit.MILLISECONDS.toMillis(100);
//    private static final long RECEIVE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(300);
    private static int g_protocol=1;  // 0 tcp, 1 http, 8 udp, 9 dns
    static String g_web_ip = "www.tinyvpn.xyz";  //192.168.50.218   "104.248.227.173"
    static String g_ip;// = "23.105.198.10";
    private static int g_port;// = 51214;
    private static int g_obfu = 1;
    private static int g_obfu_first_four = 1;
    static String g_country;
    static String userName;
    static String userPassword;
    static int day_traffic = 0;  // k bytes
    static int month_traffic = 0;  // k bytes
    private int current_traffic = 0 ;  //bytes
    private int client_fd;  // used in jni
    private int privateIp;  // used in jni

    public static final String ACTION_CONNECT = "com.example.android.toyvpn.START";
    public static final String ACTION_DISCONNECT = "com.example.android.toyvpn.STOP";
    private ParcelFileDescriptor descriptor;
//    private FileInputStream inputStream;
//    private FileOutputStream outputStream;
    public static boolean isRun;
    private Thread vpnThread;
    private int mtuSize = 1500;
    //private DatagramChannel tunnel;
    private SocketChannel tunnel;
    private String private_ip;
//    private ByteBuffer buf_private_ip;
    private ByteBuffer g_tcp_buf;
    private static final int BUF_SIZE=4*4096;
//    private static final int HEAD_OFFSET=1024;
    @Override
    public void onCreate() {
        super.onCreate();
        g_tcp_buf = ByteBuffer.allocate(BUF_SIZE*2);
//        buf_private_ip = ByteBuffer.allocate(MAX_PACKET_SIZE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    @Override
    public void run()  {

        try{
            if(connectWebServer() != 0) {
                Log.i(TAG, "connect web error..");
                Message msg = new Message();
                msg.what = 0;
                MainActivity.handler.sendMessage(msg);
                isRun = false;
                //stop();
                return;
            }
            if (g_protocol == 1) { //http
/*                if (connectServer() != 0) {
                    Message msg = new Message();
                    msg.what = 0;
                    MainActivity.handler.sendMessage(msg);
                    isRun = false;
                    return;
                }
                // 开启连接后初始化VPN参数并建立连接
                establishVPN();
                // 开始读写操作
                startStream();*/

                int ret = connectServerSSL();
                if (ret != 0) {  // fail
                    Log.e(TAG, "connect http error.");
                }

            } else if(g_protocol == 2) {  //ssl
                int ret = connectServerSSL();
                if (ret != 0) {  // fail
                    Log.e(TAG, "connect ssl error.");
                }
            }
        } catch (Exception e){
            Log.e(TAG, "error:" , e);
        } finally {
            stop();
        }
        Log.i(TAG, "quit while...");
        Message msg = new Message();
        msg.what = 0;
        MainActivity.handler.sendMessage(msg);
        isRun = false;
    }
    int connectWebServer() throws IOException, InterruptedException, IllegalArgumentException {
        SocketAddress server = new InetSocketAddress(g_web_ip, 60315);
        ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_SIZE);
        try {
            Log.i(TAG, "open web socket.");
            //tunnel = DatagramChannel.open();
            tunnel = SocketChannel.open();
            tunnel.connect(server);
            ByteBuffer b = ByteBuffer.wrap(g_country.getBytes("US-ASCII"));
            ByteBuffer b2 = ByteBuffer.allocate(128);
            b2.put((byte)0);  // get ip
            b2.put((byte)MainActivity.premium);
            b2.put(b);
            if (MainActivity.premium <= 1) {
                b = ByteBuffer.wrap(MainActivity.g_androidId.getBytes("US-ASCII"));
                b2.put(b);
            } else {
                b = ByteBuffer.wrap(userName.getBytes("US-ASCII"));
                b2.put(b);
            }
            b2.flip();
            tunnel.write(b2);
            // Read private_ip
            int length = tunnel.read(packet);
            packet.flip();
            day_traffic = packet.getInt();
            month_traffic = packet.getInt();
            current_traffic = 0;
            Message msg = new Message();
            msg.what = 2;  // update traffic
            Bundle data = new Bundle();
            data.putInt("day_traffic", day_traffic + current_traffic/1024);
            data.putInt("month_traffic", month_traffic + current_traffic/1024);
            data.putInt("day_limit", packet.getInt());
            data.putInt("month_limit", packet.getInt());
            msg.setData(data);
            Log.d(TAG, "connect, traffic:" +(day_traffic + current_traffic/1024) +","+(month_traffic + current_traffic/1024));
            MainActivity.handler.sendMessage(msg);
            if (MainActivity.premium <=1){
                if (day_traffic+ current_traffic/1024 > data.getInt("day_limit"))
                    return 1;
            }else {
                if (month_traffic+ current_traffic/1024 > data.getInt("month_limit"))
                    return 1;

            }

            String recv_ip = new String(packet.array(), 16, length-16);
            String recv_data[] = recv_ip.split(",");
            if(recv_data.length < 1) {
                Log.e(TAG, "recv server ip data error.");
                tunnel.close();
                return 1;
            }
            Log.i(TAG, "recv:" + recv_data[0]);
            // select first server, need to fix

            String server_data[] = recv_data[0].split(":");
            if(server_data.length < 3) {
                Log.e(TAG, "parse server ip data error.");
                tunnel.close();
                return 1;
            }
            //Log.i(TAG, "data:" + server_data[0]+","+server_data[1]+","+server_data[2]);
            g_protocol = Integer.valueOf(server_data[0]);
            g_ip = server_data[1];
            //g_ip = "192.168.50.218";
            g_port = Integer.valueOf(server_data[2]);
            Log.i(TAG, "protocol:"+g_protocol +", ip:"+g_ip+", port:" + g_port);
            tunnel.close();
        } catch (SocketException e) {
            Log.e(TAG, "Cannot use socket", e);
            tunnel.close();
        }
        return 0;
    }

    int connectServerSSL() throws IOException, InterruptedException, IllegalArgumentException {

        if (connectServerFromJni(g_protocol, g_ip, g_port, MainActivity.premium, MainActivity.g_androidId, userName, userPassword)!=0)  // ssl connect, send auth info, recv private_ip
            return 1;
        byte[]bytes=new byte[4];
        bytes[0]=(byte) ((privateIp>>24)&0xff);
        bytes[1]=(byte) ((privateIp>>16)&0xff);
        bytes[2]=(byte) ((privateIp>>8)&0xff);
        bytes[3]=(byte) (privateIp & 0xff);
        int ip1 = ((privateIp>>24)&0xff);
        int ip2 = ((privateIp>>16)&0xff);
        int ip3 = ((privateIp>>8)&0xff);
        int ip4 = (privateIp & 0xff);

        //private_ip = String.valueOf(bytes[0]) + "." +String.valueOf(bytes[1]) + "."+String.valueOf(bytes[2])+ "."+String.valueOf((int)bytes[3]);
        private_ip = String.valueOf(ip1) + "." +String.valueOf(ip2) + "."+String.valueOf(ip3)+ "."+String.valueOf(ip4);
        Log.i(TAG, "get private ip ok:" + private_ip);
        if (!protect(client_fd)) {
            //throw new IllegalStateException("Cannot protect the tunnel");
            Log.e(TAG, "cannot protect the tunnel:" + client_fd);
            return 1;
        }

        try{
            establishVPN();
        } catch (Exception e){
            Log.e(TAG, "error:" , e);
            stop();
            return 1;
        }
        Message msg = new Message();
        msg.what = 5;
        MainActivity.handler.sendMessage(msg);

        isRun = true;
        startSslThreadFromJni(descriptor.getFd());
        return 0;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null ) {
            Log.e(TAG, "intent null.");
            return START_NOT_STICKY;
        }

        if (ACTION_DISCONNECT.equals(intent.getAction())) {
            Log.i(TAG, "stop...");
            //isRun = false;
            g_tcp_buf.clear();
            // 停止服务时需要关闭读写流
            stop();
            return START_NOT_STICKY;
        } else {
            // 由于需要对网络数据进行读写，为了主线程不被堵塞
            // 所以需要新建一个线程来运行读写操作
            //isRun = true;
            g_tcp_buf.clear();
            Log.i(TAG, "start...");
            vpnThread = new Thread(this,"VPNService");
            vpnThread.start();
            return START_STICKY;
        }
    }
    /**
     * 根据配置创建VPN代理  获取到文件描述符 建立VPN连接
     * @throws Exception
     */
    private void establishVPN() throws Exception {
        Builder builder = new Builder();
        // 设置最大缓存
        builder.setMtu(mtuSize);
        // 设置服务名
        builder.setSession(getString(R.string.app_name));

        // 设置虚拟主机的地址
        // 开启了服务后所有能进入该服务的数据报都会转发到该地址
        InetAddress private_address = InetAddress.getByName(private_ip);
        builder.addAddress(private_address, 32);

        // 设置需要拦截的路由地址
        // 设置了该值后，这个地址下的所有数据都被转发到虚拟主机进行处理。
        builder.addRoute("0.0.0.0", 0);

        // 设置域名解析服务器地址。
        builder.addDnsServer("8.8.8.8");
        //builder.addDnsServer("8.8.4.4");
        //builder.addDnsServer("208.67.222.222");
        //builder.addDnsServer("114.114.114.114");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 添加需要拦截的应用
            // 这里有可能拋出一个未找到应用的异常。
           // builder.addAllowedApplication(getPackageName());

        }
        // 调用方法建立连接，并把返回的包描述符实例赋值给全局变量
        descriptor = builder.establish();
    }
    // 关闭数据流 并结束服务。
    public synchronized void stop() {
        stopRunFromJni();

        if (descriptor != null) {
            try {
                descriptor.close();
            } catch (IOException e) {
            } finally {
                descriptor = null;
            }
        }
        if (tunnel != null) {
            try {
                tunnel.close();
            } catch (IOException e) {
            } finally {
                tunnel = null;
            }
        }
        stopSelf();
    }
    public int stopCallback()
    {
        Log.i(TAG, "quit while2...");
        Message msg = new Message();
        msg.what = 0;
        MainActivity.handler.sendMessage(msg);
        isRun = false;
        return 0;
    }
    public int trafficCallback()
    {
        Message msg = new Message();
        msg.what = 3;  // update traffic
        Bundle data = new Bundle();
        data.putInt("day_traffic", day_traffic + current_traffic/1024);
        data.putInt("month_traffic", month_traffic + current_traffic/1024);
        msg.setData(data);
        Log.d(TAG, "traffic:" +(day_traffic + current_traffic/1024) +","+(month_traffic + current_traffic/1024));
        MainActivity.handler.sendMessage(msg);
        return 0;
    }
//    public native int pushHttpHeaderFromJni(ByteBuffer buf, int length, int obfu);
//    public native int popFrontXdpiHeadFromJni(ByteBuffer buf, int position, int length, int obfu);
    public native int connectServerFromJni(int protocol, String ip, int port,int premium, String androidId, String userName, String userPassword);  // for ssl
    public native int startSslThreadFromJni(int tun_fd);
    public native int stopRunFromJni();
}
