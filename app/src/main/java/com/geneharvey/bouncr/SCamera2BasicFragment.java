/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.geneharvey.bouncr;

import android.Manifest;
import android.app.*;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.support.v8.renderscript.*;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.*;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.camera.*;
import com.xxxyyy.testcamera2.ScriptC_yuv420888;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SCamera2BasicFragment extends Fragment
	   implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback
{
	private static final int MAX_IMAGES = 5;
	/**
	 * Conversion from screen rotation to JPEG orientation.
	 */
	private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
	private static final int REQUEST_CAMERA_PERMISSION = 1;
	private static final String FRAGMENT_DIALOG = "dialog";
	/**
	 * Tag for the {@link Log}.
	 */
	private static final String TAG = "Camera2BasicFragment";
	/**
	 * Camera state: Showing camera preview.
	 */
	private static final int STATE_PREVIEW = 0;
	/**
	 * Camera state: Waiting for the focus to be locked.
	 */
	private static final int STATE_WAITING_LOCK = 1;
	/**
	 * Camera state: Waiting for the exposure to be precapture state.
	 */
	private static final int STATE_WAITING_PRECAPTURE = 2;
	/**
	 * Camera state: Waiting for the exposure state to be something other than precapture.
	 */
	private static final int STATE_WAITING_NON_PRECAPTURE = 3;
	/**
	 * Camera state: Picture was taken.
	 */
	private static final int STATE_PICTURE_TAKEN = 4;
	private static int imageNumber = 0;
	private static String datapath = "";
	private static TessBaseAPI mTess;
	private static MainActivity main;
	private static boolean canOCR = true;
	private static int totalImages = 0;
	private static HashMap<String, Guest> guestList;
	private static Pattern guestPattern;
	private static boolean canMatch = false;
	private static int numGuests;
	private static Matrix matrix;

	private static int marginTop;
	private static int cropRectWidth;
	private static int cropRectHeight;

	//private static double ratioImgDisplayWidth;
	//private static double ratioImgDisplayHeight;

	private static int displayWidth;
	private static int displayHeight;

	private static int cropRectX;
	private static int cropRectY;
	private static int cropRectWidthPx;
	private static int cropRectHeightPx;


	static
	{
		ORIENTATIONS.append(Surface.ROTATION_0, 0);
		ORIENTATIONS.append(Surface.ROTATION_90, 90);
		ORIENTATIONS.append(Surface.ROTATION_180, 180);
		ORIENTATIONS.append(Surface.ROTATION_270, 270);
	}

