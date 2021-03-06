package org.nuclearfog.twidda.adapter;

import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.squareup.picasso.Picasso;

import org.nuclearfog.tag.Tagger;
import org.nuclearfog.tag.Tagger.OnTagClickListener;
import org.nuclearfog.twidda.R;
import org.nuclearfog.twidda.backend.helper.FontTool;
import org.nuclearfog.twidda.backend.items.Message;
import org.nuclearfog.twidda.backend.items.TwitterUser;
import org.nuclearfog.twidda.database.GlobalSettings;

import java.util.ArrayList;
import java.util.List;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;
import static org.nuclearfog.twidda.backend.helper.TimeString.getTimeString;

public class MessageAdapter extends Adapter<MessageAdapter.MessageHolder> {

    private OnItemSelected itemClickListener;
    private GlobalSettings settings;

    private List<Message> messages;


    public MessageAdapter(OnItemSelected itemClickListener, GlobalSettings settings) {
        this.itemClickListener = itemClickListener;
        this.settings = settings;
        messages = new ArrayList<>();
    }


    @MainThread
    public void replaceAll(@NonNull List<Message> messageList) {
        messages.clear();
        messages.addAll(messageList);
        notifyDataSetChanged();
    }


    @MainThread
    public void remove(long id) {
        int pos = -1;
        for (int index = 0; index < messages.size() && pos < 0; index++) {
            if (messages.get(index).getId() == id) {
                messages.remove(index);
                pos = index;
            }
        }
        if (pos != -1) {
            notifyItemRemoved(pos);
        }
    }


    @Override
    public long getItemId(int index) {
        return messages.get(index).getId();
    }


    @Override
    public int getItemCount() {
        return messages.size();
    }


    @NonNull
    @Override
    public MessageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dm, parent, false);
        final MessageHolder vh = new MessageHolder(view);
        FontTool.setViewFontAndColor(settings, view);

        vh.message.setMovementMethod(LinkMovementMethod.getInstance());
        vh.answer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = vh.getLayoutPosition();
                if (position != NO_POSITION) {
                    itemClickListener.onClick(messages.get(position), OnItemSelected.Action.ANSWER);
                }
            }
        });
        vh.delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = vh.getLayoutPosition();
                if (position != NO_POSITION) {
                    itemClickListener.onClick(messages.get(position), OnItemSelected.Action.DELETE);
                }
            }
        });
        vh.profile_img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = vh.getLayoutPosition();
                if (position != NO_POSITION) {
                    itemClickListener.onClick(messages.get(position), OnItemSelected.Action.PROFILE);
                }
            }
        });
        return vh;
    }


    @Override
    public void onBindViewHolder(@NonNull MessageHolder vh, int index) {
        Spanned text;
        Message message = messages.get(index);
        TwitterUser sender = message.getSender();
        text = Tagger.makeTextWithLinks(message.getText(), settings.getHighlightColor(), itemClickListener);

        vh.message.setText(text);
        vh.username.setText(sender.getUsername());
        vh.screenname.setText(sender.getScreenname());
        vh.createdAt.setText(getTimeString(message.getTime()));
        vh.receivername.setText(message.getReceiver().getScreenname());

        setIcon(vh.username, sender.isVerified() ? R.drawable.verify : 0);
        setIcon(vh.screenname, sender.isLocked() ? R.drawable.lock : 0);

        if (settings.getImageLoad()) {
            String pbLink = sender.getImageLink();
            if (!sender.hasDefaultProfileImage()) {
                pbLink += "_mini";
            }
            Picasso.get().load(pbLink).error(R.drawable.no_image).into(vh.profile_img);
        }
    }


    private void setIcon(TextView tv, @DrawableRes int icon) {
        tv.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
    }


    static class MessageHolder extends ViewHolder {
        final ImageView profile_img;
        final TextView username;
        final TextView screenname;
        final TextView receivername;
        final TextView createdAt;
        final TextView message;
        final Button answer;
        final Button delete;

        MessageHolder(View v) {
            super(v);
            profile_img = v.findViewById(R.id.dm_profileImg);
            username = v.findViewById(R.id.dm_username);
            screenname = v.findViewById(R.id.dm_screenname);
            receivername = v.findViewById(R.id.dm_receiver);
            createdAt = v.findViewById(R.id.dm_time);
            message = v.findViewById(R.id.dm_message);
            answer = v.findViewById(R.id.dm_answer);
            delete = v.findViewById(R.id.dm_delete);
        }
    }


    public interface OnItemSelected extends OnTagClickListener {

        enum Action {
            ANSWER,
            DELETE,
            PROFILE
        }

        void onClick(Message message, Action action);
    }
}