package com.darexsh.revancedupdater;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

final class FancyDialogs {

    private FancyDialogs() {
    }

    static AlertDialog showMessageDialog(
            AppCompatActivity activity,
            CharSequence title,
            CharSequence message
    ) {
        return showMessageDialog(activity, title, message, R.string.ok, null, 0, null, true);
    }

    static AlertDialog showMessageDialog(
            AppCompatActivity activity,
            CharSequence title,
            CharSequence message,
            int positiveTextRes,
            @Nullable Runnable onPositive,
            int negativeTextRes,
            @Nullable Runnable onNegative,
            boolean cancelable
    ) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_fancy_selection, null);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        FrameLayout dialogContentContainer = dialogView.findViewById(R.id.dialogContentContainer);
        MaterialButton btnDialogPositive = dialogView.findViewById(R.id.btnDialogPositive);
        MaterialButton btnDialogNegative = dialogView.findViewById(R.id.btnDialogNegative);

        tvDialogTitle.setText(title);

        TextView messageView = new TextView(activity);
        messageView.setText(message);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        messageView.setLineSpacing(0f, 1.15f);
        messageView.setTextColor(ContextCompat.getColor(activity, R.color.btnTextColor));
        dialogContentContainer.addView(messageView);

        if (positiveTextRes != 0) {
            btnDialogPositive.setText(positiveTextRes);
        } else {
            btnDialogPositive.setVisibility(View.GONE);
        }

        if (negativeTextRes != 0) {
            btnDialogNegative.setText(negativeTextRes);
        } else {
            btnDialogNegative.setVisibility(View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);

        btnDialogPositive.setOnClickListener(v -> {
            if (onPositive != null) {
                onPositive.run();
            }
            dialog.dismiss();
        });
        btnDialogNegative.setOnClickListener(v -> {
            if (onNegative != null) {
                onNegative.run();
            }
            dialog.dismiss();
        });

        dialog.show();
        styleWindow(dialog, 0.86f);
        return dialog;
    }

    static void showContentDialog(AppCompatActivity activity, CharSequence title, View content, int positiveTextRes) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_fancy_selection, null);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        FrameLayout dialogContentContainer = dialogView.findViewById(R.id.dialogContentContainer);
        MaterialButton btnDialogPositive = dialogView.findViewById(R.id.btnDialogPositive);
        MaterialButton btnDialogNegative = dialogView.findViewById(R.id.btnDialogNegative);

        tvDialogTitle.setText(title);
        dialogContentContainer.addView(content);
        btnDialogPositive.setText(positiveTextRes);
        btnDialogNegative.setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();
        btnDialogPositive.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        styleWindow(dialog, 0.86f);
    }

    static void styleWindow(AlertDialog dialog, float widthPercent) {
        if (dialog.getWindow() == null) return;
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int dialogWidth = (int) (dialog.getContext().getResources().getDisplayMetrics().widthPixels * widthPercent);
        dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
