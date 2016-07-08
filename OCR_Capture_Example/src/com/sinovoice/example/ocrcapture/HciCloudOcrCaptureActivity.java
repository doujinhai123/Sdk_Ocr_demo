package com.sinovoice.example.ocrcapture;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sinovoice.example.AccountInfo;
import com.sinovoice.hcicloudsdk.android.ocr.capture.CaptureErrCode;
import com.sinovoice.hcicloudsdk.android.ocr.capture.CaptureEvent;
import com.sinovoice.hcicloudsdk.android.ocr.capture.OCRCapture;
import com.sinovoice.hcicloudsdk.android.ocr.capture.OCRCaptureListener;
import com.sinovoice.hcicloudsdk.api.HciCloudSys;
import com.sinovoice.hcicloudsdk.common.AuthExpireTime;
import com.sinovoice.hcicloudsdk.common.HciErrorCode;
import com.sinovoice.hcicloudsdk.common.InitParam;
import com.sinovoice.hcicloudsdk.common.ocr.OcrCornersResult;
import com.sinovoice.hcicloudsdk.common.ocr.OcrInitParam;
import com.sinovoice.hcicloudsdk.common.ocr.OcrRecogResult;
import com.sinovoice.hcicloudsdk.common.ocr.OcrTemplateId;


public class HciCloudOcrCaptureActivity extends Activity {
	private static final String TAG = "HciCloudOcrCaptureActivity";
	
    /**
     * 加载用户信息工具类
     */
    private AccountInfo mAccountInfo;
   
	
//	private HciCloudOcrCaptureActivity activity;
//	private Context context;
	private Handler hander;
	private String capKey;
	
	//显示摄像头预览视图的布局文件
	private FrameLayout cameraPreviewLayout;
	private ProgressDialog pDialog;
	
	//识别配置参数
	private String recogConfig;
	//是否需要加载模板文件
	private boolean isNeedLoadTemplate;
	//模板文件相关
	private OcrTemplateId currTemplateId;
	private int templateId;
	
