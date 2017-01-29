package com.geneharvey.bouncr;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.widget.FrameLayout;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.*;

/**
 * Created by Gene on 1/20/2017.
 * Unused for now
 */

public class DetectText
{
	private int imageNumber = 0;
	private Bitmap image;
	private String datapath = "";
	private MainActivity main;
	private String OCRresult = null;
	private int processingState = 0;
	private String filename = null;
	private TessBaseAPI mTess;

	public DetectText(MainActivity main)
	{
		this.main = main;
		//initialize Tesseract API
		String language = "eng";
		datapath = main.getFilesDir() + "/tesseract/";
		mTess = new TessBaseAPI();

		checkFile(new File(datapath + "tessdata/"));

		mTess.init(datapath, language);
	}

	public Thread processImage(final Image mImage)
	{

		Thread todo = new Thread()
		{
			public void run()
			{
				while(!Camera2BasicFragment.processingIsDone)
				{
					try{sleep(5);}catch(InterruptedException e){e.printStackTrace();}
				}
				image = BitmapFactory.decodeFile(filename);
				System.out.println(image.getWidth() + " by " + image.getHeight());
				mTess.setImage(image);
				OCRresult = mTess.getUTF8Text();
				imageNumber++;
				processingState = 2;
			}
		};
		todo.start();

		return todo;
	}

	public void greenSwitch()
	{
		FrameLayout footer = (FrameLayout)main.findViewById(R.id.control);
		footer.setBackgroundColor(main.getResources().getColor(R.color.valid_green));
	}

	public void valid()
	{

	}

	private void checkFile(File dir)
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

	private void copyFiles()
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
}
