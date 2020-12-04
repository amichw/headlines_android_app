package com.ami.headlines;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, View.OnClickListener, AdapterView.OnItemLongClickListener, View.OnLongClickListener {
    Button btn;
    URL url;
    ArrayAdapter aa;
    ListView lv;
    ArrayList<String> articles, links, headlines;
    TextView article;
    RelativeLayout RL_rootView;
    WebView webView;
    SplitView splitView;
    int whichFeed = -1, whichFeedPending = -1;
    final int DARK_THEME = 0, LIGHT_THEME = 1;
    final String SHARED_PREF = "shared_preference", SHARED_PREF_THEME = "theme", SHARED_PREF_HEADLINES = "headlines", SHARED_PREF_articles = "articles", SHARED_PREF_LINKS = "links", SHARED_PREF_FEED = "which feed";
    ToggleButton toggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences preferences = null;
        if ((preferences = getSharedPreferences(SHARED_PREF, MODE_PRIVATE)) != null)
            setTheme(preferences.getInt(SHARED_PREF_THEME, 0) == 0 ? R.style.AppTheme : R.style.LightTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        ListView lv=(ListView)findViewById(R.id.listview_headlines);
//        aa= new ArrayAdapter(MainActivity.this, android.R.layout.simple_list_item_1);
//        lv.setAdapter(aa);
        lv = (ListView) findViewById(R.id.listview_headlines);
        lv.setOnItemClickListener(this);
        lv.setOnItemLongClickListener(this);
        article = (TextView) findViewById(R.id.tv_article);
        article.setVisibility(View.INVISIBLE);
        article.setOnClickListener(this);
        webView = (WebView) findViewById(R.id.webView);
        webView.setVisibility(View.INVISIBLE);
        webView.setOnLongClickListener(this);
        RL_rootView = (RelativeLayout) findViewById(R.id.rl_root_view);
        RL_rootView.setOnClickListener(this);
        splitView = ((SplitView) findViewById(R.id.split_view));
        splitView.setOnClickListener(this);
        splitView.maximizePrimaryContent();


        btn = (Button) findViewById(R.id.button_download);
        btn.setOnClickListener(this);
        toggleButton = (ToggleButton) findViewById(R.id.btn_night_mode);
        toggleButton.setChecked(true);
        toggleButton.setOnClickListener(this);

        loadDataFromPref();  // for day/night purposes.  and feed


        Animation alphaAnimation = new AlphaAnimation(0.0f, 1.0f);
        alphaAnimation.setDuration(150);
        Animation translateAnimation = new TranslateAnimation(800, 0, 300, 0);
        translateAnimation.setDuration(300);
        translateAnimation.setInterpolator(this, android.R.anim.accelerate_decelerate_interpolator);
        AnimationSet set = new AnimationSet(true);
        set.addAnimation(alphaAnimation);
        set.addAnimation(translateAnimation);
        LayoutAnimationController controller =
                new LayoutAnimationController(set, 0.2f);
        lv.setLayoutAnimation(controller);
        //download first time immediately upon opening app.
        //  btn.performClick();
        try {
            whichFeedPending = whichFeed;
//            Log.d("start", "dowload "+ whichFeed+"   "+getResources().getStringArray(R.array.feed_http)[whichFeed]);
            new BackThread().execute(new URL(getResources().getStringArray(R.array.feed_http)[whichFeed]));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onClick(View v) {
        if (v == RL_rootView) {
            article.setText(R.string.article_place_holder);
            article.setVisibility(View.INVISIBLE);
            //webView.setVisibility(View.INVISIBLE);
            splitView.maximizePrimaryContent();
        }

        if (v == article) {
            article.setVisibility(View.INVISIBLE);
        }

        if (v == btn) {

            new AlertDialog.Builder(this)
                    .setTitle("Pick feed:")
                    .setItems(R.array.feeds, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            String sUrl;
                            sUrl = getResources().getStringArray(R.array.feed_http)[which];

                            try {
                                url = new URL(sUrl);
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            }
                            // Crossword:
                            if (which==1){
                                loadWebView(sUrl);
                                return;
                            }

                            whichFeedPending = which;
                            new BackThread().execute(url);
                            btn.setEnabled(false);
                        }
                    })
                    .show();
        }
        if (v == toggleButton) {
            SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREF, MODE_PRIVATE).edit();
            ToggleButton tb = (ToggleButton) v;
            if (tb.isChecked())
                editor.putInt(SHARED_PREF_THEME, DARK_THEME).apply();
            else editor.putInt(SHARED_PREF_THEME, LIGHT_THEME).apply();
            if (articles != null && links != null) {
                editor.putInt(SHARED_PREF_FEED, whichFeed);
                try {
                    editor.putString(SHARED_PREF_HEADLINES, ObjectSerializer.serialize(headlines))
                            .putString(SHARED_PREF_articles, ObjectSerializer.serialize(articles))
                            .putString(SHARED_PREF_LINKS, ObjectSerializer.serialize(links)).apply();
                } catch (IOException e) {
                    e.printStackTrace();
                }


                recreate();
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (articles.isEmpty()) {
            Toast.makeText(this, "This feed does not support more information.", Toast.LENGTH_SHORT).show();
            return;
        }
        article.setVisibility(View.VISIBLE);
        article.setText(articles.get(position));
    }


    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (links.isEmpty()) return true;
        String urlString = links.get(position);

//        if(whichFeed==5) {  Toast.makeText(this,"This feed does not support full article.", Toast.LENGTH_SHORT ).show();
//            return true;    }

        // for ynet  open browser:
        if (whichFeed == 2) {
            startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(urlString)));
            return true;
        }
        loadWebView(urlString);
        return true;
    }

    @Override
    public boolean onLongClick(View v) {
        if (v == webView) {
            String urlString = webView.getUrl();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(urlString));
            startActivity(intent);
        }
        return true;
    }


    class BackThread extends AsyncTask<URL, String, Integer> {

        int timing;


        @Override
        protected Integer doInBackground(URL... params) {
            Long startMillis = System.currentTimeMillis();
            int linesPrinted = 0;
            URL pageUrl = params[0];
            try {
                Log.d(this.getClass().getName(), "attempting to download: " + pageUrl);
                InputStream is = pageUrl.openStream();
                XmlPullParserFactory xppf = XmlPullParserFactory.newInstance();
                XmlPullParser parser = xppf.newPullParser();
                parser.setInput(is, null);
                parser.setFeature(Xml.FEATURE_RELAXED, true);
                int eventType = parser.getEventType();
                boolean title = false;
                boolean item = false;
                boolean description = false;
                boolean link = false;
                headlines = new ArrayList<>();
                articles = new ArrayList<>();
                links = new ArrayList<>();

                Log.d(this.getClass().getName(), "Downloaded: " + pageUrl);

                while (eventType != XmlPullParser.END_DOCUMENT && System.currentTimeMillis() - startMillis <5000 ) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if (parser.getName().equals("title"))
                            title = true;
                        if (parser.getName().equals("item"))
                            item = true;
                        if (parser.getName().equals("description"))
                            description = true;
                        if (parser.getName().equals("link"))
                            link = true;
                    }
                    if (eventType == XmlPullParser.END_TAG) {
                        if (parser.getName().equals("title"))
                            title = false;
                        if (parser.getName().equals("item"))
                            item = false;
                        if (parser.getName().equals("description"))
                            description = false;
                        if (parser.getName().equals("link"))
                            link = false;
                    }
                    if ((title && item) && eventType == XmlPullParser.TEXT) {
                        headlines.add(" " + parser.getText());
                        articles.add("Long press for full article."); // placeholder for no-subtitle
                        linesPrinted++;
                    }
                    if ((description && item)) {
                        eventType = parser.next();
                        Log.d(this.getClass().getName(), "eventType: " + eventType);
                        if (eventType == XmlPullParser.TEXT) {
                            String line = parser.getText();
                            Log.d(this.getClass().getName(), "line: " + line);
                            if (line.contains("<")) line = line.substring(0, line.indexOf('<'));
                            if (line.contains(">")) line = line.substring(0, line.indexOf('>'));
                            articles.set(articles.size()-1, " " + line); // already added placeholder
                        } else {
//                            articles.add("Long press for full article.");
                            description = false;
                        }
                    }
                    if ((link && item) && eventType == XmlPullParser.TEXT) {

                        links.add(parser.getText());
                        Log.d(this.getClass().getName(), "links: " + parser.getText());
                    }
//                    Log.d(this.getClass().getName(), "before next: " + parser.getPositionDescription());
                    try {
                        eventType = parser.next();
                    }catch (XmlPullParserException e) {
                        e.printStackTrace();
                    }
                }
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }


            timing = (int) (System.currentTimeMillis()-startMillis) / 1000;
            return linesPrinted;
        }

        @Override
        protected void onPreExecute() {
//            Toast.makeText(MainActivity.this, "starting to parse", Toast.LENGTH_SHORT).show();
            super.onPreExecute();

        }

        @Override
        protected void onPostExecute(Integer integer) {
            aa = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, headlines);
            lv.setAdapter(aa);
            //    Toast.makeText(MainActivity.this, "finished parsing. parsed "+integer+" Headlines\n took"+timing+" seconds. ", Toast.LENGTH_SHORT).show();
            btn.setEnabled(true);
            lv.startLayoutAnimation();
            splitView.maximizePrimaryContent();
            article.setVisibility(View.INVISIBLE);
            whichFeed = whichFeedPending;
            super.onPostExecute(integer);
        }

    }

    @Override
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE  /* 'maximizePrimaryContent' gives it a value of 1*/
                && webView.getHeight() != 1) {
            if (webView.canGoBack())
                webView.goBack();
            else {
                //   webView.setVisibility(View.GONE);
                splitView.maximizePrimaryContent();
            }
        } else
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Bye-bye")
                    .setMessage("Are you sure you want to exit?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton("No", null)
                    .show();
    }

    @Override
    protected void onPause() {
        getSharedPreferences(SHARED_PREF, MODE_PRIVATE).edit().clear()
                .putInt(SHARED_PREF_FEED, whichFeed)
                .putInt(SHARED_PREF_THEME, toggleButton.isChecked() ? DARK_THEME : LIGHT_THEME).apply();
        super.onPause();
    }

    void loadDataFromPref() {
        headlines = new ArrayList<>();
        articles = new ArrayList<>();
        links = new ArrayList<>();
        SharedPreferences preferences = getSharedPreferences(SHARED_PREF, MODE_PRIVATE);
        whichFeed = preferences.getInt(SHARED_PREF_FEED, 0);
        if (preferences.getString(SHARED_PREF_HEADLINES, null) == null) return;
//        headlines.addAll((LinkedHashSet)preferences.getStringSet("headlines", null));
//        articles.addAll((LinkedHashSet)preferences.getStringSet("articles", null));
//        links.addAll((LinkedHashSet)preferences.getStringSet("links", null));
        try {
            headlines = (ArrayList<String>) ObjectSerializer.deserialize(preferences.getString(SHARED_PREF_HEADLINES, null));
            articles = (ArrayList<String>) ObjectSerializer.deserialize(preferences.getString(SHARED_PREF_articles, null));
            links = (ArrayList<String>) ObjectSerializer.deserialize(preferences.getString(SHARED_PREF_LINKS, null));
        } catch (IOException e) {
            e.printStackTrace();
        }
        aa = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, headlines);
        lv.setAdapter(aa);
    }

    void loadWebView(String urlString){
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                    webView.zoomBy(0.02f);
//                }
                if (whichFeed == 3) {
                    webView.scrollBy(3000, 1500);
                    webView.zoomOut();
                    webView.zoomOut();
//                    webView.getSettings().setBuiltInZoomControls(true);
//                    webView.getSettings().setDisplayZoomControls(false);
                }
                webView.setVisibility(View.VISIBLE);
                webView.getSettings().setBuiltInZoomControls(true);
                webView.getSettings().setDisplayZoomControls(false);
                splitView.maximizeSecondaryContent();
                splitView.setPrimaryContentSize(500);
                article.setVisibility(View.INVISIBLE);
                super.onPageFinished(view, url);
            }
        });
        webView.loadUrl(urlString);
        // webView.setVisibility(View.VISIBLE);
    }
}
