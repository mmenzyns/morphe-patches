package app.template.extension.alza;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ScrollView;

/**
 * Dialog displaying Morphe settings with 1-tap access from the chart header
 * or settings screens.
 */
public class MorpheSettingsDialog {
    private static final String TAG = "MorpheAlza";

    public static void show(Context context, final Runnable onDismiss) {
        if (context == null) return;
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            MorpheSettingsCardView cardView = new MorpheSettingsCardView(context);
            // In dialog, remove card border/background margins since dialog already has chrome
            cardView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(cardView);

            builder.setView(scrollView);
            builder.setPositiveButton("Hotovo", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            AlertDialog dialog = builder.create();
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface d) {
                    if (onDismiss != null) {
                        try {
                            onDismiss.run();
                        } catch (Throwable t) {
                            Log.e(TAG, "Error in onDismiss callback", t);
                        }
                    }
                }
            });
            dialog.show();
        } catch (Throwable t) {
            Log.e(TAG, "Error showing MorpheSettingsDialog", t);
        }
    }
}
