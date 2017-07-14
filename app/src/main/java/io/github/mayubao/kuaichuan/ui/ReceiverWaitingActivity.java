package io.github.mayubao.kuaichuan.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import butterknife.Bind;
import butterknife.OnClick;
import io.github.mayubao.kuaichuan.AppContext;
import io.github.mayubao.kuaichuan.Constant;
import io.github.mayubao.kuaichuan.R;
import io.github.mayubao.kuaichuan.common.BaseActivity;
import io.github.mayubao.kuaichuan.core.entity.FileInfo;
import io.github.mayubao.kuaichuan.core.entity.IpPortInfo;
import io.github.mayubao.kuaichuan.core.receiver.WifiAPBroadcastReceiver;
import io.github.mayubao.kuaichuan.core.utils.ApMgr;
import io.github.mayubao.kuaichuan.core.utils.TextUtils;
import io.github.mayubao.kuaichuan.core.utils.WifiMgr;
import io.github.mayubao.kuaichuan.ui.view.RadarLayout;
import io.github.mayubao.kuaichuan.utils.NavigatorUtils;

/**
 * 接收等待文件传输UI
 */
public class ReceiverWaitingActivity extends BaseActivity {

    private static final String TAG = ReceiverWaitingActivity.class.getSimpleName();

    /**
     * Topbar相关UI
     */
    @Bind(R.id.tv_back)
    TextView tv_back;

    /**
     * 其他UI
     */
    @Bind(R.id.radarLayout)
    RadarLayout radarLayout;
    @Bind(R.id.tv_device_name)
    TextView tv_device_name;
    @Bind(R.id.tv_desc)
    TextView tv_desc;

    WifiAPBroadcastReceiver mWifiAPBroadcastReceiver;
    boolean mIsInitialized = false;

    /**
     * 与 文件发送方 通信的 线程
     */
    Runnable mUdpServerRuannable;

