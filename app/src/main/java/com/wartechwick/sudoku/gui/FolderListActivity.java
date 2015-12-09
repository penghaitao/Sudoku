/* 
 * Copyright (C) 2009 Roman Masek
 * 
 * This file is part of OpenSudoku.
 * 
 * OpenSudoku is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * OpenSudoku is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with OpenSudoku.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.wartechwick.sudoku.gui;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.wartechwick.sudoku.R;
import com.wartechwick.sudoku.db.FolderColumns;
import com.wartechwick.sudoku.db.SudokuDatabase;
import com.wartechwick.sudoku.game.FolderInfo;
import com.wartechwick.sudoku.gui.FolderDetailLoader.FolderDetailCallback;

/**
 * List of puzzle's folder. This activity also serves as root activity of application.
 *
 * @author romario
 */
public class FolderListActivity extends ListActivity {

	private static final String TAG = "FolderListActivity";

	private Cursor mCursor;
	private SudokuDatabase mDatabase;
	private FolderListViewBinder mFolderListBinder;
	private InterstitialAd mInterstitialAd;
	private long folderId;
    public static boolean test = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.folder_list);


		AdView mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice("3EDBD52D74D95B8CBE8E95973F7864DF")
                .build();
        mAdView.loadAd(adRequest);

        mInterstitialAd = new InterstitialAd(this);
//        mInterstitialAd.setAdUnitId("ca-app-pub-3940256099942544/1033173712"); test
        mInterstitialAd.setAdUnitId(getResources().getString(R.string.interstitial_ad_unit_id));

		mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                requestNewInterstitial();
                playPuzzle(folderId);
            }
        });

        requestNewInterstitial();

        mDatabase = new SudokuDatabase(getApplicationContext());

        SudokuListFilter mListFilter = new SudokuListFilter(getApplicationContext());
        mListFilter.showStateNotStarted = false;
        mListFilter.showStatePlaying = true;
        mListFilter.showStateCompleted = false;
        folderId = mDatabase.getFolderId(-1, mListFilter);
        if (folderId != -1) {
            playPuzzle(folderId);
        }


		setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
		// Inform the list we provide context menus for items
		getListView().setOnCreateContextMenuListener(this);

		mCursor = mDatabase.getFolderList();
		startManagingCursor(mCursor);
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.folder_list_item,
				mCursor, new String[]{FolderColumns.NAME, FolderColumns._ID},
				new int[]{R.id.name, R.id.detail});
		mFolderListBinder = new FolderListViewBinder(this);
		adapter.setViewBinder(mFolderListBinder);

		setListAdapter(adapter);

		// show changelog on first run
//		Changelog changelog = new Changelog(this);
//		changelog.showOnFirstRun();
    }

	@Override
	protected void onStart() {
		super.onStart();
		updateList();
	}

    @Override
    protected void onResume() {
        super.onResume();
        if (test) {
            test = false;
            finish();
        }
    }

    @Override
	protected void onDestroy() {
		super.onDestroy();
        mDatabase.close();
        mFolderListBinder.destroy();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
//		Intent i = new Intent(this, SudokuListActivity.class);
//		i.putExtra(SudokuListActivity.EXTRA_FOLDER_ID, id);
//		startActivity(i);
		if (mInterstitialAd.isLoaded()) {
            folderId = id;
			mInterstitialAd.show();
		} else {
			playPuzzle(id);
		}
	}

	private  void playPuzzle(long folderId) {
		SudokuListFilter mListFilter = new SudokuListFilter(getApplicationContext());
		mListFilter.showStateNotStarted = true;
		mListFilter.showStatePlaying = true;
		mListFilter.showStateCompleted = false;
		long sudokuId = mDatabase.getSudokuId(folderId, mListFilter);
		if (sudokuId != -1) {
			Intent i = new Intent(this, SudokuPlayActivity.class);
			i.putExtra(SudokuPlayActivity.EXTRA_SUDOKU_ID, sudokuId);
            i.putExtra(SudokuPlayActivity.EXTRA_FOLDER_ID, folderId);
			startActivity(i);
		} else {
			Toast.makeText(this, "You have solved all puzzles of this level:) Please choose other level", Toast.LENGTH_SHORT);
		}
	}

	private void updateList() {
		mCursor.requery();
	}

	private void requestNewInterstitial() {
		AdRequest adRequest = new AdRequest.Builder()
				.addTestDevice("3EDBD52D74D95B8CBE8E95973F7864DF")
				.build();

		mInterstitialAd.loadAd(adRequest);
	}

	private static class FolderListViewBinder implements ViewBinder {
		private Context mContext;
		private FolderDetailLoader mDetailLoader;


		public FolderListViewBinder(Context context) {
			mContext = context;
			mDetailLoader = new FolderDetailLoader(context);
		}

		@Override
		public boolean setViewValue(View view, Cursor c, int columnIndex) {

			switch (view.getId()) {
				case R.id.name:
					((TextView) view).setText(c.getString(columnIndex));
					break;
				case R.id.detail:
					final long folderID = c.getLong(columnIndex);
					final TextView detailView = (TextView) view;
					detailView.setText(mContext.getString(R.string.loading));
					mDetailLoader.loadDetailAsync(folderID, new FolderDetailCallback() {
						@Override
						public void onLoaded(FolderInfo folderInfo) {
							if (folderInfo != null)
								detailView.setText(folderInfo.getDetail(mContext));
						}
					});
			}

			return true;
		}

		public void destroy() {
			mDetailLoader.destroy();
		}
	}


}
