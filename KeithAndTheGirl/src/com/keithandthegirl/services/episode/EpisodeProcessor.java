/**
 *  This file is part of KeithAndTheGirl for Android
 * 
 *  KeithAndTheGirl for Android is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeithAndTheGirl for Android is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeithAndTheGirl for Android.  If not, see <http://www.gnu.org/licenses/>.
 *   
 * This software can be found at <https://github.com/dmfrey/KeithAndTheGirl/>
 *
 */
package com.keithandthegirl.services.episode;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.code.rome.android.repackaged.com.sun.syndication.feed.synd.SyndEnclosure;
import com.google.code.rome.android.repackaged.com.sun.syndication.feed.synd.SyndEntry;
import com.google.code.rome.android.repackaged.com.sun.syndication.feed.synd.SyndFeed;
import com.google.code.rome.android.repackaged.com.sun.syndication.fetcher.FeedFetcher;
import com.google.code.rome.android.repackaged.com.sun.syndication.fetcher.impl.FeedFetcherCache;
import com.google.code.rome.android.repackaged.com.sun.syndication.fetcher.impl.HashMapFeedInfoCache;
import com.google.code.rome.android.repackaged.com.sun.syndication.fetcher.impl.HttpURLFeedFetcher;
import com.keithandthegirl.MainApplication;
import com.keithandthegirl.db.EpisodeConstants;
import com.keithandthegirl.db.EpisodeConstants.Show;
import com.keithandthegirl.provider.KatgProvider;
import com.keithandthegirl.services.AbstractKatgProcessor;
import com.keithandthegirl.services.download.DownloadService.Resource;
import com.keithandthegirl.utils.NotificationHelper;
import com.keithandthegirl.utils.NotificationHelper.NotificationType;

/**
 * @author Daniel Frey
 *
 */
public class EpisodeProcessor extends AbstractKatgProcessor {

	protected static final String TAG = EpisodeProcessor.class.getSimpleName();

	private NotificationHelper mNotificationHelper;
	
	public interface EpisodeProcessorCallback {

		void send( int resultCode );

	}

	public EpisodeProcessor( Context context ) {
		super( context );
		Log.v( TAG, "initialize : enter" );
		
		mNotificationHelper = new NotificationHelper( context );
		
		Log.v( TAG, "initialize : exit" );
	}

	public void getEpisodes( EpisodeProcessorCallback callback ) {
		Log.v( TAG, "getEpisodes : enter" );

		try {
            mNotificationHelper.createNotification( "KeithAndTheGirl", "Refreshing KATG RSS Feed", NotificationType.SYNC );

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( mContext );
            String vip = sharedPref.getString( MainApplication.KATG_RSS_FEED_VIP_KEY, "" );
            boolean isVip = ( null != vip && !"".equals( vip ) ? true : false );
            Log.v( TAG, "getEpisodes : isVip=" + isVip );
            
            // if not VIP, then reset all episodes where VIP is 0 to 1;
            if( !isVip ) {
            	resetVipEpisodes();
            }
            
            String address = MainApplication.KATG_RSS_FEED;
            if( isVip ) {
            	address = MainApplication.KATG_RSS_FEED_VIP + "?a=" + vip;
            }
            //Log.v( TAG, "getEpisodes : rss address=" + address );
            
            FeedFetcherCache feedInfoCache = HashMapFeedInfoCache.getInstance();
            FeedFetcher feedFetcher = new HttpURLFeedFetcher( feedInfoCache );
            SyndFeed feed = feedFetcher.retrieveFeed( new URL( address ) );
			
			processFeed( feed, isVip );
			
			callback.send( 1 );
			
			mNotificationHelper.completed();
		} catch( Exception e ) {
			Log.e( TAG, e.getLocalizedMessage(), e );
		}
		
		Log.v( TAG, "getRecordedList : exit" );
	}

	// internal helpers
	
