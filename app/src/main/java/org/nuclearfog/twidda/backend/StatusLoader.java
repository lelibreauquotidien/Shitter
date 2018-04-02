package org.nuclearfog.twidda.backend;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.List;

import twitter4j.TwitterException;

import org.nuclearfog.twidda.database.DatabaseAdapter;
import org.nuclearfog.twidda.R;
import org.nuclearfog.twidda.viewadapter.TimelineRecycler;
import org.nuclearfog.twidda.window.SearchPage;
import org.nuclearfog.twidda.window.TweetDetail;
import org.nuclearfog.twidda.backend.listitems.*;
import org.nuclearfog.twidda.window.TweetPopup;
import org.nuclearfog.twidda.window.UserProfile;

public class StatusLoader extends AsyncTask<Long, Void, Long> implements View.OnClickListener {

    private static final long ERROR = -1;
    public static final long RETWEET = 0;
    public static final long FAVORITE = 1;
    public static final long DELETE = 2;
    public static final long LOAD_TWEET = 3;
    public static final long LOAD_REPLY = 4;
    public static final long LOAD_DB = 5;
    private static final long IGNORE = 6;

    private TwitterEngine mTwitter;
    private TimelineRecycler tlAdp;
    private RecyclerView replyList;
    private String usernameStr, scrNameStr, tweetStr, dateString;
    private String repliedUsername, apiName, retweeter;
    private String medialinks[], profile_pb;
    private String errMSG = "";
    private boolean retweeted, favorited, toggleImg, verified;
    private boolean rtFlag = false;
    private long tweetReplyID,tweetID, userID, retweeterID;
    private int rt, fav;
    private int highlight, font;

    private WeakReference<TweetDetail> ui;

    public StatusLoader(Context c) {
        mTwitter = TwitterEngine.getInstance(c);
        SharedPreferences settings = c.getSharedPreferences("settings", 0);
        font = settings.getInt("font_color", 0xffffffff);
        highlight = settings.getInt("highlight_color", 0xffff00ff);
        toggleImg = settings.getBoolean("image_load",true);
        ui = new WeakReference<>((TweetDetail)c);
        replyList = (RecyclerView) ui.get().findViewById(R.id.answer_list);
    }


    /**
     * @param data [0] TWEET ID , [1] Mode
     */
    @Override
    protected Long doInBackground(Long... data) {
        tweetID = data[0];
        final long MODE = data[1];
        Tweet tweet;
        try {
            if(MODE == LOAD_DB) {
                tweet = new DatabaseAdapter(ui.get()).getStatus(tweetID);
                if(tweet == null)
                    return IGNORE;
            }
            else {
                tweet = mTwitter.getStatus(tweetID);
                if(MODE == LOAD_TWEET) {
                    new DatabaseAdapter(ui.get()).storeStatus(tweet);
                }
            }
            if(tweet.embedded != null) {
                retweeter = tweet.user.screenname;
                retweeterID = tweet.user.userID;
                tweet = tweet.embedded;
                tweetID = tweet.tweetID;
                rtFlag = true;
            }
            rt = tweet.retweet;
            fav = tweet.favorit;
            retweeted = tweet.retweeted;
            favorited = tweet.favorized;

            if(MODE == LOAD_TWEET || MODE == LOAD_DB) {
                tweetReplyID = tweet.replyID;
                verified = tweet.user.isVerified;
                tweetStr = tweet.tweet;
                usernameStr = tweet.user.username;
                userID = tweet.user.userID;
                scrNameStr = tweet.user.screenname;
                apiName = formatString(tweet.source);
                dateString = DateFormat.getDateTimeInstance().format(tweet.time);
                repliedUsername = tweet.replyName;
                profile_pb = tweet.user.profileImg+"_bigger";
                medialinks = tweet.media;
            }
            else if(MODE == RETWEET) {
                if(retweeted) {
                    mTwitter.retweet(tweetID, true);
                    new DatabaseAdapter(ui.get()).removeStatus(tweetID);
                    retweeted = false;
                    rt--;
                } else {
                    mTwitter.retweet(tweetID, false);
                    retweeted = true;
                    rt++;
                }
            }
            else if(MODE == FAVORITE) {
                if(favorited) {
                    mTwitter.favorite(tweetID, true);
                    favorited = false;
                    fav--;
                } else {
                    mTwitter.favorite(tweetID, false);
                    favorited = true;
                    fav++;
                }
            }
            else if(MODE == LOAD_REPLY) {
                List<Tweet> answers;
                String replyname = tweet.user.screenname;
                tlAdp = (TimelineRecycler) replyList.getAdapter();
                if(tlAdp != null && tlAdp.getItemCount() > 0) {
                    long sinceId = tlAdp.getItemId(0);
                    answers = mTwitter.getAnswers(replyname, tweetID, sinceId);
                    answers.addAll(tlAdp.getData());
                } else {
                    answers = mTwitter.getAnswers(replyname, tweetID, tweetID);
                }
                tlAdp = new TimelineRecycler(answers,ui.get());
                tlAdp.setColor(highlight, font);
            }
            else if(MODE == DELETE) {
                mTwitter.deleteTweet(tweetID);
                new DatabaseAdapter(ui.get()).removeStatus(tweetID);
            }
        }catch(TwitterException e) {
            int err = e.getErrorCode();
            if(err == 144) { // gelöscht
                new DatabaseAdapter(ui.get()).removeStatus(tweetID);
            }
            errMSG = e.getMessage();
            return ERROR;
        } catch(Exception err) {
            errMSG = err.getMessage();
            return ERROR;
        }
        return MODE;
    }