	/**
	 * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
	 * still image is ready to be saved.
	 */
	private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener()
	{
		@Override
		public void onImageAvailable(ImageReader reader)
		{
			if(imageNumber == 5 && totalImages <= MAX_IMAGES)
			{
				Image image = null;
				try
				{
					image = reader.acquireNextImage();
					totalImages++;
					if(image != null)
					{
						final Bitmap bmp = YUV_420_888_toRGB(image, image.getWidth(), image.getHeight());
						image.close();
						totalImages--;
						if(canOCR)
						{
							canOCR = false;
							final Thread OCRAction = new Thread()
							{
								@Override
								public void run()
								{
									//System.out.println("createBitmap(bmp, " + cropRectY + ", " + cropRectX + ", " + cropRectHeightPx + ", " + cropRectWidthPx + ", matrix, true)");
									setmTessImage(
										   Bitmap.createBitmap(bmp, cropRectY, cropRectX,
														   cropRectHeightPx, cropRectWidthPx, matrix,
														   true)
									);//Arguments are contrary since the image is cropped before rotation
									final String OCRresult = mTess.getUTF8Text();
									canOCR = true;
									changeButtonText(OCRresult);
									if(OCRresult != null && canMatch)
									{
										Matcher match = guestPattern.matcher(OCRresult);
										if(match.find())
										{
											System.out.println("found");
											main.runOnUiThread(new Runnable()
											{
												Matcher match;
												String OCRresult = "";

												private Runnable init(Matcher match)
												{
													this.match = match;
													return this;
												}

												public void run()
												{
													do
													{
														try
														{
															OCRresult = match.group();
															System.out.println(OCRresult);
															Guest guest = guestList.get(OCRresult);
															if(!guest.getCheckedOff())
															{
																Button OCRTextView = (Button)main.findViewById(
																	   R.id.checkin);
																OCRTextView.setText(OCRresult);
																greenSwitch();
																guest.setCheckedOff(true);
															}
														}
														catch(NullPointerException e)
														{
															e.printStackTrace();
														}
													}
													while(match.find());
												}
											}.init(match));
										}
									}
								}
							};
							OCRAction.start();
						}
					}
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
				finally
				{
					if(image != null)
					{
						image.close();
						totalImages--;
						imageNumber = 0;
					}
					// make sure to close image
				}
			}
			else
			{
				Image image = reader.acquireNextImage();
				if(image != null && imageNumber < 5)
				{
					image.close();
					if(imageNumber < 5)
					{
						imageNumber++;
					}
				}
			}
		}
	};
	/**
	 * ID of the current {@link SCameraDevice}.
	 */
	private String mCameraId;
	/**
	 * An {@link AutoFitTextureView} for camera preview.
	 */
	private AutoFitTextureView mTextureView;
	/**
	 * A {@link SCameraCaptureSession } for camera preview.
	 */
	private SCamera mSCamera;
	private SCameraManager mSCameraManager;
	private SCameraDevice mSCameraDevice;
	private SCameraCaptureSession mSCameraSession;
	private SCameraCharacteristics mCharacteristics;
	private SCaptureRequest.Builder mPreviewBuilder;
	/**
	 * The {@link Size} of camera preview.
	 */
	private Size mPreviewSize;
	/**
	 * An additional thread for running tasks that shouldn't block the UI.
	 */
	private HandlerThread mBackgroundThread;
	/**
	 * A {@link Handler} for running tasks in the background.
	 */
	private Handler mBackgroundHandler;
	/**
	 * An {@link ImageReader} that handles still image capture.
	 */
	private ImageReader mImageReader;
	/**
	 * This is the output file for our picture.
	 */
	private File mFile;
	/**
	 * The current state of camera state for taking pictures.
	 *
	 * @see #mCaptureCallback
	 */
	private SCaptureRequest mSPreviewRequest;
	private int mState = STATE_PREVIEW;
	/**
	 * A {@link Semaphore} to prevent the app from exiting before closing the camera.
	 */
	private Semaphore mCameraOpenCloseLock = new Semaphore(1);
	/**
	 * Whether the current camera device supports Flash or not.
	 */
	private boolean mFlashSupported;
	/**
	 * Orientation of the camera sensor
	 */
	private int mSensorOrientation;
	/**
	 * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
	 * {@link TextureView}.
	 */
	private final TextureView.SurfaceTextureListener mSurfaceTextureListener
		   = new TextureView.SurfaceTextureListener()
	{

		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height)
		{
			openCamera(width, height);
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height)
		{
			configureTransform(width, height);
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture texture)
		{
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture texture)
		{
		}

	};
	/**
	 * A {@link SCameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
	 */
	private SCameraCaptureSession.CaptureCallback mCaptureCallback = new SCameraCaptureSession.CaptureCallback()
	{

		private void process(SCaptureResult result)
		{
			switch(mState)
			{
				case STATE_PREVIEW:
				{
					// We have nothing to do when the camera preview is working normally.
					break;
				}
				case STATE_WAITING_LOCK:
				{
					Integer afState = result.get(SCaptureResult.CONTROL_AF_STATE);
					if(afState == null)
					{
						//captureStillPicture();
					}
					else if(SCaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
						   SCaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState)
					{
						// CONTROL_AE_STATE can be null on some devices
						Integer aeState = result.get(SCaptureResult.CONTROL_AE_STATE);
						if(aeState == null ||
							   aeState == SCaptureResult.CONTROL_AE_STATE_CONVERGED)
						{
							mState = STATE_PICTURE_TAKEN;
							//captureStillPicture();
						}
						else
						{
							//runPrecaptureSequence();
						}
					}
					break;
				}
				case STATE_WAITING_PRECAPTURE:
				{
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(SCaptureResult.CONTROL_AE_STATE);
					if(aeState == null ||
						   aeState == SCaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
						   aeState == SCaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED)
					{
						mState = STATE_WAITING_NON_PRECAPTURE;
					}
					break;
				}
				case STATE_WAITING_NON_PRECAPTURE:
				{
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(SCaptureResult.CONTROL_AE_STATE);
					if(aeState == null || aeState != SCaptureResult.CONTROL_AE_STATE_PRECAPTURE)
					{
						mState = STATE_PICTURE_TAKEN;
						//captureStillPicture();
					}
					break;
				}
			}
		}

		@Override
		public void onCaptureProgressed(@NonNull SCameraCaptureSession session,
								  @NonNull SCaptureRequest request,
								  @NonNull SCaptureResult partialResult)
		{
			process(partialResult);
		}

		@Override
		public void onCaptureCompleted(@NonNull SCameraCaptureSession session,
								 @NonNull SCaptureRequest request,
								 @NonNull STotalCaptureResult result)
		{
			process(result);
		}

	};
	/**
	 * {@link SCameraDevice.StateCallback} is called when {@link SCameraDevice} changes its state.
	 */
	private final SCameraDevice.StateCallback mStateCallback = new SCameraDevice.StateCallback()
	{

		@Override
		public void onOpened(@NonNull SCameraDevice cameraDevice)
		{
			// This method is called when the camera is opened.  We start camera preview here.
			mCameraOpenCloseLock.release();
			mSCameraDevice = cameraDevice;
			createCameraPreviewSession();
		}

		@Override
		public void onDisconnected(@NonNull SCameraDevice cameraDevice)
		{
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mSCameraDevice = null;
		}

		@Override
		public void onError(@NonNull SCameraDevice cameraDevice, int error)
		{
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mSCameraDevice = null;
			Activity activity = getActivity();
			if(null != activity)
			{
				activity.finish();
			}
		}

	};

