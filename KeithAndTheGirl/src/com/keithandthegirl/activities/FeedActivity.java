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
package com.keithandthegirl.activities;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.code.rome.android.repackaged.com.sun.syndication.feed.synd.SyndEnclosure;
import com.google.code.rome.android.repackaged.com.sun.syndication.feed.synd.SyndEntry;
import com.google.code.rome.android.repackaged.com.sun.syndication.feed.synd.SyndFeed;
import com.keithandthegirl.MainApplication;
import com.keithandthegirl.MainApplication.PlayType;
import com.keithandthegirl.R;
import com.keithandthegirl.api.google.Feed;
import com.keithandthegirl.api.google.When;
import com.keithandthegirl.services.MediaPlayerService;
import com.keithandthegirl.services.UpdateCalendarService;
import com.keithandthegirl.services.UpdateFeedService;

/**
 * @author Daniel Frey
 *
 */
public class FeedActivity extends AbstractKatgListActivity implements OnClickListener {

	private static final String TAG = FeedActivity.class.getSimpleName();
	private static final DateTimeFormatter fmt = DateTimeFormat.forPattern( "MMM dd '@' hh:mm a" );

	private Intent mediaPlayerReceiverIntent, feedReceiverIntent, calendarReceiverIntent;
	
	private Button liveButton, callButton, stopButton, wwwButton;
	private TextView nowPlaying, nextShow;
	