    @Override
    protected void onPostExecute(Long mode) {
        TweetDetail connect = ui.get();
        if(connect == null)
            return;

        if(mode == LOAD_TWEET ||mode == LOAD_DB) {
            TextView tweet = (TextView)connect.findViewById(R.id.tweet_detailed);
            TextView username = (TextView)connect.findViewById(R.id.usernamedetail);
            TextView scrName = (TextView)connect.findViewById(R.id.scrnamedetail);
            TextView date = (TextView)connect.findViewById(R.id.timedetail);
            TextView replyName = (TextView)connect.findViewById(R.id.answer_reference_detail);
            TextView used_api = (TextView)connect.findViewById(R.id.used_api);
            TextView txtRet = (TextView)connect.findViewById(R.id.no_rt_detail);
            TextView txtFav = (TextView)connect.findViewById(R.id.no_fav_detail);
            ImageView profile_img = (ImageView)connect.findViewById(R.id.profileimage_detail);

            tweet.setMovementMethod(LinkMovementMethod.getInstance());
            tweet.setText(highlight(tweetStr));
            username.setText(usernameStr);
            scrName.setText(scrNameStr);
            date.setText(dateString);
            used_api.setText(apiName);
            String favStr = Integer.toString(fav);
            String rtStr = Integer.toString(rt);
            txtFav.setText(favStr);
            txtRet.setText(rtStr);

            if(repliedUsername != null) {
                String reply = "antwort @"+repliedUsername;
                replyName.setText(reply);
                replyName.setVisibility(View.VISIBLE);
                replyName.setOnClickListener(this);
                replyName.setVisibility(View.VISIBLE);
            }
            if(rtFlag) {
                String retPrompt = "Retweet "+retweeter;
                TextView userRetweet = (TextView)connect.findViewById(R.id.rt_info);
                userRetweet.setText(retPrompt);
                userRetweet.setOnClickListener(this);
                userRetweet.setVisibility(View.VISIBLE);
            }
            if(verified) {
                ImageView tweet_verify =(ImageView)connect.findViewById(R.id.tweet_verify);
                tweet_verify.setVisibility(View.VISIBLE);
            }
            if(toggleImg) {
                Picasso.with(ui.get()).load(profile_pb).into(profile_img);
                if(medialinks != null && medialinks.length != 0) {
                    Button mediabutton = (Button)connect.findViewById(R.id.image_attach);
                    mediabutton.setVisibility(View.VISIBLE);
                    mediabutton.setOnClickListener(this);
                }
            }
            Button retweetButton = (Button)connect.findViewById(R.id.rt_button_detail);
            Button favoriteButton = (Button)connect.findViewById(R.id.fav_button_detail);
            Button answer = (Button) connect.findViewById(R.id.answer_button);
            setIcons(favoriteButton, retweetButton);
            profile_img.setOnClickListener(this);
            answer.setOnClickListener(this);
        }
        else if(mode == RETWEET) {
            String rtStr = Integer.toString(rt);
            TextView txtRet = (TextView)connect.findViewById(R.id.no_rt_detail);
            Button retweetButton = (Button)connect.findViewById(R.id.rt_button_detail);
            Button favoriteButton = (Button)connect.findViewById(R.id.fav_button_detail);
            txtRet.setText(rtStr);
            setIcons(favoriteButton, retweetButton);
        }
        else if(mode == FAVORITE) {
            String favStr = Integer.toString(fav);
            TextView txtFav = (TextView)connect.findViewById(R.id.no_fav_detail);
            Button retweetButton = (Button)connect.findViewById(R.id.rt_button_detail);
            Button favoriteButton = (Button)connect.findViewById(R.id.fav_button_detail);
            txtFav.setText(favStr);
            setIcons(favoriteButton, retweetButton);
        }
        else if(mode == LOAD_REPLY) {
            replyList.setAdapter(tlAdp);
            SwipeRefreshLayout ansReload = (SwipeRefreshLayout)connect.findViewById(R.id.answer_reload);
            ansReload.setRefreshing(false);
            String ansStr = Integer.toString(tlAdp.getItemCount());
            TextView txtAns = (TextView)connect.findViewById(R.id.no_ans_detail);
            txtAns.setText(ansStr);
        }
        else if(mode == DELETE) {
            Toast.makeText(ui.get(), "Tweet gelöscht", Toast.LENGTH_LONG).show();
            ui.get().finish();
        }
        else if(mode == ERROR) {
            Toast.makeText(ui.get(), "Fehler beim Laden: "+errMSG, Toast.LENGTH_LONG).show();
            SwipeRefreshLayout ansReload = (SwipeRefreshLayout)connect.findViewById(R.id.answer_reload);
            if(ansReload.isRefreshing()) {
                ansReload.setRefreshing(false);
            }
        }
    }