	@TargetApi( 8 )
	@SuppressWarnings( "unchecked" )
	private void processFeed( SyndFeed feed, boolean isVip ) throws RemoteException, OperationApplicationException {
		Log.v( TAG, "processFeed : enter" );

		if( null != feed ) {

			if( null != feed.getEntries() && !feed.getEntries().isEmpty() ) {
				
                File root;
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ) {
                	root = Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_PODCASTS );
                } else {
                	root = Environment.getExternalStorageDirectory();
                }
	            
				int count = 0, loaded = 0;

				ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
				
				String[] projection = new String[] { EpisodeConstants._ID };

				StringBuilder sb = new StringBuilder();
				sb.append( EpisodeConstants.FIELD_TITLE ).append( " = ?" );

	            //Log.v( TAG, "processFeed : feed entries downloaded=" + feed.getEntries().size() );
				for( SyndEntry entry : (List<SyndEntry>) feed.getEntries() ) {
					//Log.v( TAG, "processFeed : feed entry iteration" );
					
					String title = entry.getTitle();
					String[] args = new String[] { title };

					ContentValues values = convertFeedToContentValues( entry, root, isVip );

					Cursor cursor = mContext.getContentResolver().query( EpisodeConstants.CONTENT_URI, projection, sb.toString(), args, null );
					if( cursor.moveToFirst() ) {
						//Log.v( TAG, "processFeed : feed entry iteration, updating existing entry" );
						
						Long id = cursor.getLong( cursor.getColumnIndexOrThrow( EpisodeConstants._ID ) );
						ops.add( 
								ContentProviderOperation.newUpdate( ContentUris.withAppendedId( EpisodeConstants.CONTENT_URI, id ) )
									.withValues( values )
									.withYieldAllowed( true )
									.build()
							);

					} else {
						//Log.v( TAG, "processFeed : feed entry iteration, adding new entry" );

						ops.add(  
								ContentProviderOperation.newInsert( EpisodeConstants.CONTENT_URI )
									.withValues( values )
									.withYieldAllowed( true )
									.build()
							);

					}
					cursor.close();
					count++;

					if( count > 100 ) {
						Log.v( TAG, "processFeed : applying batch for '" + count + "' transactions" );
						
						if( !ops.isEmpty() ) {
							//Log.v( TAG, "process : applying batch '" + channel.getCallSign() + "'" );
							
							ContentProviderResult[] results = mContext.getContentResolver().applyBatch( KatgProvider.AUTHORITY, ops );
							loaded += results.length;
							
							if( results.length > 0 ) {
								ops.clear();
							}
						}

						count = 0;
					}

				}
				
				if( !ops.isEmpty() ) {
					Log.v( TAG, "processFeed : applying final batch for '" + count + "' transactions" );

					ContentProviderResult[] results = mContext.getContentResolver().applyBatch( KatgProvider.AUTHORITY, ops );
					loaded += results.length;

					if( results.length > 0 ) {
						ops.clear();
					}
				}
			
				Log.i( TAG, "processFeed : feed entries loaded '" + loaded + "'" );

			}

		}

		mNotificationHelper.completed();
		
		Log.v( TAG, "processFeed : exit" );
	}

	// internal helpers
	
	private ContentValues convertFeedToContentValues( SyndEntry entry, File root, boolean isVip ) {
		
		String title = entry.getTitle();

		String show = generateShowName( title );
		String showKey = generateShowKey( title );
		//Log.v( TAG, "processFeed : show=" + show + ", showKey=" + showKey );
		
		String value = entry.getDescription().getValue();
		value = value.replace( "<p>", "" );
		value = value.replace( "</p>", "" );
		value = value.replace( "\"", "" );
        
		Resource fileType = null;
		String filename = "", address = "", type = "";
		long length = 0;
		try {
			SyndEnclosure enc = (SyndEnclosure) entry.getEnclosures().get( 0 );
			
			address = enc.getUrl();
			type = enc.getType();
			length = enc.getLength();
			//Log.d( TAG, "processFeed : enc=[ address=" + address + ", type=" + type + ", length=" + length + "]" );
			
			String encFilename = address.substring( address.lastIndexOf( '/' ) + 1 );
			if( isVip ) {
				encFilename = encFilename.substring( 0, encFilename.indexOf( '?' ) );
				fileType = Resource.findByExtension( encFilename.substring( encFilename.indexOf( "." ) + 1 ) );
			}
			
            File episodeDir = new File( root, show );
            episodeDir.mkdirs();

			File f = new File( episodeDir, encFilename );
			if( f.exists() ) {
				filename = f.getAbsolutePath();

				//Log.d( TAG, "processFeed : filename=" + filename );
			}
		} catch( Exception e ) {
			Log.w( TAG, "processFeed : episode '" + title + "' could not be processed", e );
		}
		
		ContentValues values = new ContentValues();
		values.put( EpisodeConstants.FIELD_SHOW, show );
		values.put( EpisodeConstants.FIELD_SHOW_KEY, showKey );
		values.put( EpisodeConstants.FIELD_PUBLISH_DATE, entry.getPublishedDate().getTime() );
		values.put( EpisodeConstants.FIELD_TITLE, title );
		values.put( EpisodeConstants.FIELD_DESCRIPTION, value );
		values.put( EpisodeConstants.FIELD_URL, address  );
		values.put( EpisodeConstants.FIELD_TYPE, ( null != fileType ) ? fileType.getMimeType() : Resource.MP3.getMimeType() );
		values.put( EpisodeConstants.FIELD_LENGTH, length );
		values.put( EpisodeConstants.FIELD_FILE, filename );
		values.put( EpisodeConstants.FIELD_VIP, ( isVip ? 1 : 0 ) );

		return values;
	}
	
	private String generateShowKey( String title ) {
		
		String show = Show.KATG.getKey();
		
		if( title.startsWith( Show.WMN.getKey() ) ) {
			show = Show.WMN.getKey();
		}
		
		if( title.startsWith( Show.MNIK.getKey() ) ) {
			show = Show.MNIK.getKey();
		}

		if( title.startsWith( Show.TTSWD.getKey() ) ) {
			show = Show.TTSWD.getKey();
		}
		
		if( title.startsWith( Show.INTERNMENT.getKey() ) ) {
			show = Show.INTERNMENT.getKey();
		}

		if( title.startsWith( Show.BROLO.getKey() ) ) {
			show = Show.BROLO.getKey();
		}
		
		if( title.startsWith( Show.HENNY.getKey() ) ) {
			show = Show.HENNY.getKey();
		}
		
		if( title.startsWith( Show.KATG_TV.getKey() ) ) {
			show = Show.KATG_TV.getKey();
		}

		if( title.startsWith( Show.BEGINNINGS.getKey() ) ) {
			show = Show.BEGINNINGS.getKey();
		}

		return show;
	}

	private String generateShowName( String title ) {
		
		String show = Show.KATG.getShowName();
		
		if( title.startsWith( Show.WMN.getKey() ) ) {
			show = Show.WMN.getShowName();
		}
		
		if( title.startsWith( Show.MNIK.getKey() ) ) {
			show = Show.MNIK.getShowName();
		}

		if( title.startsWith( Show.TTSWD.getKey() ) ) {
			show = Show.TTSWD.getShowName();
		}
		
		if( title.startsWith( Show.INTERNMENT.getKey() ) ) {
			show = Show.INTERNMENT.getShowName();
		}

		if( title.startsWith( Show.KATG_TV.getKey() ) ) {
			show = Show.KATG_TV.getShowName();
		}

		if( title.startsWith( Show.BROLO.getKey() ) ) {
			show = Show.BROLO.getShowName();
		}
		
		if( title.startsWith( Show.HENNY.getKey() ) ) {
			show = Show.HENNY.getShowName();
		}
		
		if( title.startsWith( Show.BEGINNINGS.getKey() ) ) {
			show = Show.BEGINNINGS.getShowName();
		}

		return show;
	}
	
	private int resetVipEpisodes() {
		Log.v( TAG, "resetVipEpisodes : enter" );
		
		ContentValues values = new ContentValues();
		values.put( EpisodeConstants.FIELD_VIP, 1 );
		
		int updated = mContext.getContentResolver().update( EpisodeConstants.CONTENT_URI, values, EpisodeConstants.FIELD_VIP + " = ?", new String[] { "0" } );
		
		Log.v( TAG, "resetVipEpisodes : exit" );
		return updated;
	}

}
