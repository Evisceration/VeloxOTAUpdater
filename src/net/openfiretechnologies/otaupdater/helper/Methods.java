package net.openfiretechnologies.otaupdater.helper;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by 0x337 on 16.09.13.
 */
public class Methods {

    /**
     * Logs a Message as Error, if DEBUG is true.
     * Means when you change DEBUG to false, it doesnt log any message anymore.
     * Very helpful for developing and releases, as you only have to set TRUE to FALSE
     * to remove all Message Logs.
     *
     * @param message The message to log
     */
    public static void LogDebug(String message) {
        if (Const.DEBUG) {
            Log.e(Const.TAG, message);
        }
    }

    public static void MakeToast(Context c, String msg, boolean longDuration) {
        Toast.makeText(c, msg, (longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)).show();
    }

}
