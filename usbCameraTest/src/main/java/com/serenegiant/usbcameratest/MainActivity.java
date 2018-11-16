/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameratest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.serenegiant.FaceApplication;
import com.serenegiant.FaceDB;
import com.serenegiant.common.BaseActivity;
import com.serenegiant.encoder.MediaAudioEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaMuxerWrapper;
import com.serenegiant.encoder.MediaVideoBufferEncoder;
import com.serenegiant.encoder.MediaVideoEncoder;
import com.serenegiant.facenet.Box;
import com.serenegiant.facenet.FaceFeature;
import com.serenegiant.facenet.Facenet;
import com.serenegiant.facenet.MTCNN;
import com.serenegiant.facenet.Utils;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;


import com.serenegiant.widget.CameraViewInterface;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import dou.utils.DisplayUtil;

public final class MainActivity extends BaseActivity implements OnClickListener,CameraDialog.CameraDialogParent,IFrameCallback {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "MainActivity";

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_WIDTH = 640;
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_HEIGHT = 480;
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     * 0:YUYV, other:MJPEG
     */
    private static final int PREVIEW_MODE = UVCCamera.FRAME_FORMAT_MJPEG;

	/**
	 * for accessing USB
	 */
	private USBMonitor mUSBMonitor;
	/**
	 * Handler to execute camera releated methods sequentially on private thread
	 */
	private AbstractUVCCameraHandler mCameraHandler;
	/**
	 * for camera preview display
	 */
	private CameraViewInterface mUVCCameraView;
	/**
	 * for open&start / stop&close camera preview
	 */
	private ToggleButton mCameraButton;
	/**
	 * button for start/stop recording
	 */
	private ImageButton mCaptureButton;


	private ImageView iv_face;
	private ImageView iv_image;
	private TextView tv_result;
	private Button btn_camera,btn_register,btn_compare;
	private EditText et_name;
	private Surface mPreviewSurface;
	private Facenet facenet;
	private MTCNN mtcnn;
	private double cmp;
	private String username;
	private Bitmap bitmap;
	private boolean isNeedCallBack=false;
	private static boolean isGettingFace = false;

	protected int iw = 0, ih;
	private float scale_bit=0.5f;
	private SurfaceView draw_view;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:");
		setContentView(R.layout.activity_main);
		mCameraButton = (ToggleButton)findViewById(R.id.camera_button);
		mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
		mCaptureButton = (ImageButton)findViewById(R.id.capture_button);


		iv_face=(ImageView)findViewById(R.id.iv_face);
		iv_image=(ImageView)findViewById(R.id.iv_image);
		tv_result=(TextView)findViewById(R.id.text_view);

		btn_camera=(Button)findViewById(R.id.btn_camera);
		btn_register=(Button)findViewById(R.id.btn_register);
		btn_compare=(Button)findViewById(R.id.btn_compare);
		et_name=(EditText)findViewById(R.id.et_name);




		btn_camera.setOnClickListener(this);
		btn_register.setOnClickListener(this);
		btn_compare.setOnClickListener(this);

		mCaptureButton.setOnClickListener(mOnClickListener);
		mCaptureButton.setVisibility(View.INVISIBLE);
		final View view = findViewById(R.id.camera_view);
		view.setOnLongClickListener(mOnLongClickListener);
		mUVCCameraView = (CameraViewInterface)view;
		mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);

		/**
		 * 人脸标记覆层
		 */
		draw_view = (SurfaceView) findViewById(R.id.pointView);
		draw_view.setZOrderOnTop(true);
		draw_view.getHolder().setFormat(PixelFormat.TRANSLUCENT);

		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
