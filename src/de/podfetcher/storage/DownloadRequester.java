package de.podfetcher.storage;

import java.util.ArrayList;
import java.io.File;
import java.util.concurrent.Callable;

import de.podfetcher.feed.*;
import de.podfetcher.service.DownloadService;
import de.podfetcher.util.NumberGenerator;
import de.podfetcher.R;

import android.util.Log;
import android.database.Cursor;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Messenger;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.content.ComponentName;
import android.os.Message;
import android.os.RemoteException;
import android.content.Intent;
import android.webkit.URLUtil;

public class DownloadRequester {
	private static final String TAG = "DownloadRequester";
	private static final int currentApi = android.os.Build.VERSION.SDK_INT;

	public static String EXTRA_DOWNLOAD_ID = "extra.de.podfetcher.storage.download_id";
	public static String EXTRA_ITEM_ID = "extra.de.podfetcher.storage.item_id";

	public static String ACTION_FEED_DOWNLOAD_COMPLETED = "action.de.podfetcher.storage.feed_download_completed";
	public static String ACTION_MEDIA_DOWNLOAD_COMPLETED = "action.de.podfetcher.storage.media_download_completed";
	public static String ACTION_IMAGE_DOWNLOAD_COMPLETED = "action.de.podfetcher.storage.image_download_completed";

	private static boolean STORE_ON_SD = true;
	public static String IMAGE_DOWNLOADPATH = "images/";
	public static String FEED_DOWNLOADPATH = "cache/";
	public static String MEDIA_DOWNLOADPATH = "media/";

	private static DownloadRequester downloader;
	private DownloadManager manager;

	public ArrayList<FeedFile> downloads;

	private DownloadRequester() {
		downloads = new ArrayList<FeedFile>();
	}

	public static DownloadRequester getInstance() {
		if (downloader == null) {
			downloader = new DownloadRequester();
		}
		return downloader;
	}

	private long download(Context context, FeedFile item, File dest) {
		if (dest.exists()) {
			Log.d(TAG, "File already exists. Deleting !");
			dest.delete();
		}
		Log.d(TAG, "Requesting download of url " + item.getDownload_url());
		downloads.add(item);
		DownloadManager.Request request = new DownloadManager.Request(
				Uri.parse(item.getDownload_url())).setDestinationUri(Uri
				.fromFile(dest));
		Log.d(TAG, "Version is " + currentApi);
		if (currentApi >= 11) {
			request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
		} else {
			request.setVisibleInDownloadsUi(false);
			request.setShowRunningNotification(false);
		}

		// TODO Set Allowed Network Types
		DownloadManager manager = (DownloadManager) context
				.getSystemService(Context.DOWNLOAD_SERVICE);
		
		long downloadId = manager.enqueue(request);
		item.setDownloadId(downloadId);
		item.setFile_url(dest.toString());
		context.startService(new Intent(context, DownloadService.class));
		return downloadId;
	}

	public long downloadFeed(Context context, Feed feed) {
		return download(context, feed, new File(getFeedfilePath(context),
				getFeedfileName(feed)));
	}

	public long downloadImage(Context context, FeedImage image) {
		return download(context, image, new File(getImagefilePath(context),
				getImagefileName(image)));
	}

	public long downloadMedia(Context context, FeedMedia feedmedia) {
		return download(context, feedmedia,
				new File(getMediafilePath(context, feedmedia),
						getMediafilename(feedmedia)));
	}

	/**
	 * Cancels a running download.
	 * 
	 * @param context
	 *            A context needed to get the DownloadManager service
	 * @param id
	 *            ID of the download to cancel
	 * */
	public void cancelDownload(final Context context, final long id) {
		Log.d(TAG, "Cancelling download with id " + id);
		DownloadManager dm = (DownloadManager) context
				.getSystemService(Context.DOWNLOAD_SERVICE);
		int removed = dm.remove(id);
		if (removed > 0) {
			FeedFile f = getFeedFile(id);
			if (f != null) {
				downloads.remove(f);
			}
		}
	}

	/** Get a feedfile by its download id */
	public FeedFile getFeedFile(long id) {
		for (FeedFile f : downloads) {
			if (f.getDownloadId() == id) {
				return f;
			}
		}
		return null;
	}

	/** Remove an object from the downloads-list of the requester. */
	public void removeDownload(FeedFile f) {
		downloads.remove(f);
	}

	public ArrayList<FeedFile> getDownloads() {
		return downloads;
	}

	/** Get the number of uncompleted Downloads */
	public int getNumberOfDownloads() {
		return downloads.size();
	}

	public String getFeedfilePath(Context context) {
		return context.getExternalFilesDir(FEED_DOWNLOADPATH).toString() + "/";
	}

	public String getFeedfileName(Feed feed) {
		return "feed-" + NumberGenerator.generateLong(feed.getDownload_url());
	}

	public String getImagefilePath(Context context) {
		return context.getExternalFilesDir(IMAGE_DOWNLOADPATH).toString() + "/";
	}

	public String getImagefileName(FeedImage image) {
		return "image-" + NumberGenerator.generateLong(image.getDownload_url());
	}

	public String getMediafilePath(Context context, FeedMedia media) {
		return context
				.getExternalFilesDir(
						MEDIA_DOWNLOADPATH
								+ media.getItem().getFeed().getTitle() + "/")
				.toString();
	}

	public String getMediafilename(FeedMedia media) {
		return URLUtil.guessFileName(media.getDownload_url(), null,
				media.getMime_type());
	}

	/*
	 * ------------ Methods for communicating with the DownloadService
	 * -------------
	 */
	private DownloadService mService = null;
	boolean mIsBound;

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = ((DownloadService.LocalBinder) service).getService();
			Log.d(TAG, "Connection to service established");
			mService.queryDownloads();
		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null;
			Log.i(TAG, "Closed connection with DownloadService.");
		}
	};

	/** Notifies the DownloadService to check if there are any Downloads left */
	public void notifyDownloadService(Context context) {
		context.bindService(new Intent(context, DownloadService.class),
				mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;

		context.unbindService(mConnection);
		mIsBound = false;
	}
}