	//拍照器模块
	private OCRCapture ocrCapture;
	private OCRCaptureListener ocrCaptureListener = new OCRCaptureListener() {
		
		@Override
		public void onCaptureEventStateChange(CaptureEvent captureEvent) {
			// TODO Auto-generated method stub		
		}
		
		@Override
		public void onCaptureEventRecogFinish(CaptureEvent captureEvent,
				OcrRecogResult ocrRecogResult) {
			switch (captureEvent) {
			case CAPTURE_EVENT_RECOGNIZE_FINISH:
				if(ocrRecogResult != null){
					Log.v(TAG, "recog result = " + ocrRecogResult.getResultText());
					showResultView(ocrRecogResult.getResultText());
				}
				break;

			default:
				break;
			}
		}
		
		@Override
		public void onCaptureEventError(CaptureEvent captureEvent, int errorCode) {
			// TODO Auto-generated method stub		
		}
		
		@Override
		public void onCaptureEventCapturing(CaptureEvent captureEvent,
				final byte[] imageData, OcrCornersResult ocrCornersResult) {
			Log.i(TAG, "onCaptureEventCapturing. imageData len = " + imageData.length);
			
			//文档识别
			if(capKey.equalsIgnoreCase("ocr.cloud")
					|| capKey.equalsIgnoreCase("ocr.cloud.english")
					|| capKey.equalsIgnoreCase("ocr.local")){
				
				hander.post(new Runnable() {
					
					@Override
					public void run() {
						ocrCapture.hciOcrCaptureRecog(imageData, recogConfig, null);
					}
				});
			}
		}
	};	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//设置屏幕显示方向
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);			
		
		//呈现默认布局
		showMainView();
	
		hander = new Handler();		
		
		pDialog = ProgressDialog.show(this, getText(R.string.dialog_title_tips), getText(R.string.dialog_msg_hcicloud_sysinit));
				
        mAccountInfo = AccountInfo.getInstance();
        boolean loadResult = mAccountInfo.loadAccountInfo(this);
        if (loadResult) {
            // 加载信息成功进入主界面
			Toast.makeText(getApplicationContext(), "加载灵云账号成功",
					Toast.LENGTH_SHORT).show();
        } else {
            // 加载信息失败，显示失败界面
			Toast.makeText(getApplicationContext(), "加载灵云账号失败！请在assets/AccountInfo.txt文件中填写正确的灵云账户信息，账户需要从www.hcicloud.com开发者社区上注册申请。",
					Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 加载信息,返回InitParam, 获得配置参数的字符串
        InitParam initParam = getInitParam();
        String strConfig = initParam.getStringConfig();
        Log.i(TAG,"\nhciInit config:" + strConfig);
        
        // 初始化
        int errCode = HciCloudSys.hciInit(strConfig, this);
        if (errCode != HciErrorCode.HCI_ERR_NONE && errCode != HciErrorCode.HCI_ERR_SYS_ALREADY_INIT) {
        	Toast.makeText(getApplicationContext(), "hciInit error: " + HciCloudSys.hciGetErrorInfo(errCode),Toast.LENGTH_SHORT).show();
            return;
        } 
        
        // 获取授权/更新授权文件 :
        errCode = checkAuthAndUpdateAuth();
        if (errCode != HciErrorCode.HCI_ERR_NONE) {
            // 由于系统已经初始化成功,在结束前需要调用方法hciRelease()进行系统的反初始化
        	Toast.makeText(getApplicationContext(), "CheckAuthAndUpdateAuth error: " + HciCloudSys.hciGetErrorInfo(errCode),Toast.LENGTH_SHORT).show();
            HciCloudSys.hciRelease();
            return;
        }
                
		// 读取用户的调用的能力
		capKey = mAccountInfo.getCapKey();
				
		if(capKey.equalsIgnoreCase("ocr.cloud")
				|| capKey.equalsIgnoreCase("ocr.cloud.english")
				|| capKey.equalsIgnoreCase("ocr.local") || capKey.equalsIgnoreCase("ocr.cloud.bankcard")){
			//文档识别
			recogConfig = "capKey=" + capKey;
		}else if(capKey.equalsIgnoreCase("ocr.cloud.template")){
			//身份证识别
			recogConfig = "capKey=" + capKey + ",domain=idcard,templateIndex=0,templatePageIndex=0";
		}
		else if(capKey.equalsIgnoreCase("ocr.local.bizcard.v6")){
			//名片识别
			recogConfig = "capkey=ocr.local.bizcard,cutedge=yes";
		}else if(capKey.equalsIgnoreCase("ocr.local.bankcard.v7")){
			//银行卡识别
			recogConfig = "capkey=ocr.local.bankcard.v7,cutedge=yes";
		}else if(capKey.equalsIgnoreCase("ocr.local.template.v6")){
			//模板识别
			isNeedLoadTemplate = true;
		}else{
			Log.e(TAG, "未知的capKey。 capKey = " + capKey);
		}				
		
		ocrCapture = new OCRCapture();
		initOCRCapture();
		dismissDialog();											
	}

	/**
	 * 初始化OCR模块 
	 */
	private void initOCRCapture() {
		OcrInitParam ocrInitParam = new OcrInitParam();
		String sdPath = Environment.getExternalStorageDirectory()
                .getAbsolutePath();
        String packageName = this.getPackageName();
		String dataPath = sdPath + File.separator + "sinovoice"
                + File.separator + packageName + File.separator + "data"
                + File.separator;
		copyData(dataPath, "ocr-data");
        //String dataPath = getFilesDir().getPath().replace("files", "lib");
		if (capKey.contains("local")) {
			ocrInitParam.addParam(OcrInitParam.PARAM_KEY_DATA_PATH, dataPath);
			ocrInitParam.addParam(OcrInitParam.PARAM_KEY_INIT_CAP_KEYS, capKey);
			//ocrInitParam.addParam(OcrInitParam.PARAM_KEY_FILE_FLAG, OcrInitParam.VALUE_OF_PARAM_FILE_FLAG_ANDROID_SO);
		}
		
		String initParam = ocrInitParam.getStringConfig();
		int captureErrorCode = ocrCapture.hciOcrCaptureInit(getApplicationContext(), initParam, ocrCaptureListener);
		dismissDialog();
		
		//初始化成功，如果需要加载模板就启动新线程加载模板，加载成功后显示摄像头预览界面，否则直接显示摄像头预览界面
		if(captureErrorCode == CaptureErrCode.CAPTURE_ERR_NONE){
			Log.i(TAG, "hciOcrCaptureInit success.");
			if(isNeedLoadTemplate){
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						//载入模板
				        String sdPath = Environment.getExternalStorageDirectory()
				                .getAbsolutePath();
						String dataPath = sdPath + File.separator + "sinovoice"
				                + File.separator + "com.sinovoice.example.ocrcapture" + File.separator + "data";

						String templatePath = dataPath + "/iRead_VLC_encode.xml";
						currTemplateId = new OcrTemplateId();
						int errorCode = ocrCapture.hciOcrCaptureLoadTemplate("", templatePath, currTemplateId);
						if (errorCode != CaptureErrCode.CAPTURE_ERR_NONE) {
							Log.e(TAG, "hciOcrLoadTemplate() error. errorcode = " + errorCode);
						}else{
							templateId = currTemplateId.getTemplateId();
							recogConfig = "capkey=ocr.local.template.v6,cutedge=yes,templateid="+ templateId +",templateIndex=0,templatePageIndex=0";
							hander.post(new Runnable() {
								
								@Override
								public void run() {
									showCaptureView();
								}
							});
						}
					}
				}).start();
			}else{
				showCaptureView();				
			}
		}else{
			Log.e(TAG, "hciOcrCaptureInit fail. captureErrorCode = " + captureErrorCode);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		//释放拍照器资源
		if(ocrCapture != null){
			if(isNeedLoadTemplate){
				int captureErrorCode = ocrCapture.hciOcrCaptureUnloadTemplate(currTemplateId);
				Log.v(TAG, "hciOcrCaptureUnloadTemplate(), captureErrorCode = " + captureErrorCode);
			}
			
			int captureErrorCode = ocrCapture.hciOcrCaptureRelease();
			Log.v(TAG, "hciOcrCaptureRelease(), captureErrorCode = " + captureErrorCode);
			
			ocrCapture = null;
		}
		
		HciCloudSys.hciRelease();
	}

	/**
	 * 关闭对话框
	 */
	private void dismissDialog() {
		if(pDialog != null && pDialog.isShowing()){
			pDialog.dismiss();
			pDialog = null;
		}
	}
	
	/**
	 * 显示摄像头预览界面
	 */
	private void showCaptureView(){
		setContentView(R.layout.ocr_capture_camera_preview);
		
		if(cameraPreviewLayout != null){
			cameraPreviewLayout.removeAllViews();
			cameraPreviewLayout = null;
		}
		
		cameraPreviewLayout = (FrameLayout) findViewById(R.id.layout_camera_preview);
		cameraPreviewLayout.addView(ocrCapture.getCameraPreview());
		
		Button btnTakePicture = (Button) findViewById(R.id.btn_take_picture);
		btnTakePicture.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				ocrCapture.hciOcrCaptureStopAndRecog();
			}
		});
		
		ocrCapture.hciOcrCaptureStart(recogConfig);
	}
	
	private void showMainView(){
		showResultView("初始状态");
	}
	
	/**
	 * 显示结果界面
	 * @param text
	 */
	private void showResultView(String text){
		if(cameraPreviewLayout != null){
			cameraPreviewLayout.removeAllViews();
			cameraPreviewLayout = null;
		}
		
		setContentView(R.layout.ocr_capture_result);
		
		TextView tvResult = (TextView) findViewById(R.id.tv_result);
		tvResult.setText(text);
	}
	
	 /**
     * 获取授权
     * 
     * @return true 成功
     */
    private int checkAuthAndUpdateAuth() {
        
    	// 获取系统授权到期时间
        int initResult;
        AuthExpireTime objExpireTime = new AuthExpireTime();
        initResult = HciCloudSys.hciGetAuthExpireTime(objExpireTime);
        if (initResult == HciErrorCode.HCI_ERR_NONE) {
            // 显示授权日期,如用户不需要关注该值,此处代码可忽略
            Date date = new Date(objExpireTime.getExpireTime() * 1000);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd",
                    Locale.CHINA);
            Log.i(TAG, "expire time: " + sdf.format(date));

            if (objExpireTime.getExpireTime() * 1000 > System
                    .currentTimeMillis()) {
                // 已经成功获取了授权,并且距离授权到期有充足的时间(>7天)
                Log.i(TAG, "checkAuth success");
                return initResult;
            }
            
        } 
        
        // 获取过期时间失败或者已经过期
        initResult = HciCloudSys.hciCheckAuth();
        if (initResult == HciErrorCode.HCI_ERR_NONE) {
            Log.i(TAG, "checkAuth success");
            return initResult;
        } else {
            Log.e(TAG, "checkAuth failed: " + initResult);
            return initResult;
        }
    }
    
    /**
     * 加载初始化信息
     * 
     * @param context
     *            上下文语境
     * @return 系统初始化参数
     */
    private InitParam getInitParam() {
        String authDirPath = this.getFilesDir().getAbsolutePath();

        // 前置条件：无
        InitParam initparam = new InitParam();

        // 授权文件所在路径，此项必填
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_AUTH_PATH, authDirPath);

        // 是否自动访问云授权,详见 获取授权/更新授权文件处注释
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_AUTO_CLOUD_AUTH, "no");

        // 灵云云服务的接口地址，此项必填
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_CLOUD_URL, AccountInfo
                .getInstance().getCloudUrl());

        // 开发者Key，此项必填，由捷通华声提供
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_DEVELOPER_KEY, AccountInfo
                .getInstance().getDeveloperKey());

        // 应用Key，此项必填，由捷通华声提供
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_APP_KEY, AccountInfo
                .getInstance().getAppKey());

        // 配置日志参数
        String sdcardState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(sdcardState)) {
            String sdPath = Environment.getExternalStorageDirectory()
                    .getAbsolutePath();
            String packageName = this.getPackageName();

            String logPath = sdPath + File.separator + "sinovoice"
                    + File.separator + packageName + File.separator + "log"
                    + File.separator;

            // 日志文件地址
            File fileDir = new File(logPath);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }

            // 日志的路径，可选，如果不传或者为空则不生成日志
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_FILE_PATH, logPath);

            // 日志数目，默认保留多少个日志文件，超过则覆盖最旧的日志
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_FILE_COUNT, "5");

            // 日志大小，默认一个日志文件写多大，单位为K
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_FILE_SIZE, "1024");

            // 日志等级，0=无，1=错误，2=警告，3=信息，4=细节，5=调试，SDK将输出小于等于logLevel的日志信息
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_LEVEL, "5");
        }

        return initparam;
    }
    
    private void copyData(String dataPath, String dataAssetPath)
    {
    	// 创建资源路径
    	File file = new File(dataPath);
    	if (!file.exists()) {
    		file.mkdirs();
    	}
    	
    	AssetManager assetMgr = this.getResources().getAssets();
    	
    	try {
    		
    		String[] filesList = assetMgr.list(dataAssetPath);
    		for (String string : filesList) {
    			Log.v(TAG, string);
    			copyAssetFile(assetMgr, dataAssetPath + File.separator
    					+ string, dataPath + File.separator + string);
    		}
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    }
    
    private void copyAssetFile(AssetManager assetMgr, String src, String dst) {
    	if (assetMgr == null) {
    		throw new NullPointerException("Method param assetMgr is null.");
    	}

    	if (src == null) {
    		throw new NullPointerException("Method param src is null.");
    	}

    	if (dst == null) {
    		throw new NullPointerException("Method param dst is null.");
    	}
    	
    	InputStream is = null;
    	DataInputStream dis = null;
    	FileOutputStream fos = null;
    	DataOutputStream dos = null;
    	try {
    		is = assetMgr.open(src, AssetManager.ACCESS_RANDOM);
    		dis = new DataInputStream(is);

    		File file = new File(dst);
    		if (file.exists()) {
    			// file.delete();
    			return;
    		}
    		file.createNewFile();

    		fos = new FileOutputStream(file);
    		dos = new DataOutputStream(fos);
    		byte[] buffer = new byte[1024];
    		
    		int len = 0;
    		while ((len = dis.read(buffer, 0, buffer.length)) != -1) {
    			dos.write(buffer, 0, len);
    			dos.flush();
    		}
    	} catch (IOException e) {
    		e.printStackTrace();
    	} finally {
    		try {
    			if (dis != null) {
    				dis.close();
    				dis = null;
    			}

    			if (is != null) {
    				is.close();
    				is = null;
    			}

    			if (dos != null) {
    				dos.close();
    				dos = null;
    			}

    			if (fos != null) {
    				fos.close();
    				fos = null;
    			}
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    	}
    }

}




	
		

