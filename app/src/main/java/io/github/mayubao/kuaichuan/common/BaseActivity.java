package io.github.mayubao.kuaichuan.common;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import butterknife.ButterKnife;
import io.github.mayubao.kuaichuan.R;
import io.github.mayubao.kuaichuan.core.utils.ToastUtils;
import io.github.mayubao.kuaichuan.ui.ReceiverWaitingActivity;
import io.github.mayubao.kuaichuan.utils.StatusBarUtils;

/**
 * Created by mayubao on 2016/11/24.
 * Contact me 345269374@qq.com
 */
public abstract class BaseActivity extends AppCompatActivity {
    /**
     * 写文件的请求码
     */
    public static final int  REQUEST_CODE_WRITE_FILE = 200;

    /**
     * 读取文件的请求码
     */
    public static final int  REQUEST_CODE_READ_FILE = 201;

    /**
     * 打开GPS的请求码
     */
    public static final int  REQUEST_CODE_OPEN_GPS = 205;
    public static final int REQUEST_PERMISSION_CODE = 1;
    public static final int REQUEST_CODE_WRITE_SETTINGS = 7879;
    protected Context mContext;
    protected ProgressDialog mProgressDialog;
    protected String[] permissions;
    protected Bundle savedInstanceState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mContext = this;
        StatusBarUtils.setStatuBarAndBottomBarTranslucent(this);
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());
        ButterKnife.bind(this);
        this.savedInstanceState = savedInstanceState;
        permissions = initPermissions();
        boolean isPass;
        if(this instanceof ReceiverWaitingActivity){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                isPass = Settings.System.canWrite(mContext);
            }else{
                isPass = (ContextCompat.checkSelfPermission(mContext,
                        Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED);
            }
            if(!isPass){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + mContext.getPackageName()));
                    startActivityForResult(intent, REQUEST_CODE_WRITE_SETTINGS);
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_SETTINGS}, REQUEST_PERMISSION_CODE);
                }
            }
        }
        if(checkPermissionIsGranted()){
            initParams(savedInstanceState);
        }else{
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_CODE);
        }
    }

    protected abstract int getLayoutId();
    protected abstract void initParams(Bundle savedInstanceState);
    protected abstract String[] initPermissions();

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_CODE
                || requestCode == REQUEST_CODE_WRITE_SETTINGS) {
            if (permissionIsGranted(grantResults)) {
                initParams(savedInstanceState);
            } else {
                ToastUtils.show(this, "权限被拒绝，关闭程序");
                finish();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * 获取上下文
     * @return
     */
    public Context getContext(){
        return mContext;
    }

    /**
     * 显示对话框
     */
    protected void showProgressDialog(){
        if(mProgressDialog == null){
            mProgressDialog = new ProgressDialog(mContext);
        }
        mProgressDialog.setMessage(getResources().getString(R.string.tip_loading));
        mProgressDialog.show();
    }

    /**
     * 隐藏对话框
     */
    protected void hideProgressDialog(){
        if(mProgressDialog != null && mProgressDialog.isShowing()){
            mProgressDialog.hide();
            mProgressDialog = null;
        }
    }

    private boolean permissionIsGranted(int[] grantResults) {
        for (int grant : grantResults) {
            if (grant != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean checkPermissionIsGranted() {
        if(permissions == null || permissions.length == 0)
            return true;
        for (String permission : permissions) {
            if(ContextCompat.checkSelfPermission(mContext, permission)
                    != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }
}