    public static final int MSG_TO_FILE_RECEIVER_UI = 0X88;

    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == MSG_TO_FILE_RECEIVER_UI){
                IpPortInfo ipPortInfo = (IpPortInfo) msg.obj;
                Bundle bundle = new Bundle();
                bundle.putSerializable(Constant.KEY_IP_PORT_INFO, ipPortInfo);
                NavigatorUtils.toFileReceiverListUI(getContext(), bundle);

                finishNormal();
            }
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_receiver_waiting;
    }

    @Override
    protected void initParams(Bundle savedInstanceState) {
        radarLayout.setUseRing(true);
        radarLayout.setColor(getResources().getColor(R.color.white));
        radarLayout.setCount(4);
        radarLayout.start();

        //1.初始化热点
        WifiMgr.getInstance(getContext()).disableWifi();
        if(ApMgr.isApOn(getContext())){
            ApMgr.disableAp(getContext());
        }

        mWifiAPBroadcastReceiver = new WifiAPBroadcastReceiver() {
            @Override
            public void onWifiApEnabled() {
                Log.i(TAG, "======>>>onWifiApEnabled !!!");
                if(!mIsInitialized){
                    mUdpServerRuannable = createSendMsgToFileSenderRunnable();
                    AppContext.MAIN_EXECUTOR.execute(mUdpServerRuannable);
                    mIsInitialized = true;

                    tv_desc.setText(getResources().getString(R.string.tip_now_init_is_finish));
                    tv_desc.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            tv_desc.setText(getResources().getString(R.string.tip_is_waitting_connect));
                        }
                    }, 2*1000);
                }
            }
        };
        IntentFilter filter = new IntentFilter(WifiAPBroadcastReceiver.ACTION_WIFI_AP_STATE_CHANGED);
        registerReceiver(mWifiAPBroadcastReceiver, filter);

        ApMgr.isApOn(getContext()); // check Ap state :boolean
        String ssid = TextUtils.isNullOrBlank(Build.DEVICE) ? Constant.DEFAULT_SSID : Build.DEVICE;
        ApMgr.configApState(getContext(), ssid); // change Ap state :boolean

        tv_device_name.setText(ssid);
        tv_desc.setText(getResources().getString(R.string.tip_now_is_initial));
    }

    @Override
    protected String[] initPermissions() {
        return null;
    }

    @SuppressLint("NewApi")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_WRITE_SETTINGS && Settings.System.canWrite(this)){
            Log.d("TAG", "CODE_WRITE_SETTINGS_PERMISSION success");
            initParams(savedInstanceState);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if(mWifiAPBroadcastReceiver != null){
            unregisterReceiver(mWifiAPBroadcastReceiver);
            mWifiAPBroadcastReceiver = null;
        }
        closeSocket();
        //关闭热点
        ApMgr.disableAp(getContext());
        this.finish();
    }


    /**
     * 成功进入 文件接收列表UI 调用的finishNormal()
     */
    private void finishNormal(){
        if(mWifiAPBroadcastReceiver != null){
            unregisterReceiver(mWifiAPBroadcastReceiver);
            mWifiAPBroadcastReceiver = null;
        }
        closeSocket();
        this.finish();
    }

    @OnClick({R.id.tv_back})
    public void onClick(View view){
        switch (view.getId()){
            case R.id.tv_back:{
                onBackPressed();
                break;
            }
        }
    }

    /**
     * 创建发送UDP消息到 文件发送方 的服务线程
     */
    private Runnable createSendMsgToFileSenderRunnable(){
        return new Runnable() {
            @Override
            public void run() {
                try {
                    startFileReceiverServer(Constant.DEFAULT_SERVER_COM_PORT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }
    

    /**
     * 开启 文件接收方 通信服务 (必须在子线程执行)
     * @param serverPort
     * @throws Exception
     */
    DatagramSocket mDatagramSocket;
    private void startFileReceiverServer(int serverPort) throws Exception{

        //网络连接上，无法获取IP的问题
        int count = 0;
        String localAddress = WifiMgr.getInstance(getContext()).getHotspotLocalIpAddress();
        while(localAddress.equals(Constant.DEFAULT_UNKOWN_IP) && count <  Constant.DEFAULT_TRY_TIME){
            Thread.sleep(1000);
            localAddress = WifiMgr.getInstance(getContext()).getHotspotLocalIpAddress();
            Log.i(TAG, "receiver get local Ip ----->>>" + localAddress);
            count ++;
        }

        mDatagramSocket = new DatagramSocket(serverPort);
        byte[] receiveData = new byte[1024];
        while(true) {
            //1.接收 文件发送方的消息
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            mDatagramSocket.receive(receivePacket);
            String msg = new String( receivePacket.getData()).trim();
            InetAddress inetAddress = receivePacket.getAddress();
            int port = receivePacket.getPort();
            if(msg != null && msg.startsWith(Constant.MSG_FILE_RECEIVER_INIT)){
                Log.i(TAG, "Get the msg from FileReceiver######>>>" + Constant.MSG_FILE_RECEIVER_INIT);
                // 进入文件接收列表界面 (文件接收列表界面需要 通知 文件发送方发送 文件开始传输UDP通知)
                mHandler.obtainMessage(MSG_TO_FILE_RECEIVER_UI, new IpPortInfo(inetAddress, port)).sendToTarget();
            }else{ //接收发送方的 文件列表
                if(msg != null){
                    System.out.println("Get the FileInfo from FileReceiver######>>>" + msg);
                    parseFileInfo(msg);
                }
            }
        }
    }

    /**
     * 解析FileInfo
     * @param msg
     */
    private void parseFileInfo(String msg) {
        FileInfo fileInfo = FileInfo.toObject(msg);
        if(fileInfo != null && fileInfo.getFilePath() != null){
            AppContext.getAppContext().addReceiverFileInfo(fileInfo);
        }
    }

    /**
     * 关闭UDP Socket 流
     */
    private void closeSocket(){
        if(mDatagramSocket != null){
            mDatagramSocket.disconnect();
            mDatagramSocket.close();
            mDatagramSocket = null;
        }
    }

}
