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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.serenegiant.FaceDB;
import com.serenegiant.common.BaseActivity;
import com.serenegiant.facenet.Box;
import com.serenegiant.facenet.FaceFeature;
import com.serenegiant.facenet.Facenet;
import com.serenegiant.facenet.MTCNN;
import com.serenegiant.facenet.Utils;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.SimpleUVCCameraTextureView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Vector;

/**
 *  Created by caydencui on 2018/9/6.
 */
public final class CameraActivity extends BaseActivity implements OnClickListener, CameraDialog.CameraDialogParent,IFrameCallback {
	private static final String TAG="TAG";
	private final Object mSync = new Object();
    // for accessing USB and USB camera

    private USBMonitor mUSBMonitor;
	private UVCCamera mUVCCamera;
	private SimpleUVCCameraTextureView mUVCCameraView;
	// for open&start / stop&close camera preview
	private ImageButton mCameraButton;
	private ImageView iv_face;
	private TextView tv_result;
	private Button btn_camera,btn_register,btn_compare;
	private Surface mPreviewSurface;
	private  Facenet facenet;
	private MTCNN mtcnn;
	private double cmp;
	private String username;
	private Bitmap bitmap;
	private boolean isNeedCallBack=false;
	private static boolean isGettingFace = false;
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);
		mCameraButton = (ImageButton)findViewById(R.id.camera_button);
		iv_face=(ImageView)findViewById(R.id.iv_face);
		tv_result=(TextView)findViewById(R.id.text_view);

		btn_camera=(Button)findViewById(R.id.btn_camera);
		btn_register=(Button)findViewById(R.id.btn_register);
		btn_compare=(Button)findViewById(R.id.btn_compare);


		btn_camera.setOnClickListener(this);
		btn_register.setOnClickListener(this);
		btn_compare.setOnClickListener(this);



		mCameraButton.setOnClickListener(mOnClickListener);

		mUVCCameraView = (SimpleUVCCameraTextureView)findViewById(R.id.UVCCameraTextureView1);
		mUVCCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);

		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

		facenet=Facenet.getInstance();
		mtcnn=MTCNN.getInstance();
		FaceDB.getInstance().loadFaces();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()){
			case R.id.btn_camera:


				break;
			case R.id.btn_register:


//				FaceFeature ff1=facenet.recognizeImage(mBitmapFace1);
//				Log.d(TAG,"FaceFeature="+ff1.getFeature().length);
//				FaceDB.getInstance().addFace(username,ff1);

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

	@Override
	protected void onStart() {
		super.onStart();
		mUSBMonitor.register();
		synchronized (mSync) {
			if (mUVCCamera != null) {
				mUVCCamera.startPreview();
			}
		}
	}

	@Override
	protected void onStop() {
		synchronized (mSync) {
			if (mUVCCamera != null) {
				mUVCCamera.stopPreview();
			}
			if (mUSBMonitor != null) {
				mUSBMonitor.unregister();
			}
		}
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		synchronized (mSync) {
			releaseCamera();
			if (mToast != null) {
				mToast.cancel();
				mToast = null;
			}
			if (mUSBMonitor != null) {
				mUSBMonitor.destroy();
				mUSBMonitor = null;
			}
		}
		mUVCCameraView = null;
		mCameraButton = null;
		super.onDestroy();
	}

	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			synchronized (mSync) {
				if (mUVCCamera == null) {
					isGettingFace=true;
					CameraDialog.showDialog(CameraActivity.this);

				} else {
					releaseCamera();
				}
			}
		}
	};

	private Toast mToast;

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			Toast.makeText(CameraActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {

			Log.d("TAG","onConnect");
			releaseCamera();
			queueEvent(new Runnable() {
				@Override
				public void run() {
					final UVCCamera camera = new UVCCamera();
					camera.open(ctrlBlock);
					camera.setStatusCallback(new IStatusCallback() {
						@Override
						public void onStatus(final int statusClass, final int event, final int selector,
											 final int statusAttribute, final ByteBuffer data) {
							Log.d("TAG","onStatus(statusClass=" + statusClass
									+ "; " +
									"event=" + event + "; " +
									"selector=" + selector + "; " +
									"statusAttribute=" + statusAttribute + "; " +
									"data=...)");
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									final Toast toast = Toast.makeText(CameraActivity.this, "onStatus(statusClass=" + statusClass
											+ "; " +
											"event=" + event + "; " +
											"selector=" + selector + "; " +
											"statusAttribute=" + statusAttribute + "; " +
											"data=...)", Toast.LENGTH_SHORT);
									synchronized (mSync) {
										if (mToast != null) {
											mToast.cancel();
										}
										toast.show();
										mToast = toast;
									}
								}
							});
						}
					});
					camera.setFrameCallback(CameraActivity.this,UVCCamera.PIXEL_FORMAT_YUV420SP);
					camera.setButtonCallback(new IButtonCallback() {
						@Override
						public void onButton(final int button, final int state) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									final Toast toast = Toast.makeText(CameraActivity.this, "onButton(button=" + button + "; " +
											"state=" + state + ")", Toast.LENGTH_SHORT);
									synchronized (mSync) {
										if (mToast != null) {
											mToast.cancel();
										}
										mToast = toast;
										toast.show();
									}
								}
							});
						}
					});
