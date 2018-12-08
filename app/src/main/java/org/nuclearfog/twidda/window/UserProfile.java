package org.nuclearfog.twidda.window;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;

import org.nuclearfog.tag.Tagger.OnTagClickListener;
import org.nuclearfog.twidda.R;
import org.nuclearfog.twidda.adapter.OnItemClickListener;
import org.nuclearfog.twidda.adapter.TimelineAdapter;
import org.nuclearfog.twidda.backend.ProfileLoader;
import org.nuclearfog.twidda.backend.items.Tweet;
import org.nuclearfog.twidda.database.GlobalSettings;

import static android.os.AsyncTask.Status.RUNNING;
import static org.nuclearfog.twidda.window.TweetDetail.TWEET_REMOVED;

/**
 * User Profile Activity
 *
 * @see ProfileLoader
 */
public class UserProfile extends AppCompatActivity implements OnRefreshListener,
        OnTabChangeListener, OnItemClickListener, OnTagClickListener {

    private static final int TWEET = 1;

    private ProfileLoader mProfile;
    private GlobalSettings settings;
    private SwipeRefreshLayout homeReload, favoriteReload;
    private RecyclerView homeList, favoriteList;
    private TabHost mTab;
    private View lastTab;
    private boolean home, isFollowing, isBlocked, isMuted, canDm;
    private long userId = 0;
    private int tabIndex = 0;
    private String username = "";


    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);  // Tab view
        setContentView(R.layout.page_profile);

        b = getIntent().getExtras();
        if (b != null) {
            userId = b.getLong("userID");
            if (b.containsKey("username"))
                username = b.getString("username");
        }

        Toolbar tool = findViewById(R.id.profile_toolbar);
        View root = findViewById(R.id.user_view);
        homeList = findViewById(R.id.ht_list);
        homeReload = findViewById(R.id.hometweets);
        favoriteList = findViewById(R.id.hf_list);
        favoriteReload = findViewById(R.id.homefavorits);
        mTab = findViewById(R.id.profile_tab);

        setSupportActionBar(tool);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayShowTitleEnabled(false);

        settings = GlobalSettings.getInstance(this);
        home = userId == settings.getUserId();

        homeList.setLayoutManager(new LinearLayoutManager(this));
        favoriteList.setLayoutManager(new LinearLayoutManager(this));
        root.setBackgroundColor(settings.getBackgroundColor());

        mTab.setup();
        TabHost.TabSpec tab1 = mTab.newTabSpec("tweets");
        tab1.setContent(R.id.hometweets);
        tab1.setIndicator("", ContextCompat.getDrawable(this, R.drawable.home));
        mTab.addTab(tab1);
        TabHost.TabSpec tab2 = mTab.newTabSpec("favorites");
        tab2.setContent(R.id.homefavorits);
        tab2.setIndicator("", ContextCompat.getDrawable(this, R.drawable.favorite));
        mTab.addTab(tab2);
        lastTab = mTab.getCurrentView();

        mTab.setOnTabChangedListener(this);
        homeReload.setOnRefreshListener(this);
        favoriteReload.setOnRefreshListener(this);
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (mProfile == null) {
            TimelineAdapter homeTl = new TimelineAdapter(this);
            homeTl.setColor(settings.getHighlightColor(), settings.getFontColor());
            homeTl.toggleImage(settings.loadImages());
            homeList.setAdapter(homeTl);

            TimelineAdapter homeFav = new TimelineAdapter(this);
            homeFav.setColor(settings.getHighlightColor(), settings.getFontColor());
            homeFav.toggleImage(settings.loadImages());
            favoriteList.setAdapter(homeFav);

            mProfile = new ProfileLoader(this);
            mProfile.execute(userId, 0L);
            homeReload.setRefreshing(true);
            favoriteReload.setRefreshing(true);
        }
    }


    @Override
    protected void onStop() {
        if (mProfile != null && mProfile.getStatus() == RUNNING)
            mProfile.cancel(true);
        super.onStop();
    }


    @Override
    protected void onActivityResult(int reqCode, int returnCode, Intent i) {
        super.onActivityResult(reqCode, returnCode, i);
        if (reqCode == TWEET && returnCode == TWEET_REMOVED) {
            mProfile = null;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        getMenuInflater().inflate(R.menu.profile, m);
        if (!home) {
            m.findItem(R.id.profile_follow).setVisible(true);
            m.findItem(R.id.profile_block).setVisible(true);
            m.findItem(R.id.profile_mute).setVisible(true);
        }
        return super.onCreateOptionsMenu(m);
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu m) {
        if (!home) {
            MenuItem followIcon = m.findItem(R.id.profile_follow);
            MenuItem blockIcon = m.findItem(R.id.profile_block);
            MenuItem muteIcon = m.findItem(R.id.profile_mute);
            MenuItem dmIcon = m.findItem(R.id.profile_message);

            if (isFollowing) {
                followIcon.setIcon(R.drawable.follow_enabled);
                followIcon.setTitle(R.string.unfollow);
            } else {
                followIcon.setIcon(R.drawable.follow);
                followIcon.setTitle(R.string.follow);
            }
            if (isBlocked) {
                blockIcon.setTitle(R.string.unblock);
                followIcon.setVisible(false);
            } else {
                blockIcon.setTitle(R.string.block);
                followIcon.setVisible(true);
            }
            if (isMuted) {
                muteIcon.setTitle(R.string.unmute);
            } else {
                muteIcon.setTitle(R.string.mute);
            }
            if (!canDm) {
                dmIcon.setVisible(false);
            }
        }
        return super.onPrepareOptionsMenu(m);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mProfile != null && mProfile.getStatus() != RUNNING) {
            switch (item.getItemId()) {
                case R.id.profile_tweet:
                    Intent tweet = new Intent(this, TweetPopup.class);
                    if (!home)
                        tweet.putExtra("Addition", username);
                    startActivity(tweet);
                    break;

                case R.id.profile_follow:
                    mProfile = new ProfileLoader(this);
                    if (!isFollowing) {
                        mProfile.execute(userId, ProfileLoader.ACTION_FOLLOW);
                    } else {
                        new Builder(this).setMessage(R.string.confirm_unfollow)
                                .setNegativeButton(R.string.no_confirm, null)
                                .setPositiveButton(R.string.yes_confirm, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mProfile.execute(userId, ProfileLoader.ACTION_FOLLOW);
                                    }
                                })
                                .show();
                    }
                    break;

                case R.id.profile_block:
                    mProfile = new ProfileLoader(this);
                    if (isBlocked) {
                        mProfile.execute(userId, ProfileLoader.ACTION_BLOCK);
                    } else {
                        new Builder(this).setMessage(R.string.confirm_block)
                                .setNegativeButton(R.string.no_confirm, null)
                                .setPositiveButton(R.string.yes_confirm, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mProfile.execute(userId, ProfileLoader.ACTION_BLOCK);
                                    }
                                })
                                .show();
                    }
                    break;

                case R.id.profile_mute:
                    mProfile = new ProfileLoader(this);
                    mProfile.execute(userId, ProfileLoader.ACTION_MUTE);
                    break;

                case R.id.profile_message:
                    if (home) {
                        Intent dm = new Intent(this, DirectMessage.class);
                        startActivity(dm);
                    } else {
                        Intent sendDm = new Intent(this, MessagePopup.class);
                        sendDm.putExtra("username", username);
                        startActivity(sendDm);
                    }
                    break;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        if (tabIndex == 0) {
            super.onBackPressed();
        } else {
            mTab.setCurrentTab(0);
        }
    }


    @Override
    public void onRefresh() {
        if (mProfile != null && mProfile.getStatus() == RUNNING)
            mProfile.cancel(true);
        mProfile = new ProfileLoader(this);

        switch (tabIndex) {
            case 0:
                mProfile.execute(userId, ProfileLoader.GET_TWEETS, 1L);
                break;
            case 1:
                mProfile.execute(userId, ProfileLoader.GET_FAVORS, 1L);
                break;
        }
    }


    @Override
    public void onTabChanged(String tabId) {
        animate();

        switch (tabIndex) {
            case 0:
                homeList.smoothScrollToPosition(0);
                break;
            case 1:
                favoriteList.smoothScrollToPosition(0);
                break;
        }
        tabIndex = mTab.getCurrentTab();
    }


    @Override
    public void onItemClick(RecyclerView parent, int position) {
        if (parent.getId() == R.id.ht_list) {
            TimelineAdapter tweetAdapter = (TimelineAdapter) homeList.getAdapter();
            if (tweetAdapter != null && !homeReload.isRefreshing()) {
                Tweet tweet = tweetAdapter.getData().get(position);
                if (tweet.getEmbeddedTweet() != null)
                    tweet = tweet.getEmbeddedTweet();
                openTweet(tweet.getId(), tweet.getUser().getId(), tweet.getUser().getScreenname());
            }
        } else {
            TimelineAdapter tweetAdapter = (TimelineAdapter) favoriteList.getAdapter();
            if (tweetAdapter != null && !favoriteReload.isRefreshing()) {
                Tweet tweet = tweetAdapter.getData().get(position);
                if (tweet.getEmbeddedTweet() != null)
                    tweet = tweet.getEmbeddedTweet();
                openTweet(tweet.getId(), tweet.getUser().getId(), tweet.getUser().getScreenname());
            }
        }
    }


    @Override
    public void onClick(String text) {
        Intent intent = new Intent(this, SearchPage.class);
        intent.putExtra("search", text);
        startActivity(intent);
    }


    private void openTweet(long tweetId, long userId, String username) {
        Intent intent = new Intent(this, TweetDetail.class);
        intent.putExtra("tweetID", tweetId);
        intent.putExtra("userID", userId);
        intent.putExtra("username", username);
        startActivityForResult(intent, TWEET);
    }


    private void animate() {
        final int ANIM_DUR = 300;
        final int DIMENS = Animation.RELATIVE_TO_PARENT;
        final float LEFT = -1.0f;
        final float RIGHT = 1.0f;
        final float NULL = 0.0f;
        Animation lIn = new TranslateAnimation(DIMENS, LEFT, DIMENS, NULL, DIMENS, NULL, DIMENS, NULL);
        Animation rIn = new TranslateAnimation(DIMENS, RIGHT, DIMENS, NULL, DIMENS, NULL, DIMENS, NULL);
        Animation lOut = new TranslateAnimation(DIMENS, NULL, DIMENS, LEFT, DIMENS, NULL, DIMENS, NULL);
        Animation rOut = new TranslateAnimation(DIMENS, NULL, DIMENS, RIGHT, DIMENS, NULL, DIMENS, NULL);
        lIn.setDuration(ANIM_DUR);
        rIn.setDuration(ANIM_DUR);
        lOut.setDuration(ANIM_DUR);
        rOut.setDuration(ANIM_DUR);

        View currentTab = mTab.getCurrentView();
        if (mTab.getCurrentTab() > tabIndex) {
            lastTab.setAnimation(lOut);
            currentTab.setAnimation(rIn);
        } else {
            lastTab.setAnimation(rOut);
            currentTab.setAnimation(lIn);
        }
        lastTab = mTab.getCurrentView();
    }


    public void setConnection(boolean isFollowing, boolean isMuted, boolean isBlocked, boolean canDm) {
        this.isFollowing = isFollowing;
        this.isMuted = isMuted;
        this.isBlocked = isBlocked;
        this.canDm = canDm;
    }


    public void imageClick(String link) {
        Intent image = new Intent(this, ImageDetail.class);
        image.putExtra("link", new String[]{link});
        startActivity(image);
    }
}