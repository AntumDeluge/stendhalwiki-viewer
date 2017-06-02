package com.jtmcn.archwiki.viewer;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Toast;

import com.jtmcn.archwiki.viewer.data.SearchResult;
import com.jtmcn.archwiki.viewer.data.SearchResultsBuilder;
import com.jtmcn.archwiki.viewer.data.WikiPage;
import com.jtmcn.archwiki.viewer.tasks.Fetch;
import com.jtmcn.archwiki.viewer.tasks.FetchUrl;
import com.jtmcn.archwiki.viewer.utils.AndroidUtils;

import java.util.List;

public class MainActivity extends Activity implements FetchUrl.OnFinish<List<SearchResult>> {
	public static final String TAG = MainActivity.class.getSimpleName();
	private SearchView searchView;
	private MenuItem searchMenuItem;
	private WikiView wikiViewer;
	private List<SearchResult> currentSuggestions;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wiki_layout);

		wikiViewer = (WikiView) findViewById(R.id.wvMain);
		ProgressBar progressBar = (ProgressBar) findViewById(R.id.ProgressBar);

		WikiChromeClient wikiChrome = new WikiChromeClient(progressBar, getActionBar());
		wikiViewer.setWebChromeClient(wikiChrome);
		wikiViewer.buildView();

		handleIntent(getIntent());
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateWebSettings();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		handleIntent(intent);
	}

	private void handleIntent(Intent intent) {
		if (intent == null) {
			return;
		}

		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			String query = intent.getStringExtra(SearchManager.QUERY);
			wikiViewer.passSearch(query);
			hideSearchView();
		} else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			final String url = intent.getDataString();
			wikiViewer.wikiClient.shouldOverrideUrlLoading(wikiViewer, url);
		}
	}

	/**
	 * Update the font size used in the webview.
	 */
	public void updateWebSettings() {
		WebSettings webSettings = wikiViewer.getSettings();

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		// https://stackoverflow.com/questions/11346916/listpreference-use-string-array-as-entry-and-integer-array-as-entry-values-does
		// the value of this preference must be parsed as a string
		// todo make a settings utils class to wrap this
		String fontSizePref = prefs.getString(WikiPrefsActivity.KEY_TEXT_SIZE, "2");
		int fontSize = Integer.valueOf(fontSizePref);

		//todo this setting should be changed to a slider, remove deprecated call
		// deprecated method must be used until Android API 14
		// https://developer.android.com/reference/android/webkit/WebSettings.TextSize.html#NORMAL
		switch (fontSize) {
			case 0:
				webSettings.setTextSize(WebSettings.TextSize.SMALLEST); //50%
				break;
			case 1:
				webSettings.setTextSize(WebSettings.TextSize.SMALLER); //75%
				break;
			case 2:
				webSettings.setTextSize(WebSettings.TextSize.NORMAL); //100%
				break;
			case 3:
				webSettings.setTextSize(WebSettings.TextSize.LARGER); //150%
				break;
			case 4:
				webSettings.setTextSize(WebSettings.TextSize.LARGEST); //200%
				break;
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		searchMenuItem = menu.findItem(R.id.menu_search);
		final SearchView searchView = (SearchView) searchMenuItem.getActionView();
		searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (!hasFocus) {
					hideSearchView();
				}
			}
		});
		searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		this.searchView = searchView;
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				wikiViewer.passSearch(query);
				return false;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				if (newText.isEmpty()) {
					searchView.setSuggestionsAdapter(null);
					return true;
				} else {
					String searchUrl = SearchResultsBuilder.getSearchQuery(newText);
					Fetch.search(MainActivity.this, searchUrl);
					return false;
				}
			}
		});

		searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
			@Override
			public boolean onSuggestionSelect(int position) {
				return false;
			}

			@Override
			public boolean onSuggestionClick(int position) {
				SearchResult searchResult = currentSuggestions.get(position);
				Log.d(TAG, "Opening '" + searchResult.getPageName() + "' from search suggestion.");
				wikiViewer.wikiClient.shouldOverrideUrlLoading(wikiViewer, searchResult.getPageUrl());
				hideSearchView();
				return true;
			}
		});
		return true;
	}

	public void hideSearchView() {
		searchMenuItem.collapseActionView();
		wikiViewer.requestFocus(); //pass control back to the wikiview
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.options, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_settings:
				startActivity(new Intent(this, WikiPrefsActivity.class));
				break;
			case R.id.menu_share:
				WikiPage wikiPage = wikiViewer.getCurrentWebPage();
				if (wikiPage != null) {
					AndroidUtils.shareText(wikiPage.getPageTitle(), wikiPage.getPageUrl(), this);
				} else { //// TODO: 5/14/2017 either make sure this never happens or localize the strings
					Log.w(TAG, "Failed to share current page " + wikiViewer.getUrl());
					Toast.makeText(this, "Sorry, can't share this page!", Toast.LENGTH_SHORT).show();
				}
				break;
			case R.id.exit:
				finish();
				break;
		}
		return true;
	}


	@Override
	public void onFinish(List<SearchResult> results) {
		currentSuggestions = results;
		searchView.setSuggestionsAdapter(SearchResultsAdapter.getCursorAdapter(this, currentSuggestions));
	}
}