//					camera.setPreviewTexture(camera.getSurfaceTexture());
					if (mPreviewSurface != null) {
						mPreviewSurface.release();
						mPreviewSurface = null;
					}
					try {
						camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
					} catch (final IllegalArgumentException e) {
						// fallback to YUV mode
						try {
							camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
						} catch (final IllegalArgumentException e1) {
							camera.destroy();
							return;
						}
					}
					final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
					if (st != null) {
						mPreviewSurface = new Surface(st);
						camera.setPreviewDisplay(mPreviewSurface);
//						camera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_RGB565/*UVCCamera.PIXEL_FORMAT_NV21*/);
						camera.startPreview();
					}
					synchronized (mSync) {
						mUVCCamera = camera;
					}
				}
			}, 0);
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			// XXX you should check whether the coming device equal to camera device that currently using
			releaseCamera();
		}

		@Override
		public void onDettach(final UsbDevice device) {
			Toast.makeText(CameraActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCancel(final UsbDevice device) {
		}
	};

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


	@Override
	public void onFrame(ByteBuffer frame) {
		/**
		 *According to each frame image, extract facial feature values, and then perform face matching
		 */
		try {
			Log.d(TAG,"~~~~~~~~~~~~~~~~~~~onFrame~~~~~~~~~~~~~~~");
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
					Log.d(TAG,">>>>>>>>>>score="+tempScore);
				}
				cmp=tempScore;

				//(显示人脸)
				Thread.sleep(1000);
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

	private synchronized void releaseCamera() {
		synchronized (mSync) {
			if (mUVCCamera != null) {
				try {
					mUVCCamera.setStatusCallback(null);
					mUVCCamera.setButtonCallback(null);
					mUVCCamera.close();
					mUVCCamera.destroy();
				} catch (final Exception e) {
					//
				}
				mUVCCamera = null;
			}
			if (mPreviewSurface != null) {
				mPreviewSurface.release();
				mPreviewSurface = null;
			}
		}
	}

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
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// FIXME
				}
			}, 0);
		}
	}

	// if you need frame data as byte array on Java side, you can use this callback method with UVCCamera#setFrameCallback
	// if you need to create Bitmap in IFrameCallback, please refer following snippet.
/*	final Bitmap bitmap = Bitmap.createBitmap(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, Bitmap.Config.RGB_565);
	private final IFrameCallback mIFrameCallback = new IFrameCallback() {
		@Override
		public void onFrame(final ByteBuffer frame) {
			frame.clear();
			synchronized (bitmap) {
				bitmap.copyPixelsFromBuffer(frame);
			}
			mImageView.post(mUpdateImageTask);
		}
	};
	
	private final Runnable mUpdateImageTask = new Runnable() {
		@Override
		public void run() {
			synchronized (bitmap) {
				mImageView.setImageBitmap(bitmap);
			}
		}
	}; */
}
