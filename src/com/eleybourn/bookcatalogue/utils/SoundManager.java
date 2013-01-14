package com.eleybourn.bookcatalogue.utils;

import java.io.IOException;

import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

public class SoundManager {

	public static final String TAG = "SoundManager";
	public static final String PREF_BEEP_IF_SCANNED_ISBN_INVALID = TAG + "." + "BeepIfScannedIsbnInvalid";
	public static final String PREF_BEEP_IF_SCANNED_ISBN_VALID = TAG + "." + "BeepIfScannedIsbnValid";

	public static void beepLow() {
		try {
			if (BookCatalogueApp.getAppPreferences().getBoolean(PREF_BEEP_IF_SCANNED_ISBN_INVALID, true)) {
				MediaPlayer player = initPlayer();
				AssetFileDescriptor file = BookCatalogueApp.context.getResources().openRawResourceFd(R.raw.beep_low);
				playFile(player, file);
			}
		} catch (Exception e) {
			// No sound is critical. Just log errors
			Logger.logError(e);
		}
	}
	
	public static void beepHigh() {
		try {
			if (BookCatalogueApp.getAppPreferences().getBoolean(PREF_BEEP_IF_SCANNED_ISBN_VALID, false)) {
				MediaPlayer player = initPlayer();
				AssetFileDescriptor file = BookCatalogueApp.context.getResources().openRawResourceFd(R.raw.beep_high);
				playFile(player, file);
			}
		} catch (Exception e) {
			// No sound is critical. Just log errors
			Logger.logError(e);
		}
	}
	
	private static MediaPlayer initPlayer() {
		MediaPlayer player = new MediaPlayer();
		player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		return player;
	}

	private static void playFile(final MediaPlayer player, final AssetFileDescriptor file) throws IllegalArgumentException, IllegalStateException, IOException {
	    // When the beep has finished playing, rewind to queue up another one.
		player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
	      @Override
	      public void onCompletion(MediaPlayer player) {
	        player.release();
	      }
	    });
	    player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
	    file.close();
	    player.setVolume(0.2f, 0.2f);
	    player.prepare();
	    player.start();
	}
}