//		mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
//			2, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);

		mCameraHandler = AbstractUVCCameraHandler.createHandler(this, mUVCCameraView);
		facenet=Facenet.getInstance();
		mtcnn=MTCNN.getInstance();
		FaceDB.getInstance().loadFaces();


		int width = UVCCamera.DEFAULT_PREVIEW_WIDTH;
		int height =UVCCamera.DEFAULT_PREVIEW_HEIGHT;

		ih=UVCCamera.DEFAULT_PREVIEW_HEIGHT;
		if (width < height) {
			scale_bit = width / (float) ih;

		} else {
			scale_bit = height / (float) ih;
		}
		scale_bit=scale_bit+0.4312f;
		Log.d(TAG,"…………width:"+width+",height:"+height+"………………………………………………………………………………scale_bit …………………………………………………………………………………………:"+scale_bit);
		ViewGroup.LayoutParams params = draw_view.getLayoutParams();
		params.width = width;
		params.height = height;
		draw_view.requestLayout();
	}
	Bitmap mBitmapFace1;

	@Override
	public void onClick(View v) {
		switch (v.getId()){
			case R.id.btn_camera:
				File file=getOutputMediaFile();
				mCameraHandler.captureStill(file.getAbsolutePath());
				Log.d(TAG,"image saved success ");
				break;
			case R.id.btn_register:
				String username= et_name.getText().toString();
				if(TextUtils.isEmpty(username)){
					Toast.makeText(this,"请输入一个名字",Toast.LENGTH_SHORT).show();
					return;
				}
				FaceFeature ff1=facenet.recognizeImage(mBitmapFace1);
				FaceDB.getInstance().addFace(username,ff1);

				break;
			case R.id.btn_compare:
				if(!isNeedCallBack){
					//开始比对
					isNeedCallBack=true;
					btn_compare.setText("停止比对");
				}else{
					isNeedCallBack=false;
					btn_compare.setText("开始比对");
				}
				break;
		}
	}


	public static File getOutputMediaFile() {
		File imageFileDir =
				new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyTestImage");
		if (!imageFileDir.exists()) if (!imageFileDir.mkdirs()) {
			return null;
		}
		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File imageFile = new File(imageFileDir.getPath() + File.separator +
					"IMG_" + timeStamp + ".jpg");
		return imageFile;
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (DEBUG) Log.v(TAG, "onStart:");
		mUSBMonitor.register();
		if (mUVCCameraView != null)
			mUVCCameraView.onResume();
	}

	@Override
	protected void onStop() {
		if (DEBUG) Log.v(TAG, "onStop:");
		queueEvent(new Runnable() {
			@Override
			public void run() {
				mCameraHandler.close();
			}
		}, 0);
		if (mUVCCameraView != null)
			mUVCCameraView.onPause();
		setCameraButton(false);
		mCaptureButton.setVisibility(View.INVISIBLE);
		mUSBMonitor.unregister();
		super.onStop();
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
		if (mCameraHandler != null) {
			mCameraHandler.release();
			mCameraHandler = null;
		}
		if (mUSBMonitor != null) {
			mUSBMonitor.destroy();
			mUSBMonitor = null;
		}
		mUVCCameraView = null;
		mCameraButton = null;
		mCaptureButton = null;
		super.onDestroy();
	}



	protected void checkPermissionResult(final int requestCode, final String permission, final boolean result) {
		super.checkPermissionResult(requestCode, permission, result);
		if (!result && (permission != null)) {
			setCameraButton(false);
		}
	}

	/**
	 * event handler when click camera / capture button
	 */
	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			switch (view.getId()) {
			case R.id.capture_button:
				if (mCameraHandler.isOpened()) {
					if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
						if (!mCameraHandler.isRecording()) {
							mCaptureButton.setColorFilter(0xffff0000);	// turn red
							mCameraHandler.startRecording();
						} else {
							mCaptureButton.setColorFilter(0);	// return to default color
							mCameraHandler.stopRecording();
						}
					}
				}
				break;
			}
		}
	};

	private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener
		= new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
			switch (compoundButton.getId()) {
			case R.id.camera_button:
				if (isChecked && !mCameraHandler.isOpened()) {
					CameraDialog.showDialog(MainActivity.this);
				} else {
					mCameraHandler.close();
					mCaptureButton.setVisibility(View.INVISIBLE);
					setCameraButton(false);
				}
				break;
			}
		}
	};

	/**
	 * capture still image when you long click on preview image(not on buttons)
	 */
	private final OnLongClickListener mOnLongClickListener = new OnLongClickListener() {
		@Override
		public boolean onLongClick(final View view) {
			switch (view.getId()) {
			case R.id.camera_view:
				if (mCameraHandler.isOpened()) {
					if (checkPermissionWriteExternalStorage()) {
						mCameraHandler.captureStill();
					}
					return true;
				}
			}
			return false;
		}
	};

	private void setCameraButton(final boolean isOn) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mCameraButton != null) {
					try {
						mCameraButton.setOnCheckedChangeListener(null);
						mCameraButton.setChecked(isOn);
					} finally {
						mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
					}
				}
				if (!isOn && (mCaptureButton != null)) {
					mCaptureButton.setVisibility(View.INVISIBLE);
				}
			}
		}, 0);
	}

	private Surface mSurface;
	private void startPreview() {
		final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
		if (mSurface != null) {
			mSurface.release();
		}
		mSurface = new Surface(st);
		mCameraHandler.startPreview(mSurface);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mCaptureButton.setVisibility(View.VISIBLE);
			}
		});


	}



	@Override
	public void onFrame(ByteBuffer frame) {
		/**
		 *According to each frame image, extract facial feature values, and then perform face matching
		 */
		try {
//			Log.d(TAG,"~~~~~~~~~~~~~~~~~~~onFrame~~~~~~~~~~~~~~~");
			byte[] data=new byte[frame.remaining()];
			frame.get(data);
			int[] rgb=new int[UVCCamera.DEFAULT_PREVIEW_WIDTH*UVCCamera.DEFAULT_PREVIEW_HEIGHT];
			decodeYUV420SP(rgb,data,UVCCamera.DEFAULT_PREVIEW_WIDTH,UVCCamera.DEFAULT_PREVIEW_HEIGHT);
//			bitmap = Bitmap.createBitmap(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT,Bitmap.Config.RGB_565);
			Bitmap bitmap = Bitmap.createBitmap(rgb, UVCCamera.DEFAULT_PREVIEW_WIDTH,
					UVCCamera.DEFAULT_PREVIEW_HEIGHT, Bitmap.Config.RGB_565);
			bitmap = comp(bitmap);
			if(isNeedCallBack) {
//				ImageUtils.saveImageData(bitmabToBytes(bitmap));
				Bitmap bm1= Utils.copyBitmap(bitmap);
				Vector<Box> boxes=mtcnn.detectFaces(bitmap,40);
				drawAnim(boxes,draw_view,1,1,"");
				if (boxes.size()==0) return ;
				for (int i=0;i<boxes.size();i++) Utils.drawBox(bitmap,boxes.get(i),1+bitmap.getWidth()/500 );
				Log.i("Main","[*]boxNum："+boxes.size());
				Box box=boxes.get(0);
				Rect rect1=box.transform2Rect();

				//MTCNN检测到的人脸框，再上下左右扩展margin个像素点，再放入facenet中。
				int margin=20; //20这个值是facenet中设置的。自己应该可以调整。
				Utils.rectExtend(bitmap,rect1,margin);
				//要比较的两个人脸，加厚Rect
				Utils.drawRect(bitmap,rect1,1+bitmap.getWidth()/100 );
				//(2)裁剪出人脸(只取第一张)
				final Bitmap face1=Utils.crop(bitmap,rect1);

				FaceFeature ff1=facenet.recognizeImage(face1);
				if(null==ff1||ff1.getFeature().length<0)return;
				List<FaceDB.FaceRegist> mResgist =FaceDB.getInstance().mRegister;
				double tempScore=-1;
				for(int i=0;i<mResgist.size();i++){
					FaceDB.FaceRegist faceRegist= mResgist.get(i);
					double temp= ff1.compare(faceRegist.faceFeature);

					if(tempScore==-1){
						tempScore=temp;//第一次直接fuzhi
						username=faceRegist.mName;
					}else{
						if(tempScore>temp){
							username=faceRegist.mName;
							tempScore=temp;
						}
					}
					Log.d(TAG,">>>>>>>>>>temp="+temp+",tempScore="+tempScore);
				}
				cmp=tempScore;

				//(显示人脸)
//				Thread.sleep(1000);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						iv_face.setImageBitmap(face1);
						tv_result.setText(String.format("名字:%s  \n相似度 :  %.4f", username,cmp) );
					}
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}


	protected void drawAnim(Vector<Box> faces, SurfaceView outputView, float scale_bit, int cameraId, String fps) {
		Paint paint=new Paint();
		Canvas canvas = ((SurfaceView) outputView).getHolder().lockCanvas();
		if(canvas!=null){
			try{
				int viewH=outputView.getHeight();
				int viewW=outputView.getWidth();
				canvas.drawColor(0, PorterDuff.Mode.CLEAR);
				if(faces==null||faces.size()==0)return;
				for(int i=0;i<faces.size();i++){
					paint.setColor(0x44ffffff);
					int size = DisplayUtil.dip2px(this, 3);

					paint.setStrokeWidth(size);
					paint.setStyle(Paint.Style.STROKE);

					Box box=faces.get(i);
					float[] rect=box.transform2float();
					float x1 = viewW - rect[0] * scale_bit - rect[2] * scale_bit;
					if (cameraId == (FaceApplication.yu ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK))
						x1 = rect[0] * scale_bit;
					float y1 = rect[1] * scale_bit;

					float rect_width = rect[2] * scale_bit;

//                    float x_ = y1;
//                    float y_ = x1;
//                    y_ = viewH - y_ - rect_width;
//                    RectF rectf = new RectF(x_, y_, x_ + rect_width, y_ + rect_width);


					//draw rect
					RectF rectf = new RectF(x1, y1, x1 + rect_width, y1 + rect_width);
					canvas.drawRect(rectf, paint);

//                    DLog.d(rect[0] + " : " + rect[1] + " : " + rect[2] + ": " + rect[3]);
					//draw grid
					int line = 10;
					int per_line = (int) (rect_width / (line + 1));
					int smailSize = DisplayUtil.dip2px(this, 1.5f);
					paint.setStrokeWidth(smailSize);
					for (int j = 1; j < line + 1; j++) {
						canvas.drawLine(x1 + per_line * j, y1, x1 + per_line * j, y1 + rect_width, paint);
						canvas.drawLine(x1, y1 + per_line * j, x1 + rect_width, y1 + per_line * j, paint);
					}

					paint.setStrokeWidth(size);
					paint.setColor(Color.WHITE);
//                    注意前置后置摄像头问题
					float x2 = viewW - rect[0] * scale_bit - rect[2] * scale_bit;
					if (cameraId == (FaceApplication.yu ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK))
						x2 = rect[0] * scale_bit;
					float y2 = rect[1] * scale_bit;

					float length = rect[3] * scale_bit / 5;
					float width = rect[3] * scale_bit;
					float heng = size / 2;
					canvas.drawLine(x2 - heng, y2, x2 + length, y2, paint);
					canvas.drawLine(x2, y2 - heng, x2, y2 + length, paint);

					x2 = x2 + width;
					canvas.drawLine(x2 + heng, y2, x2 - length, y2, paint);
					canvas.drawLine(x2, y2 - heng, x2, y2 + length, paint);

					y2 = y2 + width;
					canvas.drawLine(x2 + heng, y2, x2 - length, y2, paint);
					canvas.drawLine(x2, y2 + heng, x2, y2 - length, paint);

					x2 = x2 - width;
					canvas.drawLine(x2 - heng, y2, x2 + length, y2, paint);
					canvas.drawLine(x2, y2 + heng, x2, y2 - length, paint);



				}

			}catch (Exception e){
				e.printStackTrace();
			} finally {
				((SurfaceView) outputView).getHolder().unlockCanvasAndPost(canvas);
			}
		}
	}

	protected void drawAnim1(Vector<Box> faces, SurfaceView outputView, float scale_bit, int cameraId, String fps) {
		Paint paint=new Paint();
		Canvas canvas = ((SurfaceView) outputView).getHolder().lockCanvas();
		if(canvas!=null){
			try{
				int viewH=outputView.getHeight();
				int viewW=outputView.getWidth();
				canvas.drawColor(0, PorterDuff.Mode.CLEAR);
				if(faces==null||faces.size()==0)return;
				for(int i=0;i<faces.size();i++){
					paint.setColor(0x44ffffff);
					int size = DisplayUtil.dip2px(this, 3);

					paint.setStrokeWidth(size);
					paint.setStyle(Paint.Style.STROKE);

					Box box=faces.get(i);
					Rect rect1=box.transform2Rect();
//					float x1 = viewW - rect1.left * scale_bit - rect1.right * scale_bit;
					float x1 = rect1.left* scale_bit;
					float y1 = rect1.top* scale_bit;

					float rect_width = rect1.right  * scale_bit;
					//draw rect
					RectF rectf = new RectF(x1, y1, x1 + rect_width, y1 + rect_width);
					canvas.drawRect(rectf, paint);


					//draw grid
					int line = 10;
					int per_line = (int) (rect_width / (line + 1));
					int smailSize = DisplayUtil.dip2px(this, 1.5f);
					paint.setStrokeWidth(smailSize);
					for (int j = 1; j < line + 1; j++) {
						canvas.drawLine(x1 + per_line * j, y1, x1 + per_line * j, y1 + rect_width, paint);
						canvas.drawLine(x1, y1 + per_line * j, x1 + rect_width, y1 + per_line * j, paint);
					}

					paint.setStrokeWidth(size);
					paint.setColor(Color.WHITE);

//                    注意前置后置摄像头问题
//					float x2 = viewW - rect1.left * scale_bit - rect1.right * scale_bit;
					float x2 = rect1.left * scale_bit;
//					if (cameraId == (BaseApplication.yu ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK))
//						x2 = rect[0] * scale_bit;
					float y2 = rect1.top* scale_bit;

					float length = rect1.bottom * scale_bit / 5;
					float width = rect1.bottom* scale_bit;
					float heng = size / 2;
					canvas.drawLine(x2 - heng, y2, x2 + length, y2, paint);
					canvas.drawLine(x2, y2 - heng, x2, y2 + length, paint);

					x2 = x2 + width;
					canvas.drawLine(x2 + heng, y2, x2 - length, y2, paint);
					canvas.drawLine(x2, y2 - heng, x2, y2 + length, paint);

					y2 = y2 + width;
					canvas.drawLine(x2 + heng, y2, x2 - length, y2, paint);
					canvas.drawLine(x2, y2 + heng, x2, y2 - length, paint);

					x2 = x2 - width;
					canvas.drawLine(x2 - heng, y2, x2 + length, y2, paint);
					canvas.drawLine(x2, y2 + heng, x2, y2 - length, paint);


				}

			}catch (Exception e){
				e.printStackTrace();
			} finally {
				((SurfaceView) outputView).getHolder().unlockCanvasAndPost(canvas);
			}
		}
	}

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			if (DEBUG) Log.v(TAG, "onConnect:");
			mCameraHandler.open(ctrlBlock);
			startPreview();
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG, "onDisconnect:");
			if (mCameraHandler != null) {
				mCameraHandler.close();
				setCameraButton(false);
			}
		}
		@Override
		public void onDettach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCancel(final UsbDevice device) {
		}
	};

	/**
	 * to access from CameraDialog
	 * @return
	 */
	@Override
	public USBMonitor getUSBMonitor() {
		return mUSBMonitor;
	}

	@Override
	public void onDialogResult(boolean canceled) {
		if (canceled) {
			setCameraButton(false);
		}
	}

	public void showImage(File file){
		Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());
		Bitmap bm1= Utils.copyBitmap(bitmap);
		Vector<Box> boxes=mtcnn.detectFaces(bitmap,40);
		if (boxes.size()==0) return ;
		for (int i=0;i<boxes.size();i++) Utils.drawBox(bitmap,boxes.get(i),1+bitmap.getWidth()/500 );
		Log.i("Main","[*]boxNum"+boxes.size());
		Rect rect1=boxes.get(0).transform2Rect();
		//MTCNN检测到的人脸框，再上下左右扩展margin个像素点，再放入facenet中。
		int margin=20; //20这个值是facenet中设置的。自己应该可以调整。
		Utils.rectExtend(bitmap,rect1,margin);
		//要比较的两个人脸，加厚Rect
		Utils.drawRect(bitmap,rect1,1+bitmap.getWidth()/100 );
		//(2)裁剪出人脸(只取第一张)
		mBitmapFace1=Utils.crop(bitmap,rect1);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				iv_image.setImageBitmap(mBitmapFace1);
			}
		});
	}

	/**
	 * Handler class to execute camera releated methods sequentially on private thread
	 */
	private static final class AbstractUVCCameraHandler extends Handler {
		private static final int MSG_OPEN = 0;
		private static final int MSG_CLOSE = 1;
		private static final int MSG_PREVIEW_START = 2;
		private static final int MSG_PREVIEW_STOP = 3;
		private static final int MSG_CAPTURE_STILL = 4;
		private static final int MSG_CAPTURE_START = 5;
		private static final int MSG_CAPTURE_STOP = 6;
		private static final int MSG_MEDIA_UPDATE = 7;
		private static final int MSG_RELEASE = 9;
		private static final int MSG_CAPTURE_SUCCESS = 10;
		private volatile boolean mReleased;
		private final WeakReference<CameraThread> mWeakThread;

		public static final AbstractUVCCameraHandler createHandler(final MainActivity parent, final CameraViewInterface cameraView) {
			final CameraThread thread = new CameraThread(parent, cameraView);
			thread.start();
			return thread.getHandler();
		}

		private AbstractUVCCameraHandler(final CameraThread thread) {
			mWeakThread = new WeakReference<CameraThread>(thread);
		}

		public void release(){
			mReleased = true;
			close();
			sendEmptyMessage(MSG_RELEASE);
		}
		protected boolean isReleased() {
			final CameraThread thread = mWeakThread.get();
			return mReleased || (thread == null);
		}
		public boolean isOpened() {
			final CameraThread thread = mWeakThread.get();
			return thread != null && thread.isOpened();
		}

		public boolean isRecording() {
			final CameraThread thread = mWeakThread.get();
			return thread != null && thread.isRecording();
		}

		public void open(final UsbControlBlock ctrlBlock) {
			sendMessage(obtainMessage(MSG_OPEN, ctrlBlock));
		}

		public void close() {
			stopPreview();
			sendEmptyMessage(MSG_CLOSE);
		}

		public void startPreview(final Surface sureface) {
			if (sureface != null)
				sendMessage(obtainMessage(MSG_PREVIEW_START, sureface));
		}

		public void stopPreview() {
			stopRecording();
			final CameraThread thread = mWeakThread.get();
			if (thread == null) return;
			synchronized (thread.mSync) {
				sendEmptyMessage(MSG_PREVIEW_STOP);
				// wait for actually preview stopped to avoid releasing Surface/SurfaceTexture
				// while preview is still running.
				// therefore this method will take a time to execute
				try {
					thread.mSync.wait();
				} catch (final InterruptedException e) {
				}
			}
		}

		public void captureStill() {
			sendEmptyMessage(MSG_CAPTURE_STILL);
		}

		protected void captureStill(final String path) {

			sendMessage(obtainMessage(MSG_CAPTURE_STILL, path));
		}

		public void startRecording() {
			sendEmptyMessage(MSG_CAPTURE_START);
		}

		public void stopRecording() {
			sendEmptyMessage(MSG_CAPTURE_STOP);
		}

		@Override
		public void handleMessage(final Message msg) {
			final CameraThread thread = mWeakThread.get();
			if (thread == null) return;
			switch (msg.what) {
			case MSG_OPEN:
				thread.handleOpen((UsbControlBlock)msg.obj);
				break;
			case MSG_CLOSE:
				thread.handleClose();
				break;
			case MSG_PREVIEW_START:
				thread.handleStartPreview((Surface)msg.obj);
				break;
			case MSG_PREVIEW_STOP:
				thread.handleStopPreview();
				break;
			case MSG_CAPTURE_STILL:
				thread.handleCaptureStill((String)msg.obj);
				break;
			case MSG_CAPTURE_SUCCESS:

				thread.cropImage((String)msg.obj);
				break;
			case MSG_CAPTURE_START:
				thread.handleStartRecording();
				break;
			case MSG_CAPTURE_STOP:
				thread.handleStopRecording();
				break;
			case MSG_MEDIA_UPDATE:
				thread.handleUpdateMedia((String)msg.obj);
				break;
			case MSG_RELEASE:
				thread.handleRelease();
				break;
			default:
				throw new RuntimeException("unsupported message:what=" + msg.what);
			}
		}


		private static final class CameraThread extends Thread {
			private static final String TAG_THREAD = "Activity-CameraThread";
			private final Object mSync = new Object();
			private final WeakReference<MainActivity> mWeakParent;
			private final WeakReference<CameraViewInterface> mWeakCameraView;
			private boolean mIsRecording;
			/**
			 * shutter sound
			 */
			private SoundPool mSoundPool;
			private int mSoundId;
			private AbstractUVCCameraHandler mHandler;
			/**
			 * for accessing UVC camera
			 */
			private UVCCamera mUVCCamera;
			/**
			 * muxer for audio/video recording
			 */
			private MediaMuxerWrapper mMuxer;
			/**
			 * for video recording
			 */
			private MediaVideoBufferEncoder mVideoEncoder;

			private CameraThread(final MainActivity parent, final CameraViewInterface cameraView) {
				super("CameraThread");
				mWeakParent = new WeakReference<MainActivity>(parent);
				mWeakCameraView = new WeakReference<CameraViewInterface>(cameraView);
				loadShutterSound(parent);
			}

			@Override
			protected void finalize() throws Throwable {
				Log.i(TAG, "CameraThread#finalize");
				super.finalize();
			}

			public AbstractUVCCameraHandler getHandler() {
				if (DEBUG) Log.v(TAG_THREAD, "getHandler:");
				synchronized (mSync) {
					if (mHandler == null)
					try {
						mSync.wait();
					} catch (final InterruptedException e) {
					}
				}
				return mHandler;
			}

			public boolean isOpened() {
				return mUVCCamera != null;
			}

			public boolean isRecording() {
				return (mUVCCamera != null) && (mMuxer != null);
			}

			public void handleOpen(final UsbControlBlock ctrlBlock) {
				if (DEBUG) Log.v(TAG_THREAD, "handleOpen:");
				handleClose();
				mUVCCamera = new UVCCamera();
				mUVCCamera.open(ctrlBlock);
				if (DEBUG) Log.i(TAG, "supportedSize:" + mUVCCamera.getSupportedSize());
			}

			public void handleClose() {
				if (DEBUG) Log.v(TAG_THREAD, "handleClose:");
				handleStopRecording();
				if (mUVCCamera != null) {
					mUVCCamera.stopPreview();
					mUVCCamera.destroy();
					mUVCCamera = null;
				}
			}

			public void handleStartPreview(final Surface surface) {
				if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:");
				if (mUVCCamera == null){
					Log.v(TAG_THREAD, "mUVCCamera is null null");
					return;
				}

				final MainActivity parent = mWeakParent.get();

				try {
					mUVCCamera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);
					mUVCCamera.setFrameCallback(parent, UVCCamera.PIXEL_FORMAT_YUV420SP);
				} catch (final IllegalArgumentException e) {
					try {
						// fallback to YUV mode
						mUVCCamera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
					} catch (final IllegalArgumentException e1) {
						handleClose();
					}
				}
				if (mUVCCamera != null) {

					mUVCCamera.setPreviewDisplay(surface);
					mUVCCamera.startPreview();

				}

				if (DEBUG) Log.v(TAG_THREAD, "^^^^^^^^^^^^^^^^^^^^handleStartPreview:");
			}

			public void handleStopPreview() {
				if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:");
				if (mUVCCamera != null) {
					mUVCCamera.stopPreview();
				}
				synchronized (mSync) {
					mSync.notifyAll();
				}
			}

			public void cropImage(String path){
				File file=new File(path);
				if(!file.exists())return;
				MainActivity parent = mWeakParent.get();
				parent.showImage(file);
			}

			public void handleCaptureStill(final String path) {
				if (DEBUG) Log.v(TAG_THREAD, "handleCaptureStill:");
				final Activity parent = mWeakParent.get();
				if (parent == null) return;
				mSoundPool.play(mSoundId, 0.2f, 0.2f, 0, 0, 1.0f);	// play shutter sound
				try {
					final Bitmap bitmap = mWeakCameraView.get().captureStillImage();
					// get buffered output stream for saving a captured still image as a file on external storage.
					// the file name is came from current time.
					// You should use extension name as same as CompressFormat when calling Bitmap#compress.
					final File outputFile = TextUtils.isEmpty(path)
							? MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png")
							: new File(path);
					final BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
					try {
						try {
							bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
							os.flush();
							mHandler.sendMessage(mHandler.obtainMessage(MSG_CAPTURE_SUCCESS, outputFile.getPath()));
						} catch (final IOException e) {
						}
					} finally {
						os.close();
					}
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}

			public void handleStartRecording() {
				if (DEBUG) Log.v(TAG_THREAD, "handleStartRecording:");
				try {
					if ((mUVCCamera == null) || (mMuxer != null)) return;
					mMuxer = new MediaMuxerWrapper(".mp4");	// if you record audio only, ".m4a" is also OK.
					// for video capturing using MediaVideoEncoder
//					mVideoEncoder = new MediaVideoBufferEncoder(mMuxer, mMediaEncoderListener);
					if (true) {
						// for audio capturing
						new MediaAudioEncoder(mMuxer, mMediaEncoderListener);
					}
					mMuxer.prepare();
					mMuxer.startRecording();
					mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_NV21);
				} catch (final IOException e) {
					Log.e(TAG, "startCapture:", e);
				}
			}

			public void handleStopRecording() {
				if (DEBUG) Log.v(TAG_THREAD, "handleStopRecording:mMuxer=" + mMuxer);
				mVideoEncoder = null;
				if (mMuxer != null) {
					mMuxer.stopRecording();
					mMuxer = null;
					// you should not wait here
				}
				if (mUVCCamera != null)
					mUVCCamera.setFrameCallback(null, 0);
			}

			public void handleUpdateMedia(final String path) {
				if (DEBUG) Log.v(TAG_THREAD, "handleUpdateMedia:path=" + path);
				final MainActivity parent = mWeakParent.get();
				if (parent != null && parent.getApplicationContext() != null) {
					try {
						if (DEBUG) Log.i(TAG, "MediaScannerConnection#scanFile");
						MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{ path }, null, null);
					} catch (final Exception e) {
						Log.e(TAG, "handleUpdateMedia:", e);
					}
					if (parent.isDestroyed())
						handleRelease();
				} else {
					Log.w(TAG, "MainActivity already destroyed");
					// give up to add this movice to MediaStore now.
					// Seeing this movie on Gallery app etc. will take a lot of time.
					handleRelease();
				}
			}

			public void handleRelease() {
				if (DEBUG) Log.v(TAG_THREAD, "handleRelease:");
 				handleClose();
				if (!mIsRecording)
					Looper.myLooper().quit();
			}

			private final IFrameCallback mIFrameCallback = new IFrameCallback() {
				@Override
				public void onFrame(final ByteBuffer frame) {
					if (mVideoEncoder != null) {
						mVideoEncoder.frameAvailableSoon();
						mVideoEncoder.encode(frame);
					}
				}
			};

			private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
				@Override
				public void onPrepared(final MediaEncoder encoder) {
					if (DEBUG) Log.v(TAG, "onPrepared:encoder=" + encoder);
					mIsRecording = true;
				}

				@Override
				public void onStopped(final MediaEncoder encoder) {
					if (DEBUG) Log.v(TAG_THREAD, "onStopped:encoder=" + encoder);
					if (encoder instanceof MediaVideoEncoder)
					try {
						mIsRecording = false;
						final MainActivity parent = mWeakParent.get();
						final String path = encoder.getOutputPath();
						if (!TextUtils.isEmpty(path)) {
							mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_MEDIA_UPDATE, path), 1000);
						} else {
							if (parent == null || parent.isDestroyed()) {
								handleRelease();
							}
						}
					} catch (final Exception e) {
						Log.e(TAG, "onPrepared:", e);
					}
				}
			};

			/**
			 * prepare and load shutter sound for still image capturing
			 */
			@SuppressWarnings("deprecation")
			private void loadShutterSound(final Context context) {
		    	// get system stream type using refrection
		        int streamType;
		        try {
		            final Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
		            final Field sseField = audioSystemClass.getDeclaredField("STREAM_SYSTEM_ENFORCED");
		            streamType = sseField.getInt(null);
		        } catch (final Exception e) {
		        	streamType = AudioManager.STREAM_SYSTEM;	// set appropriate according to your app policy
		        }
		        if (mSoundPool != null) {
		        	try {
		        		mSoundPool.release();
		        	} catch (final Exception e) {
		        	}
		        	mSoundPool = null;
		        }
		        // load sutter sound from resource
			    mSoundPool = new SoundPool(2, streamType, 0);
			    mSoundId = mSoundPool.load(context, R.raw.camera_click, 1);
			}

			@Override
			public void run() {
				Looper.prepare();
				synchronized (mSync) {
					mHandler = new AbstractUVCCameraHandler(this);
					mSync.notifyAll();
				}
				Looper.loop();
				synchronized (mSync) {
					mHandler = null;
					mSoundPool.release();
					mSoundPool = null;
					mSync.notifyAll();
				}
			}
		}
	}

	/**
	 * yuv转420sp
	 * @param rgb
	 * @param yuv420sp
	 * @param width
	 * @param height
	 */
	static public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
		final int frameSize = width * height;

		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0) y = 0;
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}

				int y1192 = 1192 * y;
				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);

				if (r < 0) r = 0; else if (r > 262143) r = 262143;
				if (g < 0) g = 0; else if (g > 262143) g = 262143;
				if (b < 0) b = 0; else if (b > 262143) b = 262143;

				rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
			}
		}
	}



	public byte[] bitmabToBytes(Bitmap bitmap){
		//将图片转化为位图
		///Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
		int size = bitmap.getWidth() * bitmap.getHeight() * 4;
		//创建一个字节数组输出流,流的大小为size
		ByteArrayOutputStream baos= new ByteArrayOutputStream(size);
		try {
			//设置位图的压缩格式，质量为100%，并放入字节数组输出流中
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
			//将字节数组输出流转化为字节数组byte[]
			byte[] imagedata = baos.toByteArray();
			return imagedata;
		}catch (Exception e){
		}finally {
			try {
				bitmap.recycle();
				baos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return new byte[0];
	}


	private Bitmap comp(Bitmap image) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
		if (baos.toByteArray().length / 1024 >
				1024) {//判断如果图片大于1M,进行压缩避免在生成图片（BitmapFactory.decodeStream）时溢出
			baos.reset();//重置baos即清空baos
			image.compress(Bitmap.CompressFormat.JPEG, 50, baos);//这里压缩50%，把压缩后的数据存放到baos中
		}
		ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
		BitmapFactory.Options newOpts = new BitmapFactory.Options();
		//开始读入图片，此时把options.inJustDecodeBounds 设回true了
		newOpts.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(isBm, null, newOpts);
		newOpts.inJustDecodeBounds = false;
		int w = newOpts.outWidth;
		int h = newOpts.outHeight;
		float hh = 720f;
		float ww = 1280f;
		//缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
		int be = 1;//be=1表示不缩放
		if (w > h && w > ww) {//如果宽度大的话根据宽度固定大小缩放
			be = (int) (newOpts.outWidth / ww);
		} else if (w < h && h > hh) {//如果高度高的话根据宽度固定大小缩放
			be = (int) (newOpts.outHeight / hh);
		}
		if (be <= 0) {
			be = 1;
		}
		newOpts.inSampleSize = be;//设置缩放比例
		newOpts.inPreferredConfig = Bitmap.Config.RGB_565;//降低图片从ARGB888到RGB565
		//重新读入图片，注意此时已经把options.inJustDecodeBounds 设回false了
		isBm = new ByteArrayInputStream(baos.toByteArray());
		bitmap = BitmapFactory.decodeStream(isBm, null, newOpts);
		return compressImage(bitmap);//压缩好比例大小后再进行质量压缩
	}

	private Bitmap compressImage(Bitmap image) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		image.compress(Bitmap.CompressFormat.JPEG, 100, baos);//质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
		int options = 100;
		while (baos.toByteArray().length / 1024 > 100) {    //循环判断如果压缩后图片是否大于100kb,大于继续压缩
			baos.reset();//重置baos即清空baos
			options -= 10;//每次都减少10
			image.compress(Bitmap.CompressFormat.JPEG, options, baos);//这里压缩options%，把压缩后的数据存放到baos中

		}
		ByteArrayInputStream isBm = new ByteArrayInputStream(
				baos.toByteArray());//把压缩后的数据baos存放到ByteArrayInputStream中
		Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);//把ByteArrayInputStream数据生成图片
		return bitmap;
	}


	/**
	 * 将拍下来的照片存放在SD卡中
	 * @param data
	 * @throws IOException
	 */
	public static String saveToSDCard(byte[] data) throws IOException {
		Date date = new Date();
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss"); // 格式化时间
		String filename = format.format(date) + ".jpg";
		// File fileFolder = new File(getTrueSDCardPath()
		//       + "/rebot/cache/");

		File fileFolder = new File("/mnt/internal_sd"+ "/rebot/cache/");
//		File fileFolder = ImageUtils.getOutputMediaFile(ImageUtils.MEDIA_TYPE_IMAGE);
		if (!fileFolder.exists()) {
			fileFolder.mkdir();
		}
		File jpgFile = new File(fileFolder, filename);
		FileOutputStream outputStream = new FileOutputStream(jpgFile); // 文件输出流
		outputStream.write(data); // 写入sd卡中
		outputStream.close(); // 关闭输出流
		return jpgFile.getName().toString();
	}


	public Bitmap bytes2Bimap(byte[] b) {
		if (b.length != 0) {
			return BitmapFactory.decodeByteArray(b, 0, b.length);
		} else {
			return null;
		}
	}
}
