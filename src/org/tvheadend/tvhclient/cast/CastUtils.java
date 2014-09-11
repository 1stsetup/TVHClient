/*
 *  Copyright (C) 2014 Michel Verbraak
 *
 * This file is part of TVHGuide.
 *
 * TVHGuide is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TVHGuide is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TVHGuide.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tvheadend.tvhclient.cast;

import java.io.IOException;

import org.tvheadend.tvhclient.R;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.Cast.ApplicationConnectionResult;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
//import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.cast.RemoteMediaPlayer.MediaChannelResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouter.RouteInfo;

public class CastUtils {
	private static boolean initialized = false;
    private static MediaRouter mMediaRouter = null;
    private static MediaRouteSelector mMediaRouteSelector = null;
    private static MediaRouter.Callback mMediaRouterCallback = null;
    private static GoogleApiClient.ConnectionCallbacks mConnectionCallbacks = null;
    private static GoogleApiClient.OnConnectionFailedListener mConnectionFailedListener = null;
    private static final String APP_ID = "CD4757DE";
    private static final String LOG_TAG = "CastUtils";
    private static CastDevice mSelectedDevice = null;
    private static GoogleApiClient mApiClient = null;
    private static Cast.Listener mCastClientListener = null;
    private static TVHClientChannel mTVHClientChannel = null;
    private static RemoteMediaPlayer mRemoteMediaPlayer = null;
    private static OnConnectedCallback mOnConnectedCallback = null;
    
    private static String mTitle = "";
    private static String mUrl = "";
    private static String mMime = "";

    public static interface OnConnectedCallback {
        void onConnected(String title, String url, String mime);
    }

    public static void initialize(Activity activity) {
    	
    	if (mMediaRouter != null) return;
    	
        mMediaRouter = MediaRouter.getInstance(activity.getApplicationContext());
        
        mMediaRouteSelector = new MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(APP_ID))
        .build();
  /*    
        mMediaRouteSelector = new MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
        .build();
    */    
        mMediaRouterCallback = new MediaRouterCallback();
        mConnectionCallbacks = new ConnectionCallbacks();
        mConnectionFailedListener = new ConnectionFailedListener();
        mCastClientListener = new CastClientListener();
    	mTVHClientChannel = new TVHClientChannel();
    	
    	mRemoteMediaPlayer = new RemoteMediaPlayer();
    	mRemoteMediaPlayer.setOnStatusUpdatedListener(
    	                           new RemoteMediaPlayer.OnStatusUpdatedListener() {
    	  @Override
    	  public void onStatusUpdated() {
//    	    MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();
//    	    boolean isPlaying = mediaStatus.getPlayerState() == 
//    	            MediaStatus.PLAYER_STATE_PLAYING;
    	    //...
    	  }
    	});

    	mRemoteMediaPlayer.setOnMetadataUpdatedListener(
    	                           new RemoteMediaPlayer.OnMetadataUpdatedListener() {
    	  @Override
    	  public void onMetadataUpdated() {
 //   	    MediaInfo mediaInfo = mRemoteMediaPlayer.getMediaInfo();
 //   	    MediaMetadata metadata = mediaInfo.getMetadata();
    	    Log.d(LOG_TAG,"onMetadataUpdated");
    	    //...
    	  }
    	});
    	
    	initialized = true;
    }
    
    public static String appId() {
    	return APP_ID;
    }
    
    public static void addMediaRouterCallback() {
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    public static void removeMediaRouterCallback() {
        mMediaRouter.removeCallback(mMediaRouterCallback);
    }

    public static void onCreateOptionsMenu(Activity activity, Menu menu) {
    	activity.getMenuInflater().inflate(R.menu.cast_menu, menu);

        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider = 
          (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
    }
    
    public static void setCastDevice(CastDevice device) {
    	mSelectedDevice = device;
    }
    
    public static CastDevice getCastDevice() {
    	return mSelectedDevice;
    }
    
    public static void connect(Activity activity, String title, String url, String mime, OnConnectedCallback cb) {
    	
    	if (! initialized) {
    		initialize(activity);
    	}
    	if (getCastDevice() != null) {
    		Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                    .builder(getCastDevice(), mCastClientListener);

    		mApiClient = new GoogleApiClient.Builder(activity)
                            .addApi(Cast.API, apiOptionsBuilder.build())
                            .addConnectionCallbacks(mConnectionCallbacks)
                            .addOnConnectionFailedListener(mConnectionFailedListener)
                            .build(); 
    		
    		mTitle = title;
    		mUrl = url;
    		mMime = mime;
    		
    		mOnConnectedCallback = cb;
    		
    		mApiClient.connect();
    	}
    	
    }
    
    private static void disconnect() {
        if (mApiClient != null) {
            mApiClient.disconnect();
            mApiClient = null;
        }
    }

    public static void play(String title, String url, String mime) {

    	Log.d(LOG_TAG, "Requested to play:" + url + " with mime:" + mime);
        
    	MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
    	mediaMetadata.putString(MediaMetadata.KEY_TITLE, title);
    	MediaInfo mediaInfo = new MediaInfo.Builder(url)
    	    .setContentType(mime)
    	    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
    	    .setMetadata(mediaMetadata)
    	              .build();
    	try {
    	  mRemoteMediaPlayer.load(mApiClient, mediaInfo, true)
    	     .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
    	    @Override
    	    public void onResult(MediaChannelResult result) {
    	      if (result.getStatus().isSuccess()) {
    	        Log.d(LOG_TAG, "Media loaded successfully");
    	      }
    	    }
    	     });
    	} catch (IllegalStateException e) {
    	  Log.e(LOG_TAG, "Problem occurred with media during loading", e);
    	} catch (Exception e) {
    	  Log.e(LOG_TAG, "Problem opening media during loading", e);
    	}    	
    }
    
    private static class MediaRouterCallback extends MediaRouter.Callback {

    	  @Override
    	  public void onRouteSelected(MediaRouter router, RouteInfo routeInfo) {
    		  Log.d(LOG_TAG, "onRouteSelected: " + routeInfo.getName());
    		  CastUtils.setCastDevice(CastDevice.getFromBundle(routeInfo.getExtras()));
     	  }

    	  @Override
    	  public void onRouteUnselected(MediaRouter router, RouteInfo routeInfo) {
    		  Log.d(LOG_TAG, "onRouteUnselected: " + routeInfo.getName());
    		  Cast.CastApi.stopApplication(mApiClient);
    		  CastUtils.setCastDevice(null);
    	  }
    	  
    	  @Override
    	  public void onProviderAdded (MediaRouter router, MediaRouter.ProviderInfo provider) {
    		  String tmpStr = provider.toString();
    		  Log.d(LOG_TAG, tmpStr);
    	  }
      }
    
    private static class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {

    	private static boolean mWaitingForReconnect = false;
    	
    	@Override
		public void onConnected(Bundle connectionHint) {
		  if (mWaitingForReconnect) {
		    mWaitingForReconnect = false;
		    //reconnectChannels();
		  } 
		  else {
		    try {
		      Cast.CastApi.launchApplication(mApiClient, APP_ID, false)
		      	.setResultCallback(new ConnectionResultCallback());
		
		    } catch (Exception e) {
		      Log.e(LOG_TAG, "Failed to launch application", e);
		    }
		  }
		}
		
		@Override
		public void onConnectionSuspended(int cause) {
		  mWaitingForReconnect = true;
		}
	}

    private static class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {

    	@Override
		public void onConnectionFailed(ConnectionResult result) {
		  //teardown();
		}
    }
    
    private static class CastClientListener extends Cast.Listener {
    	  @Override
    	  public void onApplicationStatusChanged() {
    	    if (mApiClient != null) {
    	      Log.d(LOG_TAG, "onApplicationStatusChanged: "
    	       + Cast.CastApi.getApplicationStatus(mApiClient));
    	    }
    	  }

    	  @Override
    	  public void onVolumeChanged() {
    	    if (mApiClient != null) {
    	      Log.d(LOG_TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(mApiClient));
    	    }
    	  }

    	  @Override
    	  public void onApplicationDisconnected(int errorCode) {
    	      Log.d(LOG_TAG, "onApplicationDisconnected: Error:" + errorCode);
   	    //teardown();
    	  }
    	};

    	private static class TVHClientChannel implements Cast.MessageReceivedCallback {

			public String getNamespace() {
			    return "urn:x-cast:com.example.custom";
			}

		  @Override
		  public void onMessageReceived(CastDevice castDevice, String namespace,
		        String message) {
		    Log.d(LOG_TAG, "onMessageReceived: " + message);
		  }
    	}
    	
        private static class ConnectionResultCallback implements ResultCallback<ApplicationConnectionResult> {

        	@Override
		    public void onResult(ApplicationConnectionResult result) {
		        Status status = result.getStatus();
		        ApplicationMetadata appMetaData = result.getApplicationMetadata();
		
		        if (status.isSuccess()) {
		            Log.d(LOG_TAG, "ConnectionResultCallback success: " + appMetaData.getName());
		            if (mOnConnectedCallback != null) {
		            	mOnConnectedCallback.onConnected(mTitle, mUrl, mMime);
		            }
		            try {
		                Cast.CastApi.setMessageReceivedCallbacks(mApiClient,
		                		mTVHClientChannel.getNamespace(), mTVHClientChannel);
		            } 
		            catch (IOException e) {
		                Log.w(LOG_TAG, "Exception while launching application", e);
		            }
		        } 
		        else {
		            Log.d(LOG_TAG, "ConnectionResultCallback. Unable to launch the game. statusCode: "
		                    + status.getStatusCode());
		        }
		    }
        }
}
