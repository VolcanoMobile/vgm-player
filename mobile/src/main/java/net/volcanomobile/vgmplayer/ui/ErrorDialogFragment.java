package net.volcanomobile.vgmplayer.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;

/**
 * Created by philippesimons on 25/11/16.
 */
public class ErrorDialogFragment extends DialogFragment {

    private static String ARG_TITLE = "ARG_TITLE";
    private static String ARG_CONTENT = "ARG_CONTENT";

    public static ErrorDialogFragment newInstance(int title, int content) {
        ErrorDialogFragment fragment = new ErrorDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_CONTENT, content);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(getArguments().getInt(ARG_TITLE))
                .content(getArguments().getInt(ARG_CONTENT))
                .positiveText(android.R.string.ok);

        return builder.build();
    }
}
