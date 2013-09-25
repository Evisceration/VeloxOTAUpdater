package net.openfiretechnologies.otaupdater;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import net.openfiretechnologies.otaupdater.helper.Const;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static net.openfiretechnologies.otaupdater.helper.Methods.LogDebug;
import static net.openfiretechnologies.otaupdater.helper.Methods.hasStorage;
import static net.openfiretechnologies.otaupdater.helper.Methods.getListFiles;

public class MainActivity extends Activity {
    //========================================
    public static LinearLayout mOverlay;
    public static boolean isDebug = false;
    //========================================
    public ProgressDialog mProgressDialog;
    //========================================
    private DownloadManager mDownloadManager;
    private SharedPreferences mPrefs;
    private List<String> names, urls, tmpNames, tmpUrls;
    private ListView lv;
    private ArrayAdapter<String> adapter;
    private WindowManager wm;
    private DownloadManager.Request mRequest;
    private long enqueue;
    private String fullPath = "", filename = "", foldername = "";
    private BroadcastReceiver receiver;
    //========================================

    private void getPrefEntries() {
        isDebug = mPrefs.getBoolean("prefs_debug_log", false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPrefEntries();
        registerReceiver(receiver, new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Filepath
        foldername = Environment.getExternalStorageDirectory() + File.separator + "0_velox" + File.separator;

        //Get the DownloadManager
        mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        //Get the WindowManager
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        //Get the Preferences
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        getPrefEntries();

        //Get the listview
        lv = (ListView) findViewById(R.id.lvUpdates);

        //prepare lists
        names = new ArrayList<String>();
        urls = new ArrayList<String>();

        //get already downloaded files
        names = getListFiles(new File(foldername));
        names.add(0, "LOCAL");
        while (urls.size() < names.size())
            urls.add(0, "LOCAL");

        //Setup Array adapter for the listview
        adapter = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_list_item_1, names);

        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {

                if (mOverlay != null) {
                    if (mOverlay.isShown()) {
                        wm.removeView(mOverlay);
                        return;
                    }
                }

		if(!hasStorage(true))
			return;

                //=====================================
                //=====================================
                final String name = (String) adapterView.getItemAtPosition(position);
                filename = name + ".zip";
                final String url = urls.get(position);
                fullPath = foldername + filename;
                //=====================================
                //=====================================
                if (name.equals("LOCAL") || name.equals("UPDATES")) {
                    return;
                }

                if (new File(fullPath).exists()) {
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                    alertDialog.setTitle("Update?").setMessage("File already downloaded!\n\nDo you want to install:\n" + fullPath + "\n?");
                    alertDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            installAndReboot();
                        }
                    });
                    alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    });

                    alertDialog.show();
                } else {
                    try {
                        //Create Overlay
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                350,
                                500,
                                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                                PixelFormat.TRANSLUCENT);
                        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                        mOverlay = (LinearLayout) inflater.inflate(R.layout.overlay_webview, null);
                        wm.addView(mOverlay, params);
                        //=====================================
                        //=====================================
                        final WebView wv = (WebView) mOverlay.findViewById(R.id.webView);
                        wv.getSettings().setJavaScriptEnabled(true);
                        wv.getSettings().setUseWideViewPort(true);
                        wv.getSettings().setLoadWithOverviewMode(true);
                        wv.getSettings().setLoadsImagesAutomatically(false);
                        wv.setInitialScale(50);
                        wv.getSettings().setBuiltInZoomControls(true);
                        //=====================================
                        //=====================================
                        wv.setWebViewClient(new WebViewClient() {
                            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                if (url.contains("zip")) {
                                    new File(foldername).mkdirs();
                                    //=====================================
                                    //=====================================
                                    mRequest = new DownloadManager.Request(Uri.parse(url));
                                    mRequest.setMimeType("application/zip");
                                    mRequest.setTitle(name);
                                    mRequest.setDestinationInExternalPublicDir(foldername, filename);
                                    //=====================================
                                    enqueue = mDownloadManager.enqueue(mRequest);
                                    fullPath = foldername + filename;
                                    wm.removeView(mOverlay);
                                }
                                return true;
                            }
                        });
                        //=====================================
                        //=====================================
                        wv.loadUrl(url);
                    } catch (Exception exc) {
                        LogDebug("Error: " + exc.getMessage());
                    }
                }

            }
        });
        (findViewById(R.id.RelativeLayoutMain)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (mOverlay != null) {
                    if (mOverlay.isShown())
                        wm.removeView(mOverlay);
                }
                return false;
            }
        });

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    long downloadId = intent.getLongExtra(
                            DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(enqueue);
                    Cursor c = mDownloadManager.query(query);
                    if (c.moveToFirst()) {
                        int columnIndex = c
                                .getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c
                                .getInt(columnIndex)) {
                            if (new File(fullPath).exists()) {
                                AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                                alertDialog.setTitle("Update?").setMessage("Do you want to install:\n" + fullPath + "\n?");
                                alertDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        installAndReboot();
                                    }
                                });
                                alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                });

                                alertDialog.show();
                            }
                        }
                    }
                }
            }
        };

        registerReceiver(receiver, new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));


    }

    private void installAndReboot() {
        try {
            Process p = Runtime.getRuntime().exec("sh");
            OutputStream os = p.getOutputStream();
            os.write("mkdir -p /cache/recovery/\n".getBytes());
            os.write("echo 'boot-recovery' >/cache/recovery/command\n".getBytes());

            String cmd = "echo '--update_package=" + "/sdcard/0_velox/" + filename
                    + "' >> /cache/recovery/command\n";
            os.write(cmd.getBytes());
            os.flush();

            // Trigger the reboot
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            powerManager.reboot("recovery");
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        if (mOverlay != null) {
            if (mOverlay.isShown())
                wm.removeView(mOverlay);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_update:
                new DownloadChecker(this).execute();
                break;
            case R.id.action_settings:
                startActivity(new Intent(MainActivity.this, Preferences.class));
                break;
        }
        return false;
    }

    private void createProgressDialog(String title, String msg) {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
        }
        mProgressDialog.setTitle(title);
        mProgressDialog.setMessage(msg);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    public void setUpdateResult(HashMap<String, List<String>> map) {
        try {
            tmpNames = map.get("names");
            tmpUrls = map.get("urls");
            names = new ArrayList<String>();
            urls = new ArrayList<String>();

            names = getListFiles(new File(foldername));
            names.add(0, "LOCAL");
            while (urls.size() < names.size())
                urls.add(0, "LOCAL");

            LogDebug("nameslength: " + tmpNames.size());
            LogDebug("urlslength: " + tmpUrls.size());

            new Runnable() {
                @Override
                public void run() {
                    adapter.clear();

                    names.add("UPDATES");
                    urls.add("UPDATES");

                    for (String name : tmpNames) {
                        if (mPrefs.getBoolean("prefs_update_beta", false)) {
                            int index = tmpNames.indexOf(name);
                            names.add(tmpNames.get(index));
                            urls.add(tmpUrls.get(index));
                        } else {
                            if (!(name.toUpperCase().contains("BETA"))) {
                                int index = tmpNames.indexOf(name);
                                names.add(tmpNames.get(index));
                                urls.add(tmpUrls.get(index));
                            }
                        }
                    }

                    adapter.addAll(names);
                    adapter.notifyDataSetChanged();
                }
            }.run();

        } catch (Exception exc) {
            LogDebug("Error: " + exc.getMessage());
        }
    }

    private class DownloadChecker extends AsyncTask<String, String, HashMap<String, List<String>>> {

        final MainActivity mainActivity;

        public DownloadChecker(MainActivity mainActivity) {
            this.mainActivity = mainActivity;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (!values[0].isEmpty()) {
                final String msg = values[0];
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgressDialog.setMessage(msg);
                    }
                });
            }
        }

        @Override
        protected HashMap<String, List<String>> doInBackground(String... strings) {
            //Prepare HashMap, which stores our Update Addresses as NAME, URL
            HashMap<String, List<String>> map = new HashMap<String, List<String>>();

            onProgressUpdate("Setting up HttpClient.");

            // Create HttpClient, HttpContext and HttpGet to send our request
            HttpClient httpClient = new DefaultHttpClient();
            HttpContext httpContext = new BasicHttpContext();
            HttpGet httpGet = new HttpGet(Const.URL);

            try {
                onProgressUpdate("Connecting..");
                //Getting the response and put it into a String
                HttpResponse response = httpClient.execute(httpGet,
                        httpContext);
                String responseString = EntityUtils.toString(response.getEntity());
                LogDebug("Response: " + responseString);
                onProgressUpdate("Handling the response...");
                //Prepare the String for adding it into the Hashmap
                String[] splitted = responseString.split("\\|\\|");
                //Lists to store entries
                List<String> names = new ArrayList<String>();
                List<String> urls = new ArrayList<String>();
                int i = 0;

                onProgressUpdate("Adding names to list.");
                //Add entries for names
                for (String aSplitted : splitted) {
                    names.add(aSplitted.split("\\|")[0]);
                    i++;
                }

                //Reset counter
                i = 0;

                onProgressUpdate("Adding urls to list..");
                //Add entries for urls
                for (String aSplitted : splitted) {
                    urls.add(aSplitted.split("\\|")[1]);
                    i++;
                }

                onProgressUpdate("Checking lists...");
                //Check if length of names is equal to urls
                if (names.size() == urls.size()) {
                    map.put("names", names);
                    map.put("urls", urls);
                } else {
                    LogDebug("Error, length doesnt match!");
                }
            } catch (Exception exc) {
                LogDebug("Error: " + exc.getMessage());
            }

            onProgressUpdate("Done!");
            return map;
        }

        @Override
        protected void onPreExecute() {
            mainActivity.getPrefEntries();
            mainActivity.createProgressDialog("Checking for Updates...", "");
        }

        @Override
        protected void onPostExecute(HashMap<String, List<String>> map) {
            mainActivity.mProgressDialog.dismiss();
            mainActivity.setUpdateResult(map);
        }
    }

}