    private String formatString(String input) {
        StringBuilder output = new StringBuilder("gesendet von: ");
        boolean openTag = false;
        for(int i = 0 ; i < input.length() ; i++){
            char current = input.charAt(i);
            if(current == '>' && !openTag){
                openTag = true;
            } else if(current == '<'){
                openTag = false;
            } else if(openTag) {
                output.append(current);
            }
        }
        return output.toString();
    }


    private Spannable highlight(String tweet) {
        Spannable sTweet = new SpannableStringBuilder(tweet);
        int start = 0;
        boolean marked = false;
        for(int i = 0 ; i < tweet.length() ; i++) {
            char current = tweet.charAt(i);
            switch(current){
                case '@':
                case '#':
                    start = i;
                    marked = true;
                    break;
                case '\'':
                case '\"':
                case '\n':
                case ')':
                case '(':
                case ':':
                case ' ':
                case '.':
                case ',':
                case '!':
                case '?':
                case '-':
                    if(marked && start != i-1) {
                        sTweet = spanning(sTweet, start, i);
                    }
                    marked = false;
                    break;
            }
        }
        if(marked && start != tweet.length()-1) {
            sTweet = spanning(sTweet, start, tweet.length());
        }
        return sTweet;
    }


    private Spannable spanning(Spannable sTweet, final int start, final int end) {
        sTweet.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                TextView tv = (TextView) widget;
                Spanned s = (Spanned) tv.getText();
                String search = s.subSequence(start, end).toString();
                Intent intent = new Intent(ui.get(), SearchPage.class);
                Bundle bundle = new Bundle();
                if(search.startsWith("#"))
                    bundle.putString("Addition", search);
                bundle.putString("search", search);
                intent.putExtras(bundle);
                ui.get().startActivity(intent);
            }
            @Override
            public void updateDrawState(TextPaint ds){
                ds.setColor(highlight);
                ds.setUnderlineText(false);
            }
        },start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sTweet;
    }


    private void setIcons(Button favoriteButton, Button retweetButton) {
        if(favorited)
            favoriteButton.setBackgroundResource(R.drawable.favorite_enabled);
        else
            favoriteButton.setBackgroundResource(R.drawable.favorite);
        if(retweeted)
            retweetButton.setBackgroundResource(R.drawable.retweet_enabled);
        else
            retweetButton.setBackgroundResource(R.drawable.retweet);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.profileimage_detail:
                Intent profile = new Intent(ui.get(), UserProfile.class);
                Bundle b = new Bundle();
                b.putLong("userID",userID);
                b.putString("username", scrNameStr);
                profile.putExtras(b);
                ui.get().startActivity(profile);
                break;

            case R.id.answer_reference_detail:
                Intent tweet = new Intent(ui.get(), TweetDetail.class);
                tweet.putExtra("tweetID",tweetReplyID);
                tweet.putExtra("username", '@'+repliedUsername);
                ui.get().startActivity(tweet);
                break;

            case R.id.answer_button:
                Intent tweetpop = new Intent(ui.get(), TweetPopup.class);
                Bundle ext = new Bundle();
                ext.putLong("TweetID", tweetID);
                ext.putString("Addition", scrNameStr);
                tweetpop.putExtras(ext);
                ui.get().startActivity(tweetpop);
                break;

            case R.id.image_attach:
                new ImagePopup(ui.get()).execute(medialinks);
                break;

            case R.id.rt_info:
                Intent rProfile = new Intent(ui.get(), UserProfile.class);
                Bundle extras = new Bundle();
                extras.putLong("userID",retweeterID);
                extras.putString("username", retweeter);
                rProfile.putExtras(extras);
                ui.get().startActivity(rProfile);
                break;
        }
    }
}