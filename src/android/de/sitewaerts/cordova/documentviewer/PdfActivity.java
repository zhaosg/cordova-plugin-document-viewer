package de.sitewaerts.cordova.documentviewer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import com.github.barteksc.pdfviewer.AssetUtil;
import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.shockwave.pdfium.PdfDocument;

import org.apache.cordova.LOG;

import java.util.List;

public class PdfActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener {

    private static final String TAG = PdfActivity.class.getSimpleName();
    private AssetUtil assetUtil;
    private final static int REQUEST_CODE = 42;
    public static final int PERMISSION_CODE = 42042;

    public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";

    PDFView pdfView;

    Uri uri;

    Integer pageNumber = 0;

    String pdfFileName;
    String pdfTitle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        assetUtil = AssetUtil.getInstance(super.getApplicationContext());
        requestWindowFeature(Window.FEATURE_NO_TITLE);//隐藏标题栏
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);//隐藏状态栏

        initActionBar();
        setStatusBarBackgroundColor("#2d69bd");
//        setStatusBarTransparent(true);
        setContentView(assetUtil.getResIdForLayout("activity_pdf"));
        initPdfView();
    }

    private TextView tvTitle;

    void initActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM); //Enable自定义的View
            actionBar.setCustomView(assetUtil.getResIdForLayout("document_actionbar"));//设置自定义的布局：actionbar_custom
            tvTitle = (TextView) actionBar.getCustomView().findViewById(assetUtil.getResId("action_bar_title"));

            ImageButton backButton = (ImageButton) actionBar.getCustomView().findViewById(assetUtil.getResId("backButton"));
            backButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            final ImageButton rightButton = (ImageButton) actionBar.getCustomView().findViewById(assetUtil.getResId("rightButton"));
            rightButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rightButton.setImageResource(assetUtil.getResIdForDrawable("star_fill"));
                    DocumentViewerPlugin.mainWebView.loadUrl("javascript:window.onLoveButtonClick()");
                }
            });

            Toolbar parent = (Toolbar) actionBar.getCustomView().getParent();
            parent.setContentInsetsAbsolute(0, 0);
        }
    }

    void initPdfView() {
        Bundle bundle = this.getIntent().getExtras();
        String url = bundle.getString("url");
        pdfTitle = bundle.getString("title");
        if (url.startsWith("file:///android_asset/"))
            url = url.replace("file:///android_asset/", "");
        if (url.startsWith("file:///"))
            uri = Uri.parse(url);
        pdfView = (PDFView) findViewById(assetUtil.getResId("pdfView"));
        pdfView.setBackgroundColor(Color.LTGRAY);
        if (uri != null) {
            displayFromUri(uri);
        } else {
            displayFromAsset(url);
        }
        setTitle(pdfFileName);
    }

    public void setTitle(CharSequence title) {
        tvTitle.setText(title);
        super.setTitle(title);
    }

    private void displayFromAsset(String assetFileName) {
        pdfFileName = assetFileName;

        pdfView.fromAsset(assetFileName)
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
//                .scrollHandle(new DefaultScrollHandle(this))
                .spacing(10) // in dp
                .load();
    }

    private void displayFromUri(Uri uri) {
        pdfFileName = getFileName(uri);

        pdfView.fromUri(uri)
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
//                .scrollHandle(new DefaultScrollHandle(this))
                .spacing(10) // in dp
                .load();
    }

    public void onResult(int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            uri = intent.getData();
            displayFromUri(uri);
        }
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        pageNumber = page;
        setTitle(pdfTitle == null || pdfTitle.trim().length() == 0 ? pdfFileName : pdfTitle);
//        setTitle(String.format("%s %s / %s", pdfTitle==null|| pdfTitle.trim().length()==0?pdfFileName:pdfTitle, page == 0 ? 1 : page, pageCount));
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    @Override
    public void loadComplete(int nbPages) {
        PdfDocument.Meta meta = pdfView.getDocumentMeta();
        Log.e(TAG, "title = " + meta.getTitle());
        Log.e(TAG, "author = " + meta.getAuthor());
        Log.e(TAG, "subject = " + meta.getSubject());
        Log.e(TAG, "keywords = " + meta.getKeywords());
        Log.e(TAG, "creator = " + meta.getCreator());
        Log.e(TAG, "producer = " + meta.getProducer());
        Log.e(TAG, "creationDate = " + meta.getCreationDate());
        Log.e(TAG, "modDate = " + meta.getModDate());

        printBookmarksTree(pdfView.getTableOfContents(), "-");

    }

    public void printBookmarksTree(List<PdfDocument.Bookmark> tree, String sep) {
        for (PdfDocument.Bookmark b : tree) {

            Log.e(TAG, String.format("%s %s, p %d", sep, b.getTitle(), b.getPageIdx()));

            if (b.hasChildren()) {
                printBookmarksTree(b.getChildren(), sep + "-");
            }
        }
    }

    /**
     * Listener for response to user permission request
     *
     * @param requestCode  Check that permission request code matches
     * @param permissions  Permissions that requested
     * @param grantResults Whether permissions granted
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //launchPicker();
            }
        }
    }

    private void setStatusBarBackgroundColor(final String colorPref) {
        if (Build.VERSION.SDK_INT >= 21) {
            if (colorPref != null && !colorPref.isEmpty()) {
                final Window window = this.getWindow();
                // Method and constants not available on all SDKs but we want to be able to compile this code with any SDK
                window.clearFlags(0x04000000); // SDK 19: WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.addFlags(0x80000000); // SDK 21: WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                try {
                    // Using reflection makes sure any 5.0+ device will work without having to compile with SDK level 21
                    window.getClass().getMethod("setStatusBarColor", int.class).invoke(window, Color.parseColor(colorPref));
                } catch (IllegalArgumentException ignore) {
                    LOG.e(TAG, "Invalid hexString argument, use f.i. '#999999'");
                } catch (Exception ignore) {
                    // this should not happen, only in case Android removes this method in a version > 21
                    LOG.w(TAG, "Method window.setStatusBarColor not found for SDK level " + Build.VERSION.SDK_INT);
                }
            }
        }
    }


    private void setStatusBarTransparent(final boolean transparent) {
        if (Build.VERSION.SDK_INT >= 21) {
            final Window window = this.getWindow();
            if (transparent) {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                window.setStatusBarColor(Color.TRANSPARENT);
            } else {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }
}
