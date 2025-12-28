package com.eleybourn.bookcatalogue.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.utils.Logger;

public class MessageDialogFragment extends BookCatalogueDialogFragment {
	private int mDialogId;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public interface OnMessageDialogResultListener {
		void onMessageDialogResult(int dialogId, MessageDialogFragment dialog, int button);
	}

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param titleId	Title to display
	 *
	 * @return			Created fragment
	 */
	public static MessageDialogFragment newInstance(int dialogId, int titleId, int messageId, int buttonPositiveTextId, int buttonNegativeTextId, int buttonNeutralTextId) {
		String message = BookCatalogueApp.getResourceString(messageId);
        return MessageDialogFragment.newInstance(dialogId, titleId, message, buttonPositiveTextId, buttonNegativeTextId, buttonNeutralTextId);
    }

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param titleId	Title to display
	 *
	 * @return			Created fragment
	 */
	public static MessageDialogFragment newInstance(int dialogId, int titleId, String message, int buttonPositiveTextId, int buttonNegativeTextId, int buttonNeutralTextId) {
		MessageDialogFragment frag = new MessageDialogFragment();
        Bundle args = new Bundle();
        args.putInt("dialogId", dialogId);
        args.putInt("titleId", titleId);
        args.putString("message", message);
        args.putInt("buttonPositiveTextId", buttonPositiveTextId);
        args.putInt("buttonNegativeTextId", buttonNegativeTextId);
        args.putInt("buttonNeutralTextId", buttonNeutralTextId);
        frag.setArguments(args);
        return frag;
    }

	/**
	 * Ensure activity supports event
	 */
	@Override
    public void onAttach (@NonNull Context context) {
        super.onAttach(context);

        Activity a = null;
        if (context instanceof Activity){
            a=(Activity) context;
        }

		if (! (a instanceof OnMessageDialogResultListener))
			throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnMessageDialogResultListener");
		
	}

	/**
	 * Create the underlying dialog
	 */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
    	mDialogId = getArguments().getInt("dialogId");
        int title = getArguments().getInt("titleId");
        String msg = getArguments().getString("message");
        int btnPos = getArguments().getInt("buttonPositiveTextId");
        int btnNeg = getArguments().getInt("buttonNegativeTextId");
        int btnNeut = getArguments().getInt("buttonNeutralTextId");
        
		AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setMessage(msg).create();
		alertDialog.setTitle(title);
		alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
		alertDialog.setCanceledOnTouchOutside(false);

		alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(btnPos), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				handleButton(AlertDialog.BUTTON_POSITIVE);
			}
		}); 
		
		if (btnNeg != 0) {
			alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(btnNeg), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					handleButton(AlertDialog.BUTTON_NEGATIVE);
				}
			}); 			
		}
		if (btnNeut != 0) {
			alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(btnNeut), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					handleButton(AlertDialog.BUTTON_NEUTRAL);
				}
			}); 			
		}

        return alertDialog;
    }
    
    private void handleButton(int button) {
    	try {
    		OnMessageDialogResultListener a = (OnMessageDialogResultListener)getActivity();
    		if (a != null)
	        	a.onMessageDialogResult(mDialogId, this, button);    		
    	} catch (Exception e) {
    		Logger.logError(e);
    	}
    	dismiss();
    }
}
