package com.tunjid.rcswitchcontrol.dialogfragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.tunjid.rcswitchcontrol.R;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import static java.util.Objects.requireNonNull;


@SuppressLint("InflateParams")
public class NameServiceDialogFragment extends DialogFragment {

    public static NameServiceDialogFragment newInstance() {

        NameServiceDialogFragment fragment = new NameServiceDialogFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public NameServiceDialogFragment() {

    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final ServiceNameListener nameListener = (ServiceNameListener) getParentFragment();
        final LayoutInflater inflater = requireActivity().getLayoutInflater();

        final View view = inflater.inflate(R.layout.dialog_rename_switch, null);
        final EditText editText = view.findViewById(R.id.switch_name);

        return new AlertDialog.Builder(getActivity()).setTitle(R.string.name_nsd_service)
                .setView(view)
                .setPositiveButton(R.string.ok, (dialog, id) -> {
                    requireNonNull(nameListener).onServiceNamed(editText.getText().toString());
                    dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dismiss())
                .create();
    }

    public interface ServiceNameListener {
        void onServiceNamed(String name);
    }
}