	private static void changeButtonText(String OCRresult)
	{
		main.runOnUiThread(() ->
					    {
						    Button OCRTextView = (Button)main.findViewById(
								  R.id.checkin);
						    OCRTextView.setText(OCRresult);
					    });
	}

	/**
	 * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
	 * is at least as large as the respective texture view size, and that is at most as large as the
	 * respective max size, and whose aspect ratio matches with the specified value. If such size
	 * doesn't exist, choose the largest one that is at most as large as the respective max size,
	 * and whose aspect ratio matches with the specified value.
	 *
	 * @param choices           The list of sizes that the camera supports for the intended output
	 *                          class
	 * @param textureViewWidth  The width of the texture view relative to sensor coordinate
	 * @param textureViewHeight The height of the texture view relative to sensor coordinate
	 * @param maxWidth          The maximum width that can be chosen
	 * @param maxHeight         The maximum height that can be chosen
	 * @param aspectRatio       The aspect ratio
	 * @return The optimal {@code Size}, or an arbitrary one if none were big enough
	 */
	private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
								   int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio)
	{

		// Collect the supported resolutions that are at least as big as the preview Surface
		List<Size> bigEnough = new ArrayList<>();
		// Collect the supported resolutions that are smaller than the preview Surface
		List<Size> notBigEnough = new ArrayList<>();
		int w = aspectRatio.getWidth();
		int h = aspectRatio.getHeight();
		//System.out.println((double)h/w);
		for(Size option : choices)
		{
			//System.out.println("Size:" + option.getHeight() + "==" + option.getWidth()*h/w);
			if(option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
				   option.getHeight() == option.getWidth()*h/w)
			{
				if(option.getWidth() >= textureViewWidth &&
					   option.getHeight() >= textureViewHeight)
				{
					bigEnough.add(option);
				}
				else
				{
					notBigEnough.add(option);
				}
			}
		}

		// Pick the smallest of those big enough. If there is no one big enough, pick the
		// largest of those not big enough.
		if(bigEnough.size() > 0)
		{
			return Collections.min(bigEnough, new CompareSizesByArea());
		}
		else if(notBigEnough.size() > 0)
		{
			return Collections.max(notBigEnough, new CompareSizesByArea());
		}
		else
		{
			Log.e(TAG, "Couldn't find any suitable preview size");
			return choices[0];
		}
	}

	public static SCamera2BasicFragment newInstance(MainActivity mainpass)
	{
		main = mainpass;

		//Create final image rotation matrix
		matrix = new Matrix();
		matrix.postRotate(90);


		//initialize Tesseract API
		datapath = main.getFilesDir() + "/tesseract/";
		mTess = new TessBaseAPI();
		checkFile(new File(datapath + "tessdata/"));
		mTess.init(datapath, "eng");

		//Make the Guest objects and append them into a HashMap
		makeGuestList();

		return new SCamera2BasicFragment();
	}

	public static void greenSwitch()
	{
		main.runOnUiThread(() ->

					    {

						    FrameLayout footer = (FrameLayout)main.findViewById(R.id.control);
						    footer.setBackgroundColor(main.getResources().getColor(R.color.valid_green));
						    Handler h = new Handler();
						    h.postDelayed((() -> main.runOnUiThread(
								  () -> footer.setBackgroundColor(
										main.getResources().getColor(R.color.control_background)))),
									   3000);
					    }
		);

	}

	public static void valid()
	{

	}

	private static void checkFile(File dir)
	{
		if(!dir.exists() && dir.mkdirs())
		{
			copyFiles();
		}
		if(dir.exists())
		{
			String datafilepath = datapath + "/tessdata/eng.traineddata";
			File datafile = new File(datafilepath);

			if(!datafile.exists())
			{
				copyFiles();
			}
		}
	}

	private static void copyFiles()
	{
		try
		{
			String filepath = datapath + "/tessdata/eng.traineddata";
			AssetManager assetManager = main.getAssets();

			InputStream instream = assetManager.open("tessdata/eng.traineddata");
			OutputStream outstream = new FileOutputStream(filepath);

			byte[] buffer = new byte[1024];
			int read;
			while((read = instream.read(buffer)) != -1)
			{
				outstream.write(buffer, 0, read);
			}


			outstream.flush();
			outstream.close();
			instream.close();

			File file = new File(filepath);
			if(!file.exists())
			{
				throw new FileNotFoundException();
			}
		}
		catch(FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	private static void makeGuestList()
	{
		guestList = new HashMap<>();
		guestList.put("7796431", new Guest("7796431"));
		guestList.put("7635650", new Guest("7635650"));
		guestList.put("7727608", new Guest("7727608"));
		guestList.put("7738779", new Guest("7738779"));
		numGuests = guestList.size();
		guestPattern = Pattern.compile("([0-9]{7,7})");
		canMatch = true;
	}

	/**
	 * Shows a {@link Toast} on the UI thread.
	 *
	 * @param text The message to show
	 */
	private void showToast(final String text)
	{
		final Activity activity = getActivity();
		if(activity != null)
		{
			activity.runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
				}
			});
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
						Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
	}

	@Override
	public void onViewCreated(final View view, Bundle savedInstanceState)
	{
		view.findViewById(R.id.checkin).setOnClickListener(this);
		view.findViewById(R.id.info).setOnClickListener(this);
		main.makeMenu();
		mTextureView = (AutoFitTextureView)view.findViewById(R.id.texture);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		startBackgroundThread();

		// initialize SCamera
		mSCamera = new SCamera();
		try
		{
			mSCamera.initialize(main);
		}
		catch(SsdkUnsupportedException e)
		{
			e.printStackTrace();
			return;
		}

		// When the screen is turned off and turned back on, the SurfaceTexture is already
		// available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
		// a camera and start preview from here (otherwise, we wait until the surface is ready in
		// the SurfaceTextureListener).
		if(mTextureView.isAvailable())
		{
			openCamera(mTextureView.getWidth(), mTextureView.getHeight());
		}
		else
		{
			mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
		}

		mTextureView.setOnTouchListener((View v, MotionEvent event) ->
								  {
									  switch(event.getAction() & MotionEvent.ACTION_MASK)
									  {
										  case MotionEvent.ACTION_DOWN:
											  Rect rect = mCharacteristics
													.get(SCameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
											  Size size = mCharacteristics
													.get(SCameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
											  int areaSize = 200;
											  int right = rect.right;
											  int bottom = rect.bottom;
											  int viewWidth = mTextureView.getWidth();
											  int viewHeight = mTextureView.getHeight();
											  Rect newRect;
											  int centerX = (int)event.getX();
											  int centerY = (int)event.getY();
											  int ll = ((centerX*right) - areaSize)/viewWidth;
											  int rr = ((centerY*bottom) - areaSize)/viewHeight;

											  int focusLeft = clamp(ll, 0, right);
											  int focusBottom = clamp(rr, 0, bottom);

											  newRect = new Rect(focusLeft, focusBottom, focusLeft
													+ areaSize, focusBottom + areaSize);
											  MeteringRectangle meteringRectangle = new MeteringRectangle(
													newRect, 1000);
											  MeteringRectangle[] meteringRectangleArr = {meteringRectangle};


											  System.out.println("metering rectARR" + Arrays.toString(
													meteringRectangleArr));

											  mPreviewBuilder.set(
													SCaptureRequest.CONTROL_AF_TRIGGER,
													SCameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

											  try
											  {
												  mSCameraSession.capture(
														mSPreviewRequest,
														mCaptureCallback,
														mBackgroundHandler);
											  }
											  catch(CameraAccessException e)
											  {
												  // TODO Auto-generated catch block
												  e.printStackTrace();
											  }

											  //TODO Put all of this in a separate class and cancel using static vars

											  mPreviewBuilder.set(
													SCaptureRequest.CONTROL_AF_REGIONS,
													meteringRectangleArr);

											  mPreviewBuilder.set(
													SCaptureRequest.CONTROL_AF_MODE,
													SCaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

											  mPreviewBuilder.set(SCaptureRequest.PHASE_AF_MODE,
															  SCaptureRequest.PHASE_AF_MODE_ON);

											  try
											  {
												  mSCameraSession.capture(mPreviewBuilder.build(),
																	 mCaptureCallback,
																	 mBackgroundHandler);
												  // After this, the camera will go back to the normal state of preview.
												  mState = STATE_PREVIEW;
												  mSCameraSession.setRepeatingRequest(mSPreviewRequest,
																			   mCaptureCallback,
																			   mBackgroundHandler);
											  }
											  catch(CameraAccessException e)
											  {
												  // TODO Auto-generated catch block
												  e.printStackTrace();
											  }

											  Handler h = new Handler();
											  h.postDelayed(() ->
														 {
															 final MeteringRectangle m = new MeteringRectangle(
																    newRect, 700);
															 final MeteringRectangle[] mA = {m};

															 mPreviewBuilder.set(
																    SCaptureRequest.CONTROL_AF_REGIONS,
																    mA);

															 mPreviewBuilder.set(
																    SCaptureRequest.CONTROL_AF_MODE,
																    SCaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

															 mPreviewBuilder.set(
																    SCaptureRequest.PHASE_AF_MODE,
																    SCaptureRequest.PHASE_AF_MODE_ON);

															 try
															 {
																 mSCameraSession.capture(
																	    mPreviewBuilder.build(),
																	    mCaptureCallback,
																	    mBackgroundHandler);
																 // After this, the camera will go back to the normal state of preview.
																 mState = STATE_PREVIEW;
																 mSCameraSession.setRepeatingRequest(
																	    mSPreviewRequest,
																	    mCaptureCallback,
																	    mBackgroundHandler);
															 }
															 catch(CameraAccessException e)
															 {
																 // TODO Auto-generated catch block
																 e.printStackTrace();
															 }

															 h.postDelayed(() ->
																		{
																			mPreviewBuilder.set(
																				   SCaptureRequest.CONTROL_AF_REGIONS,
																				   null);

																			mPreviewBuilder.set(
																				   SCaptureRequest.CONTROL_AF_MODE,
																				   SCaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

																			mPreviewBuilder.set(
																				   SCaptureRequest.PHASE_AF_MODE,
																				   SCaptureRequest.PHASE_AF_MODE_ON);

																			try
																			{
																				mSCameraSession.capture(
																					   mPreviewBuilder.build(),
																					   mCaptureCallback,
																					   mBackgroundHandler);
																				// After this, the camera will go back to the normal state of preview.
																				mState = STATE_PREVIEW;
																				mSCameraSession.setRepeatingRequest(
																					   mSPreviewRequest,
																					   mCaptureCallback,
																					   mBackgroundHandler);
																			}
																			catch(CameraAccessException e)
																			{
																				// TODO Auto-generated catch block
																				e.printStackTrace();
																			}
																		}, 5000);

														 }, 5000);

											  break;
										  case MotionEvent.ACTION_UP:
											  break;
									  }


									  return true;

								  });//end touch listener block

	}

	private int clamp(int x, int min, int max)
	{
		if(x < min)
		{
			return min;
		}
		else if(x > max)
		{
			return max;
		}
		else
		{
			return x;
		}
	}

	@Override
	public void onPause()
	{
		closeCamera();
		stopBackgroundThread();
		super.onPause();
	}

	private void requestCameraPermission()
	{
		if(FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA))
		{
			new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
		}
		else
		{
			FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
									    REQUEST_CAMERA_PERMISSION);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
								    @NonNull int[] grantResults)
	{
		if(requestCode == REQUEST_CAMERA_PERMISSION)
		{
			if(grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED)
			{
				ErrorDialog.newInstance(getString(R.string.request_permission))
					   .show(getChildFragmentManager(), FRAGMENT_DIALOG);
			}
		}
		else
		{
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	/**
	 * Sets up member variables related to camera.
	 *
	 * @param width  The width of available size for camera preview
	 * @param height The height of available size for camera preview
	 */
	private void setUpCameraOutputs(int width, int height)
	{
		Activity activity = getActivity();
		SCameraManager manager = mSCamera.getSCameraManager();
		try
		{
			for(String cameraId : manager.getCameraIdList())
			{
				mCharacteristics = manager.getCameraCharacteristics(cameraId);

				// We don't use a front facing camera in this sample.
				Integer facing = mCharacteristics.get(SCameraCharacteristics.LENS_FACING);
				if(facing != null && facing == SCameraCharacteristics.LENS_FACING_FRONT)
				{
					continue;
				}

				StreamConfigurationMap map = mCharacteristics.get(
					   SCameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
				if(map == null)
				{
					continue;
				}

				// Mod: largest now just denotes the aspect ratio
				Size largest = Collections.max(
					   Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
					   new CompareSizesByArea());
				//Mod: we will be using the smallest possible size for speed
				Size smallest = Collections.min(
					   Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
					   new CompareSizesByArea());
				mImageReader = ImageReader.newInstance(480, 640,
											    ImageFormat.YUV_420_888, /*maxImages*/MAX_IMAGES);
				mImageReader.setOnImageAvailableListener(
					   mOnImageAvailableListener, mBackgroundHandler);
				double ratioImgDisplayWidth = ((double)mImageReader.getWidth())/(
					   displayWidth*((double)mImageReader.getHeight()/mImageReader.getWidth()));
				double ratioImgDisplayHeight = ((double)mImageReader.getHeight())/displayHeight;
				cropRectWidthPx = (int)(ratioImgDisplayWidth*cropRectWidth);
				cropRectHeightPx = (int)(ratioImgDisplayHeight*cropRectHeight);

				cropRectX = (int)(((double)mImageReader.getWidth())/2) - cropRectWidthPx/2; //displayWidth/2 - cropRectWidth/2
				cropRectY = (int)(ratioImgDisplayHeight*(marginTop));

				//System.out.println("1.5: " + ratioImgDisplayWidth + ", " + ratioImgDisplayHeight);
				//System.out.println("2: " + mImageReader.getWidth() + ", " + mImageReader.getHeight());
				//System.out.println("2.5: " + largest.getWidth() + ", " + largest.getHeight());
				// Find out if we need to swap dimension to get the preview size relative to sensor
				// coordinate.
				int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
				//noinspection ConstantConditions
				mSensorOrientation = mCharacteristics.get(SCameraCharacteristics.SENSOR_ORIENTATION);
				boolean swappedDimensions = false;
				switch(displayRotation)
				{
					case Surface.ROTATION_0:
					case Surface.ROTATION_180:
						if(mSensorOrientation == 90 || mSensorOrientation == 270)
						{
							swappedDimensions = true;
						}
						break;
					case Surface.ROTATION_90:
					case Surface.ROTATION_270:
						if(mSensorOrientation == 0 || mSensorOrientation == 180)
						{
							swappedDimensions = true;
						}
						break;
					default:
						Log.e(TAG, "Display rotation is invalid: " + displayRotation);
				}

				//System.out.println(mSensorOrientation);

				Point displaySize = new Point();
				activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
				int rotatedPreviewWidth = width;
				int rotatedPreviewHeight = height;
				int maxPreviewWidth = displaySize.x;
				int maxPreviewHeight = displaySize.y;
				if(swappedDimensions)
				{
					rotatedPreviewWidth = height;
					rotatedPreviewHeight = width;
					maxPreviewWidth = displaySize.y;
					maxPreviewHeight = displaySize.x;
				}

				//System.out.println("3: " + rotatedPreviewWidth + ", " + rotatedPreviewHeight );
				//System.out.println("4: " + maxPreviewWidth + ", " + maxPreviewHeight );

				// Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
				// bus' bandwidth limitation, resulting in gorgeous previews but the storage of
				// garbage capture data.
				mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
										   rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
										   maxPreviewHeight, new Size(displayHeight, displayWidth));

				//System.out.println("5: " + mPreviewSize.getWidth() + ", " + mPreviewSize.getHeight() );

				// We fit the aspect ratio of TextureView to the size of preview we picked.
				int orientation = getResources().getConfiguration().orientation;
				if(orientation == Configuration.ORIENTATION_LANDSCAPE)
				{
					//System.out.println("6: ran if");
					mTextureView.setAspectRatio(
						   mPreviewSize.getWidth(), mPreviewSize.getHeight());
				}
				else
				{
					//System.out.println("7: ran else" );
					mTextureView.setAspectRatio(
						   mPreviewSize.getHeight(), mPreviewSize.getWidth());
				}


				// Check if the flash is supported.
				Boolean available = mCharacteristics.get(SCameraCharacteristics.FLASH_INFO_AVAILABLE);
				mFlashSupported = available == null ?false: available;

				mCameraId = cameraId;
				return;
			}
		}
		catch(CameraAccessException e)
		{
			e.printStackTrace();
		}
		catch(NullPointerException e)
		{
			// Currently an NPE is thrown when the Camera2API is used but not supported on the
			// device this code runs.
			ErrorDialog.newInstance(getString(R.string.camera_error))
				   .show(getChildFragmentManager(), FRAGMENT_DIALOG);
		}
	}

	/**
	 * Opens the camera specified by {@link SCamera2BasicFragment#mCameraId}.
	 */
	private void openCamera(int width, int height)
	{
		if(mPreviewBuilder != null)
		{
			mPreviewBuilder.set(SCaptureRequest.CONTROL_AF_TRIGGER,
							SCaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
		}
		//System.out.println("1: " + width + ", " + height);
		if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
			   != PackageManager.PERMISSION_GRANTED)
		{
			requestCameraPermission();
			return;
		}
		displayWidth = width;
		displayHeight = height;
		setCenterRect(width, height);
		setUpCameraOutputs(width, height);
		configureTransform(width, height);
		mSCameraManager = mSCamera.getSCameraManager();
		try
		{
			if(!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS))
			{
				throw new RuntimeException("Time out waiting to lock camera opening.");
			}
			mSCameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
		}
		catch(CameraAccessException e)
		{
			e.printStackTrace();
		}
		catch(InterruptedException e)
		{
			throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
		}
	}

	private void setCenterRect(final int width, final int height)
	{
		marginTop = main.getResources().getDimensionPixelSize(R.dimen.shrekt_margin_top);
		cropRectWidth = main.getResources().getDimensionPixelSize(R.dimen.shrekt_width);
		cropRectHeight = main.getResources().getDimensionPixelSize(R.dimen.shrekt_height);
	}

	/**
	 * Closes the current {@link SCameraDevice}.
	 */
	private void closeCamera()
	{
		try
		{
			mCameraOpenCloseLock.acquire();
			if(null != mSCameraSession)
			{
				mSCameraSession.close();
				mSCameraSession = null;
			}
			if(null != mSCameraDevice)
			{
				mSCameraDevice.close();
				mSCameraDevice = null;
			}
			if(null != mImageReader)
			{
				mImageReader.close();
				mImageReader = null;
			}
		}
		catch(InterruptedException e)
		{
			throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
		}
		finally
		{
			mCameraOpenCloseLock.release();
		}
	}

	/**
	 * Starts a background thread and its {@link Handler}.
	 */
	private void startBackgroundThread()
	{
		mBackgroundThread = new HandlerThread("CameraBackground");
		mBackgroundThread.start();
		mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
	}

	/**
	 * Stops the background thread and its {@link Handler}.
	 */
	private void stopBackgroundThread()
	{
		mBackgroundThread.quitSafely();
		try
		{
			mBackgroundThread.join();
			mBackgroundThread = null;
			mBackgroundHandler = null;
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Creates a new {@link SCameraCaptureSession} for camera preview.
	 */
	private void createCameraPreviewSession()
	{
		try
		{

			SurfaceTexture texture = mTextureView.getSurfaceTexture();
			assert texture != null;

			// We configure the size of default buffer to be the size of camera preview we want.
			texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
			//System.out.println("VIEWING DIMENSIONS: " + mPreviewSize.getWidth() + ", " + mPreviewSize.getHeight());

			// This is the output Surface we need to start preview.
			mPreviewBuilder = mSCameraDevice.createCaptureRequest(SCameraDevice.TEMPLATE_PREVIEW);

			// Set up for the preview surface
			Surface surface = new Surface(texture);
			mPreviewBuilder.addTarget(surface);

			//Set up for the capture surface
			Surface mImageSurface = mImageReader.getSurface();
			mPreviewBuilder.addTarget(mImageSurface);

			// Here, we create a SCameraCaptureSession for camera preview.
			mSCameraDevice.createCaptureSession(Arrays.asList(surface, mImageSurface),
										 new SCameraCaptureSession.StateCallback()
										 {

											 @Override
											 public void onConfigured(
												    @NonNull SCameraCaptureSession cameraCaptureSession)
											 {
												 // The camera is already closed
												 if(null == mSCameraDevice)
												 {
													 return;
												 }

												 // When the session is ready, we start displaying the preview.
												 mSCameraSession = cameraCaptureSession;
												 try
												 {
													 // Auto focus should be continuous for camera preview.
													 mPreviewBuilder.set(
														    SCaptureRequest.CONTROL_AF_MODE,
														    SCaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
													 mPreviewBuilder.set(SCaptureRequest.PHASE_AF_MODE,
																	 SCaptureRequest.PHASE_AF_MODE_ON);

													 // Finally, we start displaying the camera preview.
													 mSPreviewRequest = mPreviewBuilder.build();
													 mSCameraSession.setRepeatingRequest(mSPreviewRequest,
																				  mCaptureCallback,
																				  mBackgroundHandler);
												 }
												 catch(CameraAccessException e)
												 {
													 e.printStackTrace();
												 }
											 }

											 @Override
											 public void onConfigureFailed(
												    @NonNull SCameraCaptureSession cameraCaptureSession)
											 {
												 showToast("Failed");
											 }
										 }, null
			);
		}
		catch(CameraAccessException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Configures the necessary {@link Matrix} transformation to `mTextureView`.
	 * This method should be called after the camera preview size is determined in
	 * setUpCameraOutputs and also the size of `mTextureView` is fixed.
	 *
	 * @param viewWidth  The width of `mTextureView`
	 * @param viewHeight The height of `mTextureView`
	 */
	private void configureTransform(int viewWidth, int viewHeight)
	{
		Activity activity = getActivity();
		if(null == mTextureView || null == mPreviewSize || null == activity)
		{
			return;
		}
		int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		Matrix matrix = new Matrix();
		RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
		RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
		float centerX = viewRect.centerX();
		float centerY = viewRect.centerY();
		if(Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation)
		{
			//System.out.println("8: ran if");
			bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
			matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
			float scale = Math.max(
				   (float)viewHeight/mPreviewSize.getHeight(),
				   (float)viewWidth/mPreviewSize.getWidth());
			matrix.postScale(scale, scale, centerX, centerY);
			matrix.postRotate(90*(rotation - 2), centerX, centerY);
		}
		else if(Surface.ROTATION_180 == rotation)
		{
			//System.out.println("9: ran else");
			matrix.postRotate(180, centerX, centerY);
		}
		mTextureView.setTransform(matrix);
		//System.out.println("10: " + rotation );
	}

	/**
	 * Retrieves the JPEG orientation from the specified screen rotation.
	 *
	 * @param rotation The screen rotation.
	 * @return The JPEG orientation (one of 0, 90, 270, and 360)
	 */
	private int getOrientation(int rotation)
	{
		// Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
		// We have to take that into account and rotate JPEG properly.
		// For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
		// For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
		return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270)%360;
	}

	/**
	 * Unlock the focus. This method should be called when still image capture sequence is
	 * finished.
	 */
	private void unlockFocus()
	{
		try
		{
			// Reset the auto-focus trigger
			mPreviewBuilder.set(SCaptureRequest.CONTROL_AF_TRIGGER,
							SCaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
			mPreviewBuilder.set(
				   SCaptureRequest.CONTROL_AF_MODE,
				   SCaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			mPreviewBuilder.set(SCaptureRequest.PHASE_AF_MODE, SCaptureRequest.PHASE_AF_MODE_ON);
			mSCameraSession.capture(mPreviewBuilder.build(), mCaptureCallback,
							    mBackgroundHandler);
			// After this, the camera will go back to the normal state of preview.
			mState = STATE_PREVIEW;
			mSCameraSession.setRepeatingRequest(mSPreviewRequest, mCaptureCallback,
										 mBackgroundHandler);
		}
		catch(CameraAccessException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void onClick(View view)
	{
		switch(view.getId())
		{
			case R.id.checkin:
			{
				//Button OCRTextView = (Button)main.findViewById(R.id.checkin);
				//OCRTextView.setText(OCRresult);
				greenSwitch();
				//System.out.println("Number of files: " + (FileUtils.listFiles(getActivity().getExternalFilesDir(null), new String[]{"jpg"}, false)).size());
				//try{FileUtils.cleanDirectory(getActivity().getExternalFilesDir(null));}catch(IOException i){}
				break;
			}
			case R.id.info:
			{
				Activity activity = getActivity();
				if(null != activity)
				{
					new AlertDialog.Builder(activity)
						   .setMessage(R.string.intro_message)
						   .setPositiveButton(android.R.string.ok, null)
						   .show();
				}
				break;
			}
		}
	}

	private synchronized void setmTessImage(Bitmap bmp)
	{
		mTess.setImage(bmp);
	}

	private Bitmap YUV_420_888_toRGB(Image image, int width, int height)
	{
		// Get the three image planes
		Image.Plane[] planes = image.getPlanes();
		ByteBuffer buffer = planes[0].getBuffer();
		byte[] y = new byte[buffer.remaining()];
		buffer.get(y);

		buffer = planes[1].getBuffer();
		byte[] u = new byte[buffer.remaining()];
		buffer.get(u);

		buffer = planes[2].getBuffer();
		byte[] v = new byte[buffer.remaining()];
		buffer.get(v);

		// get the relevant RowStrides and PixelStrides
		// (we know from documentation that PixelStride is 1 for y)
		int yRowStride = planes[0].getRowStride();
		int uvRowStride = planes[1].getRowStride();  // we know from   documentation that RowStride is the same for u and v.
		int uvPixelStride = planes[1].getPixelStride();  // we know from   documentation that PixelStride is the same for u and v.

		RenderScript rs = RenderScript.create(getActivity());
		ScriptC_yuv420888 mYuv420 = new ScriptC_yuv420888(rs);

		// Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
		// Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
		Type.Builder typeUcharY = new Type.Builder(rs, Element.U8(rs));
		typeUcharY.setX(yRowStride).setY(height);
		Allocation yAlloc = Allocation.createTyped(rs, typeUcharY.create());
		yAlloc.copyFrom(y);
		mYuv420.set_ypsIn(yAlloc);

		Type.Builder typeUcharUV = new Type.Builder(rs, Element.U8(rs));
		// note that the size of the u's and v's are as follows:
		//      (  (width/2)*PixelStride + padding  ) * (height/2)
		// =    (RowStride                          ) * (height/2)
		// but I noted that on the S7 it is 1 less...
		typeUcharUV.setX(u.length);
		Allocation uAlloc = Allocation.createTyped(rs, typeUcharUV.create());
		uAlloc.copyFrom(u);
		mYuv420.set_uIn(uAlloc);

		Allocation vAlloc = Allocation.createTyped(rs, typeUcharUV.create());
		vAlloc.copyFrom(v);
		mYuv420.set_vIn(vAlloc);

		// handover parameters
		mYuv420.set_picWidth(width);
		mYuv420.set_uvRowStride(uvRowStride);
		mYuv420.set_uvPixelStride(uvPixelStride);

		Bitmap outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Allocation outAlloc = Allocation.createFromBitmap(rs, outBitmap, Allocation.MipmapControl.MIPMAP_NONE,
												Allocation.USAGE_SCRIPT);

		Script.LaunchOptions lo = new Script.LaunchOptions();
		lo.setX(0,
			   width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
		lo.setY(0, height);

		mYuv420.forEach_doConvert(outAlloc, lo);
		outAlloc.copyTo(outBitmap);

		rs.destroy();

		return outBitmap;
	}

	/**
	 * Compares two {@code Size}s based on their areas.
	 */
	static class CompareSizesByArea implements Comparator<Size>
	{

		@Override
		public int compare(Size lhs, Size rhs)
		{
			// We cast here to ensure the multiplications won't overflow
			return Long.signum((long)lhs.getWidth()*lhs.getHeight() -
								  (long)rhs.getWidth()*rhs.getHeight());
		}

	}

	/**
	 * Shows an error message dialog.
	 */
	public static class ErrorDialog extends DialogFragment
	{

		private static final String ARG_MESSAGE = "message";

		public static ErrorDialog newInstance(String message)
		{
			ErrorDialog dialog = new ErrorDialog();
			Bundle args = new Bundle();
			args.putString(ARG_MESSAGE, message);
			dialog.setArguments(args);
			return dialog;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			final Activity activity = getActivity();
			return new AlertDialog.Builder(activity)
				   .setMessage(getArguments().getString(ARG_MESSAGE))
				   .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
				   {
					   @Override
					   public void onClick(DialogInterface dialogInterface, int i)
					   {
						   activity.finish();
					   }
				   })
				   .create();
		}

	}

	/**
	 * Shows OK/Cancel confirmation dialog about camera permission.
	 */
	public static class ConfirmationDialog extends DialogFragment
	{

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			final Fragment parent = getParentFragment();
			return new AlertDialog.Builder(getActivity())
				   .setMessage(R.string.request_permission)
				   .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
				   {
					   @Override
					   public void onClick(DialogInterface dialog, int which)
					   {
						   FragmentCompat.requestPermissions(parent,
													  new String[]{Manifest.permission.CAMERA},
													  REQUEST_CAMERA_PERMISSION);
					   }
				   })
				   .setNegativeButton(android.R.string.cancel,
								  new DialogInterface.OnClickListener()
								  {
									  @Override
									  public void onClick(DialogInterface dialog, int which)
									  {
										  Activity activity = parent.getActivity();
										  if(activity != null)
										  {
											  activity.finish();
										  }
									  }
								  })
				   .create();
		}
	}

}
