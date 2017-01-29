package com.geneharvey.bouncr;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.camera.SCamera;

public class MainActivity extends Activity
{
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerList;
	private ActionBarDrawerToggle mDrawerToggle;

	private CharSequence mDrawerTitle;
	private CharSequence mTitle;
	private String[] mOptionTitles;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);
		if(null == savedInstanceState)
		{
			SCamera sCamera = new SCamera();
			try
			{
				sCamera.initialize(this);
				getFragmentManager().beginTransaction()
					   .replace(R.id.container, SCamera2BasicFragment.newInstance(this))
					   .commit();
			}
			catch(SsdkUnsupportedException e)
			{
				getFragmentManager().beginTransaction()
					   .replace(R.id.container, Camera2BasicFragment.newInstance(this))
					   .commit();
			}
		}
	}

	public void makeMenu()
	{
		mOptionTitles = getResources().getStringArray(R.array.option_array);
		mDrawerLayout = (DrawerLayout)this.findViewById(R.id.drawer_layout);
		mDrawerList = (ListView)this.findViewById(R.id.left_drawer);

		mDrawerLayout.setScrimColor(Color.TRANSPARENT);

		// Set the adapter for the list view
		mDrawerList.setAdapter(new ArrayAdapter<>(this, R.layout.drawer_list_item, mOptionTitles));
		// Set the list's click listener
		mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
	}

	/** Swaps fragments in the main content view */
	private void selectItem(int position)
	{
		/*
		// Create a new fragment and specify the planet to show based on position
		Fragment fragment = new OptionFragment();
		Bundle args = new Bundle();
		args.putInt(PlanetFragment.ARG_PLANET_NUMBER, position);
		fragment.setArguments(args);

		// Insert the fragment by replacing any existing fragment
		FragmentManager fragmentManager = getFragmentManager();
		fragmentManager.beginTransaction()
			   .replace(R.id.content_frame, fragment)
			   .commit();
		*/
		// Highlight the selected item, update the title, and close the drawer
		mDrawerList.setItemChecked(position, true);
		setTitle(mOptionTitles[position]);
		mDrawerLayout.closeDrawer(mDrawerList);
	}

	@Override
	public void setTitle(CharSequence title)
	{
		mTitle = title;
		getActionBar().setTitle(mTitle);
	}

	private class DrawerItemClickListener implements ListView.OnItemClickListener
	{
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		{
			selectItem(position);
		}
	}

}