	//***************************************
    // Activity methods
    //***************************************
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate( Bundle savedInstanceState ) {
	    Log.d( TAG, "onCreate : enter" );

	    super.onCreate( savedInstanceState );

	    setContentView( R.layout.main );
	    
	    mediaPlayerReceiverIntent = new Intent( this, MediaPlayerService.class );
	    feedReceiverIntent = new Intent( this, UpdateFeedService.class );
	    calendarReceiverIntent = new Intent( this, UpdateCalendarService.class );
	    
	    liveButton = (Button) findViewById( R.id.live_button );
	    callButton = (Button) findViewById( R.id.call_button );
	    stopButton = (Button) findViewById( R.id.stop_button );
	    wwwButton = (Button) findViewById( R.id.www_button );
	    
	    nowPlaying = (TextView) findViewById( R.id.now_playing );
	    nextShow = (TextView) findViewById( R.id.next_show );
	    
	    liveButton.setOnClickListener( this );
	    callButton.setOnClickListener( this );
	    stopButton.setOnClickListener( this );
	    wwwButton.setOnClickListener( this );
	    
	    Log.d( TAG, "onCreate : exit" );
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onStart()
	 */
	@Override
	public void onStart() {
	    Log.d( TAG, "onStart : enter" );

	    super.onStart();
	    
		Log.d( TAG, "onStart : exit" );
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
	    Log.d( TAG, "onPause : enter" );

		super.onPause();
		
		unregisterReceiver( mediaPlayerBroadcastReceiver );

		unregisterReceiver( updateFeedBroadcastReceiver );
		stopService( feedReceiverIntent ); 		

		unregisterReceiver( updateCalendarBroadcastReceiver );
		stopService( calendarReceiverIntent ); 		
		
	    Log.d( TAG, "onPause : exit" );
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	public void onResume() {
	    Log.d( TAG, "onResume : enter" );

	    super.onResume();

		registerReceiver( mediaPlayerBroadcastReceiver, new IntentFilter( MediaPlayerService.BROADCAST_ACTION ) );

		registerReceiver( updateFeedBroadcastReceiver, new IntentFilter( UpdateFeedService.BROADCAST_ACTION ) );
		if( null == ( (MainApplication) getApplicationContext() ).getFeed() ) {
		    startService( feedReceiverIntent );
		} else {
			refreshLiveStreamInfo();
		}

		registerReceiver( updateCalendarBroadcastReceiver, new IntentFilter( UpdateCalendarService.BROADCAST_ACTION ) );
		if( null == ( (MainApplication) getApplicationContext() ).getCalendarFeed() ) {
			startService( calendarReceiverIntent );
			//showProgressDialog();
		} else {
			refreshFeedEntries();
		}
		
		updateNowPlaying();
	    
		Log.d( TAG, "onResume : exit" );
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
	    Log.d( TAG, "onCreateOptionsMenu : enter" );

	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate( R.menu.feed_menu, menu );

	    Log.d( TAG, "onCreateOptionsMenu : exit" );
	    return super.onCreateOptionsMenu( menu );
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {		
	    Log.d( TAG, "onOptionsItemSelected : enter" );

	    Intent intent = new Intent();
	    
	    // Handle item selection
	    switch( item.getItemId() ) {
	    case android.R.id.home:
            // app icon in action bar clicked; go home
            intent = new Intent( this, FeedActivity.class );
            intent.addFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK );
            startActivity( intent );
	    	break;
	    case R.id.feed_menu_refresh:
	    	
		    startService( feedReceiverIntent );

		    startService( calendarReceiverIntent );
			//showProgressDialog();

	    	Log.d( TAG, "onOptionsItemSelected : exit, refresh option selected" );
	    	break;
	    case R.id.feed_menu_about:
			intent.setClass( this, AboutActivity.class );
			startActivity( intent );

	    	Log.d( TAG, "onOptionsItemSelected : exit, about option selected" );
	    	break;
	    case R.id.feed_menu_quit:
		    stopService( new Intent( this, MediaPlayerService.class ) );

			finish();
	    	
	    	Log.d( TAG, "onOptionsItemSelected : exit, quit option selected" );
	    	break;
	    }
	    
	    return super.onOptionsItemSelected( item );
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo)
	 */
	@Override
	public void onCreateContextMenu( ContextMenu menu, View v, ContextMenuInfo menuInfo ) {
		Log.d( TAG, "onCreateContextMenu : enter" );
		
		super.onCreateContextMenu( menu, v, menuInfo );
		MenuInflater inflater = getMenuInflater();
	    inflater.inflate( R.menu.feed_entry_context_menu, menu );

	    Log.d( TAG, "onCreateContextMenu : exit" );
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		Log.d( TAG, "onContextItemSelected : enter" );
		
	    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        Log.d( TAG, "onContextItemSelected : info.id=" + info.id + ", info.position=" + info.position );

        setCurrentEntry( (SyndEntry) ( (MainApplication) getApplicationContext() ).getFeed().getEntries().get( info.position ) );
        
        switch( item.getItemId() ) {
	        case R.id.feed_entry_context_menu_play:
	        	( (MainApplication) getApplicationContext() ).setSelectedPlayType( PlayType.RECORDED );
	        	play();
	        	
	            Log.d( TAG, "onContextItemSelected : exit, play selected" );
	            return true;
//	        case R.id.feed_entry_context_menu_download:
//	            download();
//
//	            Log.d( TAG, "onContextItemSelected : exit, download selected" );
//	            return true;
	        default:
	    		Log.d( TAG, "onContextItemSelected : exit" );
	            return super.onContextItemSelected( item );
	    }
    }

	/* (non-Javadoc)
	 * @see android.view.View.OnClickListener#onClick(android.view.View)
	 */
	@Override
	public void onClick( View v ) {
		Log.d( TAG, "onClick : enter" );
		
		Uri uri;
		
		switch( v.getId() ) {
			case R.id.live_button :
				Log.v( TAG, "onClick : live button pressed" );
				
				( (MainApplication) getApplicationContext() ).setSelectedPlayType( PlayType.LIVE );
				play();
				
				break;
			case R.id.stop_button :
				Log.v( TAG, "onClick : stop button pressed" );
				
				stopService( new Intent( this, MediaPlayerService.class ) );
				clearNowPlaying();
				
				break;
		    case R.id.www_button:
		    	Log.d( TAG, "onClick : www button pressed" );

				uri = Uri.parse( MainApplication.KATG_WEB_SITE );
				Intent intent = new Intent( Intent.ACTION_VIEW, uri );
				startActivity( intent );
				
		    	break;
		    case R.id.call_button :
		    	Log.d( TAG, "onClick : call button pressed" );

		    	uri = Uri.parse( "tel:" + MainApplication.KATG_PHONE_NUMBER );
				intent = new Intent( Intent.ACTION_DIAL, uri );
				startActivity( intent );
				
		    	break;
			default:
				Log.v( TAG, "onClick : no button pressed" );
				
				break;
		}
		
		Log.d( TAG, "onClick : exit" );
	}


	//***************************************
    // Private methods
    //***************************************

	@SuppressWarnings( "unchecked" )
	private void refreshFeedEntries() {
		Log.d( TAG, "refreshFeedEntries : enter" );
		
		SyndFeed feed = ( (MainApplication) getApplicationContext() ).getFeed();
		if( null != feed && !feed.getEntries().isEmpty() ) {
			ListAdapter adapter = new FeedEntryListAdapter( feed.getEntries(), this );
	
			registerForContextMenu( getListView() );

			ListView lv = getListView();
		    lv.setAdapter( adapter );
		    lv.setClickable( true );
		    lv.setOnItemClickListener( new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick( AdapterView<?> arg0, View v, int position, long arg3 ) {
					Log.v( TAG, "onItemClick : enter" );
					
					setCurrentEntry( (SyndEntry) ( (MainApplication) getApplicationContext() ).getFeed().getEntries().get( position ) );
					( (MainApplication) getApplicationContext() ).setSelectedPlayType( PlayType.RECORDED );
					play();
					
					Log.v( TAG, "onItemClick : exit" );
				}
		    	
		    });
		}

		Log.d( TAG, "refreshFeedEntries : exit" );
	}
	
	private void refreshLiveStreamInfo() {
		Log.d( TAG, "refreshLiveStreamInfo : enter" );
		
		Feed calendarFeed = ( (MainApplication) getApplicationContext() ).getCalendarFeed();
		if( null != calendarFeed && !calendarFeed.getEntries().isEmpty() ) {

			DateTime now = new DateTime();
			When when = calendarFeed.getEntries().get( 0 ).getWhen();
			
			Log.d( TAG, "refreshLiveStreamInfo : after start=" + now.isAfter( when.getStartTime() )  );
			Log.d( TAG, "refreshLiveStreamInfo : before end=" + now.isBefore( when.getEndTime() )  );

			if( now.isAfter( when.getStartTime() ) && now.isBefore( when.getEndTime() ) ) {
				Log.v( TAG, "refreshLiveStreamInfo : live streaming now!" );
				
				liveButton.setEnabled( true );
				callButton.setEnabled( true );
			} else {
				Log.v( TAG, "refreshLiveStreamInfo : NOT live streaming now!" );

				liveButton.setEnabled( false );
				callButton.setEnabled( false );
			}
			
			nextShow.setText( fmt.print( when.getStartTime() ) );
		}
		
		Log.d( TAG, "refreshLiveStreamInfo : exit" );
	}
	
	private void setCurrentEntry( SyndEntry entry ) {
		Log.d( TAG, "setCurrentEntry : enter" );

		( (MainApplication) getApplicationContext() ).setSelectedEntry( entry );

		Log.d( TAG, "Entry Title=" + entry.getTitle() );
		Log.d( TAG, "Entry Description=" + entry.getDescription() );
		Log.d( TAG, "Entry Link=" + entry.getLink() );
		Log.d( TAG, "Entry Publish Date=" + entry.getPublishedDate() );

		for( Object obj1 : entry.getEnclosures() ) {
			SyndEnclosure enclosure = (SyndEnclosure) obj1;
			Log.d( TAG, "Entry Enclosure Url=" + enclosure.getUrl() );
			Log.d( TAG, "Entry Enclosure Length=" + enclosure.getLength() );
			Log.d( TAG, "Entry Enclosure Type=" + enclosure.getType() );
		}

		Log.d( TAG, "setCurrentEntry : exit" );
	}
	
	private void play() {
		Log.d( TAG, "play : enter" );
		
	    startService( mediaPlayerReceiverIntent );
		updateNowPlaying();
		
		Log.d( TAG, "play : exit" );
	}
	
	private void updateNowPlaying() {
		Log.d( TAG, "updateNowPlaying : enter" );

		if( null != ( (MainApplication) getApplicationContext() ).getSelectedPlayType() ) {
			switch( ( (MainApplication) getApplicationContext() ).getSelectedPlayType() ) {
			case LIVE:
				nowPlaying.setText( "Streaming Live!" );

				break;
			case RECORDED:
				if( null != ( (MainApplication) getApplicationContext() ).getSelectedEntry() ) {
					nowPlaying.setText( ( (MainApplication) getApplicationContext() ).getSelectedEntry().getTitle() );
				}

				break;
			default:
				break;
			}
		}
		
		Log.d( TAG, "updateNowPlaying : exit" );
	}
	
	private void clearNowPlaying() {
		Log.d( TAG, "clearNowPlaying : enter" );

		nowPlaying.setText( "" );

		( (MainApplication) getApplicationContext() ).setSelectedEntry( null );
		( (MainApplication) getApplicationContext() ).setSelectedPlayType( null );
		
		Log.d( TAG, "clearNowPlaying : exit" );
	}

    private BroadcastReceiver updateCalendarBroadcastReceiver = new BroadcastReceiver() {
    	
        @Override
        public void onReceive( Context context, Intent intent ) {
    		Log.d( TAG, "onReceive : enter" );

    		refreshLiveStreamInfo();
    		
    		Log.d( TAG, "onReceive : exit" );
        }
        
    };
    
    private BroadcastReceiver updateFeedBroadcastReceiver = new BroadcastReceiver() {
    	
        @Override
        public void onReceive( Context context, Intent intent ) {
    		Log.d( TAG, "onReceive : enter" );

    		refreshFeedEntries();
    		//dismissProgressDialog();
    		
    		Log.d( TAG, "onReceive : exit" );
        }
        
    };
    
    private BroadcastReceiver mediaPlayerBroadcastReceiver = new BroadcastReceiver() {
    	
        @Override
        public void onReceive( Context context, Intent intent ) {
    		Log.d( TAG, "onReceive : enter" );

    		clearNowPlaying();
    		
    		Log.d( TAG, "onReceive : exit" );
        }
        
    };
    
}
