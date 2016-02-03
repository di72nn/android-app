package fr.gaulupeau.apps.Poche.network.tasks;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.entity.OfflineURL;
import fr.gaulupeau.apps.Poche.entity.OfflineURLDao;
import fr.gaulupeau.apps.Poche.ui.DialogHelperActivity;

public class UploadOfflineURLsTask extends AsyncTask<Void, Integer, Boolean> {

    private String errorMessage;
    private Context context;
    private ProgressDialog progressDialog;

    private int totalUploaded, totalCount;
    private List<OfflineURL> failedURLs = new ArrayList<>();

    public UploadOfflineURLsTask(Context context, ProgressDialog progressDialog) {
        this.context = context;
        this.progressDialog = progressDialog;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        Settings settings = App.getInstance().getSettings();
        WallabagService service = new WallabagService(
                settings.getUrl(),
                settings.getKey(Settings.USERNAME),
                settings.getKey(Settings.PASSWORD));

        OfflineURLDao offlineURLDao = DbConnection.getSession().getOfflineURLDao();
        List<OfflineURL> urls = offlineURLDao.queryBuilder()
                .orderAsc(OfflineURLDao.Properties.Id).build().list();

        if(urls.isEmpty()) {
            return true;
        }

        List<OfflineURL> uploaded = new ArrayList<>(urls.size());

        int counter = 0;
        int size = urls.size();

        publishProgress(counter, size);

        // add multithreading?

        for(OfflineURL url: urls) {
            if(isCancelled()) break;

            boolean success = false;
            try {
                if(service.addLink(url.getUrl())) {
                    success = true;
                }
            } catch(IOException e) {
                errorMessage = e.getMessage();
                e.printStackTrace();
            }

            if(success) uploaded.add(url);
            else failedURLs.add(url);

            publishProgress(++counter, size);
        }

        if(!uploaded.isEmpty()) {
            for(OfflineURL url: uploaded) {
                offlineURLDao.delete(url);
            }
        }

        totalUploaded = uploaded.size();
        totalCount = size;

        return totalUploaded == totalCount;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        if(progressDialog != null) {
            int current = progress[0];
            if(current == 0) {
                int max = progress[1];
                if(progressDialog.getMax() != max) progressDialog.setMax(max);
            }

            progressDialog.setProgress(current);
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (success) {
            if(context != null) {
                if(totalCount == 0) {
                    Toast.makeText(context, R.string.uploadURLs_nothingToUpload, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, R.string.uploadURLs_finished, Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            if(context != null) {
                if(errorMessage == null) {
                    errorMessage = context.getString(R.string.couldntUploadURL_errorMessage);
                }

                DialogHelperActivity.showAlertDialog(context,
                        context.getString(R.string.d_uploadURLs_title), errorMessage,
                        context.getString(R.string.ok));

                Toast.makeText(context, String.format(
                                context.getString(R.string.uploadURLs_result_text),
                                totalUploaded, totalCount),
                        Toast.LENGTH_SHORT).show();

                if(copyURLsToClipboard(context, failedURLs)) {
                    Toast.makeText(context, R.string.uploadURLs_copied_to_clipboard_message,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }

        if(progressDialog != null) progressDialog.dismiss();
    }

    private boolean copyURLsToClipboard(Context context, List<OfflineURL> urls) {
        if(urls.isEmpty()) return false;

        StringBuilder sb = new StringBuilder();
        for(OfflineURL url: urls) {
            sb.append(url.getUrl()).append('\n');
        }

        return copyToClipboard(context, context.getString(R.string.uploadURLs_clipboard_title),
                sb.toString());
    }

    // http://stackoverflow.com/a/19253877
    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    private boolean copyToClipboard(Context context, String label, String text) {
        try {
            int sdk = android.os.Build.VERSION.SDK_INT;
            if (sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) context
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setText(text);
            } else {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
                clipboard.setPrimaryClip(clip);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
