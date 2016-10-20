package org.edx.mobile.view.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import org.edx.mobile.R;

/**
 * Note: This is a very simple implementation that only shows an {@link AlertDialog} with a given
 * message and an OK button without any listener.
 *
 * In the future, more customizability might be added as need.
 */

public class AlertDialogFragment extends DialogFragment {
    protected static final String ARG_TITLE = "title";
    protected static final String ARG_TITLE_RES = "titleRes";
    protected static final String ARG_MESSAGE = "message";
    protected static final String ARG_MESSAGE_RES = "messageRes";

    public interface ButtonAttributes {
        @NonNull
        String getMessage();

        @Nullable
        DialogInterface.OnClickListener getOnClickListener();
    }

    public static AlertDialogFragment newInstance(@StringRes int titleResId, @StringRes int messageResId, @Nullable DialogInterface.OnClickListener onPositiveClick) {
        AlertDialogFragment fragment = new AlertDialogFragment();
        fragment.mOnPositiveClick = onPositiveClick;
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE_RES, titleResId);
        args.putInt(ARG_MESSAGE_RES, messageResId);
        fragment.setArguments(args);
        return fragment;
    }

    public static AlertDialogFragment newInstance(@Nullable String title, @NonNull String message, @Nullable DialogInterface.OnClickListener onPositiveClick) {
        AlertDialogFragment fragment = new AlertDialogFragment();
        fragment.mOnPositiveClick = onPositiveClick;
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    private DialogInterface.OnClickListener mOnPositiveClick;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        int titleResId = args.getInt(ARG_TITLE_RES);
        int messageResId = args.getInt(ARG_MESSAGE_RES);
        CharSequence title = titleResId != 0 ?
                getText(titleResId) : args.getString(ARG_TITLE);
        CharSequence message = messageResId != 0 ?
                getText(messageResId) : args.getString(ARG_MESSAGE);

        ButtonAttributes positiveButtonAttributes = getPositiveButtonAttributes();
        ButtonAttributes negativeButtonAttributes = getNegativeButtonAttributes();

        AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                .setMessage(message)
                .setPositiveButton(positiveButtonAttributes.getMessage(), positiveButtonAttributes.getOnClickListener())
                .create();

        alertDialog.setCanceledOnTouchOutside(false);

        if (title != null) {
            alertDialog.setTitle(title);
        }

        if (negativeButtonAttributes != null) {
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, negativeButtonAttributes.getMessage(), negativeButtonAttributes.getOnClickListener());
        }

        return alertDialog;
    }

    @NonNull
    protected ButtonAttributes getPositiveButtonAttributes() {
        return new ButtonAttributes() {

            @NonNull
            @Override
            public String getMessage() {
                return getContext().getResources().getString(R.string.label_ok);
            }

            @Nullable
            @Override
            public DialogInterface.OnClickListener getOnClickListener() {
                return mOnPositiveClick;
            }
        };
    }

    @Nullable
    protected ButtonAttributes getNegativeButtonAttributes() {
        return null;
    }
}