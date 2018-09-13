UVCCamera
=========
这里是通过uvccamera读取USB摄像头，然后在返回的方法
进行回调，使用mtcnn提取人脸特征值，并且使用facenet模型做人脸比对

```
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
          					Log.d(TAG,">>>>>>>>>>temp="+temp+",tempScore="+tempScore);
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
```



