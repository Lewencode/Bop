package com.sahdeepsingh.Bop.services;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.cleveroad.audiowidget.AudioWidget;
import com.sahdeepsingh.Bop.R;
import com.sahdeepsingh.Bop.SongData.Song;
import com.sahdeepsingh.Bop.notifications.MediaNotificationManager;
import com.sahdeepsingh.Bop.playerMain.Main;
import com.sahdeepsingh.Bop.utils.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.MediaSessionManager;
import androidx.media.session.MediaButtonReceiver;

public class ServicePlayMusic extends MediaBrowserServiceCompat
        implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener {


    public static final String ACTION_CMD = "com.example.android.uamp.ACTION_CMD";

    public static final String CMD_NAME = "CMD_NAME";

    public static final String CMD_PAUSE = "CMD_PAUSE";

    // Delay stopSelf by using a handler.

    private static final int STOP_DELAY = 30000;

    public static final String BROADCAST_ORDER_PLAY = "com.sahdeepsingh.Bop.action.PLAY";
    public static final String BROADCAST_ORDER_PAUSE = "com.sahdeepsingh.Bop.action.PAUSE";
    public static final String BROADCAST_ORDER_STOP = "com.sahdeepsingh.Bop.action.STOP";
    public static final String BROADCAST_ORDER_SKIP = "com.sahdeepsingh.Bop.action.SKIP";
    public static final String BROADCAST_ORDER_REWIND = "com.sahdeepsingh.Bop.action.REWIND";

    // The tag we put on debug messages
    final static String TAG = "MusicService";
    /**
     * Token for the interaction between an Activity and this Service.
     */
    private final IBinder musicBind = new MusicBinder();
    /**
     * Index of the current song we're playing on the `songs` list.
     */
    public int currentSongPosition;
    /**
     * Copy of the current song being played (or paused).
     * <p>
     * Use it to get info from the current song.
     */
    public Song currentSong = null;
    /**
     * Tells if this service is bound to an Activity.
     */
    public boolean musicBound = false;
    /**
     * Current state of the Service.
     */
    ServiceState serviceState = ServiceState.Preparing;
    /**
     * Use this to get audio focus:
     * <p>
     * 1. Making sure other music apps don't play
     * at the same time;
     * 2. Guaranteeing the lock screen widget will
     * be controlled by us;
     */
    AudioManager audioManager;

    /**
     * Will keep an eye on global broadcasts related to
     * the Headset.
     */

    public MediaSessionCompat mMediaSessionCompat;
    public MediaSessionManager mediaSessionManager;
    public MediaController mediaController;

    AudioWidget audioWidget;
    BroadcastReceiver headsetBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            // Headphones just connected (or not)
            if (action != null && action.equals(Intent.ACTION_HEADSET_PLUG)) {

                Log.w(TAG, "headset plug");
                boolean connectedHeadphones = (intent.getIntExtra("state", 0) == 1);
                boolean connectedMicrophone = (intent.getIntExtra("microphone", 0) == 1) && connectedHeadphones;

                // User just connected headphone and the player was paused,
                // so we should restart the music.
                if (connectedMicrophone && (serviceState == ServiceState.Paused)) {

                    // Will only do it if it's Setting is enabled, of course
                    if (Main.settings.get("play_headphone_on", true)) {

                    }
                }

                // I wonder what's this for
                String headsetName = intent.getStringExtra("name");

                if (connectedHeadphones) {
                    String text = context.getString(R.string.headphone_connected) + headsetName;

                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };
    /**
     * Android Media Player - we control it in here.
     */
    public MediaPlayer player;
    /**
     * List of songs we're  currently playing.
     */
    private ArrayList<Song> songs;
    /**
     * Flag that indicates whether we're at Shuffle mode.
     */
    private boolean shuffleMode = false;
    /**
     * Random number generator for the Shuffle Mode.
     */
    private Random randomNumberGenerator;
    // 0 single, 1 repeat on , 2 repeat off
    private int repeatMode = 0;
    /**
     * Spawns an on-going notification with our current
     * playing song.
     */
    // private NotificationMusic notification = null;
    MediaNotificationManager notificationManager;


    // Internal flags for the function above {{
    private boolean pausedTemporarilyDueToAudioFocus = false;
    private boolean loweredVolumeDueToAudioFocus = false;
    private BroadcastReceiver mNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (player != null && player.isPlaying()) {
                player.pause();
            }
        }
    };
    private MediaSessionCompat.Callback mMediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            super.onPlay();
            playSong();
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            super.onPlayFromMediaId(mediaId, extras);
        }

        @Override
        public void onPause() {
            super.onPause();
            pausePlayer();
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            next(true);
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            previous(true);
        }

        @Override
        public void onStop() {
            super.onStop();
            stopSelf();
        }
    };

    /**
     * Whenever we're created, reset the MusicPlayer and
     * start the MusicScrobblerService.
     */


    public void onCreate() {
        super.onCreate();

        currentSongPosition = 0;

        randomNumberGenerator = new Random();

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        if (Main.settings.get("showFloatingWidget", true))
            createAudioWidget();

        initMusicPlayer();

        initMediaSession();

        initNoisyReceiver();


        try {
            notificationManager = new MediaNotificationManager(this);
        } catch (RemoteException e) {
            throw new IllegalStateException("Could not create a MediaNotificationManager", e);
        }

        // to user plugging the headset.
        IntentFilter headsetFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetBroadcastReceiver, headsetFilter);

        Log.w(TAG, "onCreate");

    }

    private void initNoisyReceiver() {
        //Handles headphones coming unplugged. cannot be done through a manifest receiver
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mNoisyReceiver, filter);
    }

    private void initMediaSession() {
        ComponentName mediaButtonReceiver = new ComponentName(getApplicationContext(), MediaButtonReceiver.class);
        mMediaSessionCompat = new MediaSessionCompat(getApplicationContext(), "Tag", mediaButtonReceiver, null);

        mMediaSessionCompat.setCallback(mMediaSessionCallback);
        mMediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(this, MediaButtonReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);
        mMediaSessionCompat.setMediaButtonReceiver(pendingIntent);

        setSessionToken(mMediaSessionCompat.getSessionToken());
    }

    private void createAudioWidget() {

        audioWidget = new AudioWidget.Builder(getApplicationContext()).build();
    }

    /**
     * Initializes the Android's internal MediaPlayer.
     *
     * @note We might call this function several times without
     * necessarily calling {@link #stopMusicPlayer()}.
     */
    public void initMusicPlayer() {
        if (player == null)
            player = new MediaPlayer();

        // Assures the CPU continues running this service
        // even when the device is sleeping.
        player.setWakeMode(getApplicationContext(),
                PowerManager.PARTIAL_WAKE_LOCK);

        player.setAudioStreamType(AudioManager.STREAM_MUSIC);

        // These are the events that will "wake us up"
        player.setOnPreparedListener(this); // player initialized
        player.setOnCompletionListener(this); // song completed
        player.setOnErrorListener(this);

        Log.w(TAG, "initMusicPlayer");


    }

    /**
     * Cleans resources from Android's native MediaPlayer.
     *
     * @note According to the MediaPlayer guide, you should release
     * the MediaPlayer as often as possible.
     * For example, when losing Audio Focus for an extended
     * period of time.
     */
    public void stopMusicPlayer() {
        if (player == null)
            return;

        player.stop();
        player.release();
        player = null;

        Log.w(TAG, "stopMusicPlayer");
    }

    /**
     * Sets the "Now Playing List"
     *
     * @param theSongs Songs list that will play from now on.
     * @note Make sure to call {@link #playSong()} after this.
     */
    public void setList(ArrayList<Song> theSongs) {
        songs = theSongs;
    }

    /**
     * Appends a song to the end of the currently playing queue.
     *
     * @param song New song to put at the end.
     */
    public void add(Song song) {
        songs.add(song);
    }

    /**
     * Asks the AudioManager for our application to
     * have the audio focus.
     *
     * @return If we have it.
     */
    private boolean requestAudioFocus() {
        //Request audio focus for playback
        int result = audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        //Check if audio focus was granted. If not, stop the service.
        return (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    /**
     * Does something when the audio focus state changed
     *
     * @note Meaning it runs when we get and when we don't get
     * the audio focus from `#requestAudioFocus()`.
     * <p>
     * For example, when we receive a message, we lose the focus
     * and when the ringer stops playing, we get the focus again.
     * <p>
     * So we must avoid the bug that occurs when the user pauses
     * the player but receives a message - and since after that
     * we get the focus, the player will unpause.
     */
    public void onAudioFocusChange(int focusChange) {

        switch (focusChange) {

            // Yay, gained audio focus! Either from losing it for
            // a long or short periods of time.
            case AudioManager.AUDIOFOCUS_GAIN:
                Log.w(TAG, "audiofocus gain");

                if (player == null)
                    initMusicPlayer();

                if (pausedTemporarilyDueToAudioFocus) {
                    pausedTemporarilyDueToAudioFocus = false;
                    unpausePlayer();
                }

                if (loweredVolumeDueToAudioFocus) {
                    loweredVolumeDueToAudioFocus = false;
                    player.setVolume(1.0f, 1.0f);
                }
                break;

            // Damn, lost the audio focus for a (presumable) long time
            case AudioManager.AUDIOFOCUS_LOSS:
                Log.w(TAG, "audiofocus loss");

                // Giving up everything
                //audioManager.unregisterMediaButtonEventReceiver(mediaButtonEventReceiver);
                //audioManager.abandonAudioFocus(this);

                //pausePlayer();
                stopMusicPlayer();
                break;

            // Just lost audio focus but will get it back shortly
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Log.w(TAG, "audiofocus loss transient");

                if (!isPaused()) {
                    pausePlayer();
                    pausedTemporarilyDueToAudioFocus = true;
                }
                break;

            // Temporarily lost audio focus but I can keep it playing
            // at a low volume instead of stopping completely
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.w(TAG, "audiofocus loss transient can duck");

                player.setVolume(0.1f, 0.1f);
                loweredVolumeDueToAudioFocus = true;
                break;
        }
    }


    /**
     * Called when the music is ready for playback.
     */
    @Override
    public void onPrepared(MediaPlayer mp) {

        serviceState = ServiceState.Playing;

        // Start playback
        player.start();

        setMediaSessionMetaData();

        // If the user clicks on the notification, let's spawn the
        // Now Playing screen.
        notifyCurrentSong();
    }

    private void setMediaSessionMetaData() {
        mMediaSessionCompat.setMetadata(new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, utils.getBitmapfromAlbumId(getApplicationContext(), currentSong))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSong.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentSong.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong.getTitle())
                .build());
    }

    /**
     * Sets a specific song, already within internal Now Playing List.
     *
     * @param songIndex Index of the song inside the Now Playing List.
     */
    public void setSong(int songIndex) {

        if (songIndex < 0 || songIndex >= songs.size())
            currentSongPosition = 0;
        else
            currentSongPosition = songIndex;
    }

    /**
     * Will be called when the music completes - either when the
     * user presses 'next' or when the music ends or when the user
     * selects another track.
     */
    @Override
    public void onCompletion(MediaPlayer mp) {

        // Keep this state!
        serviceState = ServiceState.Playing;

        // Repeating current song if desired
        if (repeatMode == 0) {
            playSong();
            return;
        }

        // Remember that by calling next(), if played
        // the last song on the list, will reset to the
        // first one.
        next(false);

        // Reached the end, should we restart playing
        // from the first song or simply stop?
        if (currentSongPosition == 0) {
            if (Main.settings.get("repeat_list", false))
                playSong();

            else
                destroySelf();
            return;
        }
        // Common case - skipped a track or anything
        playSong();
    }

    /**
     * If something wrong happens with the MusicPlayer.
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mp.reset();
        Log.w(TAG, "onError");
        return false;
    }

    @Override
    public void onDestroy() {

        cancelNotification();

        currentSong = null;

        if (audioManager != null)
            audioManager.abandonAudioFocus(this);

        stopMusicPlayer();

        if (player != null)
            //player.release();

            Log.w(TAG, "onDestroy");

        unregisterReceiver(headsetBroadcastReceiver);
        unregisterReceiver(mNoisyReceiver);
        super.onDestroy();
    }

    /**
     * Kills the service.
     *
     * @note Explicitly call this when the service is completed
     * or whatnot.
     */
    private void destroySelf() {
        stopSelf();
        currentSong = null;
    }

    // These methods are to be called by the Activity
    // to work on the music-playing.

    /**
     * Jumps to the previous song on the list.
     *
     * @note Remember to call `playSong()` to make the MusicPlayer
     * actually play the music.
     */
    public void previous(boolean userSkippedSong) {
        if (serviceState != ServiceState.Paused && serviceState != ServiceState.Playing)
            return;

        currentSongPosition--;
        if (currentSongPosition < 0)
            currentSongPosition = songs.size() - 1;
    }

    /**
     * Jumps to the next song on the list.
     *
     * @note Remember to call `playSong()` to make the MusicPlayer
     * actually play the music.
     */
    public void next(boolean userSkippedSong) {
        if (serviceState != ServiceState.Paused && serviceState != ServiceState.Playing)
            return;


        if (shuffleMode) {
            int newSongPosition = currentSongPosition;

            while (newSongPosition == currentSongPosition)
                newSongPosition = randomNumberGenerator.nextInt(songs.size());

            currentSongPosition = newSongPosition;
            return;
        }

        currentSongPosition++;

        if (currentSongPosition >= songs.size())
            currentSongPosition = 0;
    }

    public int getPosition() {
        return player.getCurrentPosition();
    }

    public int getDuration() {
        return player.getDuration();
    }

    public boolean isPlaying() {
        boolean returnValue = false;
        if (player != null)
            try {
                returnValue = player.isPlaying();
            } catch (IllegalStateException | NullPointerException e) {

            }

        return returnValue;
    }

    public boolean isPaused() {
        return serviceState == ServiceState.Paused;
    }

    /**
     * Actually plays the song set by `currentSongPosition`.
     */
    public void playSong() {

        if (player == null)
            initMusicPlayer();
        player.reset();
        // Get the song ID from the list, extract the ID and
        // get an URL based on it
        Song songToPlay = songs.get(currentSongPosition);
        Main.songs.addsong_toRecent(getApplicationContext(), songToPlay);
        Main.songs.addcountSongsPlayed(getApplicationContext(), songToPlay);
        currentSong = songToPlay;

        // Append the external URI with our songs'
        Uri songToPlayURI = ContentUris.withAppendedId
                (android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        songToPlay.getId());
        try {
            player.setDataSource(getApplicationContext(), songToPlayURI);
        } catch (IOException io) {
            Log.e(TAG, "IOException: couldn't change the song", io);
            destroySelf();
        } catch (Exception e) {
            Log.e(TAG, "Error when changing the song", e);
            destroySelf();
        }

        // Prepare the MusicPlayer asynchronously.
        // When finished, will call `onPrepare`
        player.prepareAsync();

        mMediaSessionCompat.setActive(true);
        setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);

        serviceState = ServiceState.Preparing;

        Log.w(TAG, "play song");
    }


    private void setMediaPlaybackState(int state) {
        PlaybackStateCompat.Builder playbackstateBuilder = new PlaybackStateCompat.Builder();
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE);
        } else {
            playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY);
        }
        playbackstateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0);
        mMediaSessionCompat.setPlaybackState(playbackstateBuilder.build());
    }

    public void pausePlayer() {
        if (serviceState != ServiceState.Paused && serviceState != ServiceState.Playing)
            return;

        player.pause();
        serviceState = ServiceState.Paused;

        //notification.notifyPaused(true);
        setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

    }

    public void unpausePlayer() {
        if (serviceState != ServiceState.Paused && serviceState != ServiceState.Playing)
            return;

        player.start();
        serviceState = ServiceState.Playing;

        //  notification.notifyPaused(false);
        setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);

    }

    /**
     * Toggles between Pause and Unpause.
     */
    public void togglePlayback() {
        if (serviceState == ServiceState.Paused)
            unpausePlayer();
        else
            pausePlayer();
    }

    public void seekTo(int position) {
        player.seekTo(position);
    }

    /**
     * Toggles the Shuffle mode
     * (if will play songs in random order).
     */
    public void toggleShuffle() {
        shuffleMode = !shuffleMode;
    }

    /**
     * Shuffle mode state.
     *
     * @return If Shuffle mode is on/off.
     */
    public boolean isShuffle() {
        return shuffleMode;
    }

    /**
     * Toggles the Repeat mode
     * (if the current song will play again
     * when completed).
     */
    public void toggleRepeat() {
        if (repeatMode == 2)
            repeatMode = 0;
        else repeatMode += 1;

    }

    /**
     * Repeat mode state.
     *
     * @return If Repeat mode is on/off.
     */
    public int isRepeat() {
        return repeatMode;
    }

    // THESE ARE METHODS RELATED TO CONNECTING THE SERVICE
    // TO THE ANDROID PLATFORM
    // NOTHING TO DO WITH MUSIC-PLAYING

    /**
     * Called when the Service is finally bound to the app.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        if (TextUtils.equals(clientPackageName, getPackageName())) {
            return new BrowserRoot(getString(R.string.app_name), null);
        }

        return null;
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(null);
    }


    /**
     * Called when the Service is unbound - user quitting
     * the app or something.
     */
    @Override
    public boolean onUnbind(Intent intent) {

        return false;
    }

    /**
     * Displays a notification on the status bar with the
     * current song and some nice buttons.
     */
    public void notifyCurrentSong() {

        if (currentSong == null)
            return;

        /*if (notification == null)
            notification = new NotificationMusic();

        notification.notifySong(this, this, currentSong);*/

        notificationManager.startNotification();


    }

    /**
     * Disables the hability to notify things on the
     * status bar.
     *
     * @see #notifyCurrentSong()
     */
    public void cancelNotification() {
       /* if (notification == null)
            return;

        notification.cancel();
        notification = null;*/
        notificationManager.stopNotification();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String action = intent.getAction();
            String command = intent.getStringExtra(CMD_NAME);
            if (ACTION_CMD.equals(action)) {
                if (CMD_PAUSE.equals(command)) {
                    pausePlayer();
                }
            } else {
                MediaButtonReceiver.handleIntent(mMediaSessionCompat, intent);
            }
        }
        // Do your other onStartCommand stuff..
        return START_STICKY;
    }

    public int getAudioSession() {
        if (player == null)
            return 0;
        else return player.getAudioSessionId();
    }

    /**
     * Possible states this Service can be on.
     */
    enum ServiceState {
        // MediaPlayer is stopped and not prepared to play
        Stopped,

        // MediaPlayer is preparing...
        Preparing,

        // Playback active - media player ready!
        // (but the media player may actually be paused in
        // this state if we don't have audio focus).
        Playing,

        // So that we know we have to resume playback once we get focus back)
        // playback paused (media player ready!)
        Paused
    }

    public class MusicBinder extends Binder {
        public ServicePlayMusic getService() {
            return ServicePlayMusic.this;
        }
    }

    public void removedFromNotification() {
        cancelNotification();
        player.stop();
        // Main.musicService.stopMusicPlayer();
        Main.mainMenuHasNowPlayingItem = false;
        Main.musicService.currentSong = null;

    }

}
