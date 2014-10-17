package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.matrixandroidsdk.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * An adapter which can display events. Events are not limited to m.room.message event types, but
 * can include topic changes (m.room.topic) and room member changes (m.room.member).
 */
public class MessagesAdapter extends ArrayAdapter<Event> {

    // text, images, notices(topics, room names, membership changes,
    // displayname changes, avatar url changes), and emotes!
    private static final int NUM_ROW_TYPES = 4;

    private static final int ROW_TYPE_TEXT = 0;
    private static final int ROW_TYPE_IMAGE = 1;
    private static final int ROW_TYPE_NOTICE = 2;
    private static final int ROW_TYPE_EMOTE = 3;

    private static final String LOG_TAG = "MessagesAdapter";

    private Context mContext;
    private HashMap<Integer, Integer> mRowTypeToLayoutId = new HashMap<Integer, Integer>();
    private LayoutInflater mLayoutInflater;

    private int mOddColourResId;
    private int mEvenColourResId;

    private DateFormat mDateFormat;

    public MessagesAdapter(Context context, int textResLayoutId, int imageResLayoutId,
                           int noticeResLayoutId, int emoteRestLayoutId) {
        super(context, 0);
        mContext = context;
        mRowTypeToLayoutId.put(ROW_TYPE_TEXT, textResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_IMAGE, imageResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_NOTICE, noticeResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_EMOTE, emoteRestLayoutId);
        mLayoutInflater = LayoutInflater.from(mContext);
        mDateFormat = new SimpleDateFormat("MMM d HH:mm", Locale.getDefault());
        setNotifyOnChange(true);
    }

    public void setAlternatingColours(int oddResId, int evenResId) {
        mOddColourResId = oddResId;
        mEvenColourResId = evenResId;
    }

    @Override
    public int getViewTypeCount() {
        return NUM_ROW_TYPES;
    }

    public void addToFront(Event event) {
        if (isKnownEvent(event)) {
            this.insert(event, 0);
        }
    }

    @Override
    public void add(Event event) {
        if (isKnownEvent(event)) {
            super.add(event);
        }
    }

    @Override
    public int getItemViewType(int position) {
        Event event = getItem(position);

        if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            String msgType = event.content.getAsJsonPrimitive("msgtype").getAsString();

            if (msgType.equals(Message.MSGTYPE_TEXT)) {
                return ROW_TYPE_TEXT;
            }
            else if (msgType.equals(Message.MSGTYPE_IMAGE)) {
                return ROW_TYPE_IMAGE;
            }
            else if (msgType.equals(Message.MSGTYPE_EMOTE)) {
                return ROW_TYPE_EMOTE;
            }
            else {
                throw new RuntimeException("Unknown msgtype: " + msgType);
            }
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type) ||
                 Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) ||
                 Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
            return ROW_TYPE_NOTICE;
        }
        else {
            throw new RuntimeException("Unknown event type: " + event.type);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        try {
            switch (getItemViewType(position)) {
                case ROW_TYPE_TEXT:
                    return getTextView(position, convertView, parent);
                case ROW_TYPE_IMAGE:
                    return getImageView(position, convertView, parent);
                case ROW_TYPE_NOTICE:
                    return getNoticeView(position, convertView, parent);
                case ROW_TYPE_EMOTE:
                    return getEmoteView(position, convertView, parent);
                default:
                    throw new RuntimeException("Unknown item view type for position " + position);
            }
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Failed to render view at position " + position + ": "+e);
            return convertView;
        }
    }


    private View getTextView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_TEXT), parent, false);
        }

        Event msg = getItem(position);
        String body = msg.content.get("body") == null ? null : msg.content.get("body").getAsString();


        TextView textView = (TextView) convertView.findViewById(R.id.messagesAdapter_body);
        textView.setText(body);

        // check for html formatting
        if (msg.content.has("formatted_body") && msg.content.has("format")) {
            try {
                String format = msg.content.getAsJsonPrimitive("format").getAsString();
                if ("org.matrix.custom.html".equals(format)) {
                    textView.setText(Html.fromHtml(msg.content.getAsJsonPrimitive("formatted_body").getAsString()));
                }
            }
            catch (Exception e) {
                // ignore: The json object was probably malformed and we have already set the fallback
            }
        }

        textView = (TextView) convertView.findViewById(R.id.messagesAdapter_sender);
        textView.setText(msg.userId);

        textView = (TextView) convertView.findViewById(R.id.messagesAdapter_timestamp);
        textView.setText(getTimestamp(msg.ts));

        setBackgroundColour(convertView, position);
        return convertView;

    }

    private View getImageView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_IMAGE), parent, false);
        }
        Event msg = getItem(position);
        String thumbUrl = msg.content.get("thumbnail_url") == null ? null : msg.content.get("thumbnail_url").getAsString();

        ImageView imageView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image);
        AdapterUtils.loadBitmap(imageView, thumbUrl);

        TextView textView = (TextView) convertView.findViewById(R.id.messagesAdapter_sender);
        textView.setText(msg.userId);
        setBackgroundColour(convertView, position);
        return convertView;
    }

    private View getNoticeView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_NOTICE), parent, false);
        }
        Event msg = getItem(position);

        String notice = null;
        if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(msg.type)) {
            notice = mContext.getString(R.string.notice_topic_changed,
                    msg.userId, msg.content.getAsJsonPrimitive("topic").getAsString());
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(msg.type)) {
            notice = mContext.getString(R.string.notice_room_name_changed,
                    msg.userId, msg.content.getAsJsonPrimitive("name").getAsString());
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(msg.type)) {
            // m.room.member is used to represent at least 3 different changes in state: membership,
            // avatar pic url and display name. We need to figure out which thing changed to display
            // the right text.
            JsonObject prevState = msg.prevContent;
            if (prevState == null) {
                // if there is no previous state, it has to be an invite or a join as they are the first
                // m.room.member events for a user.
                notice = getMembershipNotice(msg);
            }
            else {
                // check if the membership changed
                if (hasStringValueChanged(msg, "membership")) {
                    notice = getMembershipNotice(msg);
                }
                // check if avatar url changed
                else if (hasStringValueChanged(msg, "avatar_url")) {
                    notice = getAvatarChangeNotice(msg);
                }
                // check if the display name changed.
                else if (hasStringValueChanged(msg, "displayname")) {
                    notice = getDisplayNameChangeNotice(msg);
                }
                else {
                    // well shucks, I'm all out of ideas, let's whine.
                    Log.e(LOG_TAG, "Redundant membership event. PREV=>"+prevState+" NOW=>"+msg.content);
                }
            }
        }

        TextView textView = (TextView)  convertView.findViewById(R.id.messagesAdapter_notice);
        textView.setText(notice);

        return convertView;
    }

    private View getEmoteView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_EMOTE), parent, false);
        }

        Event msg = getItem(position);

        String emote = msg.userId + " ";

        try {
            emote += msg.content.getAsJsonPrimitive("body").getAsString();
        }
        catch (Exception e) {} // malformed json, ignore.

        TextView textView = (TextView)  convertView.findViewById(R.id.messagesAdapter_emote);
        textView.setText(emote);
        return convertView;
    }


    private String getMembershipNotice(Event msg) {
        String membership = msg.content.getAsJsonPrimitive("membership").getAsString();
        if (RoomMember.MEMBERSHIP_INVITE.equals(membership)) {
            return mContext.getString(R.string.notice_room_invite, msg.userId, msg.stateKey);
        }
        else if (RoomMember.MEMBERSHIP_JOIN.equals(membership)) {
            return mContext.getString(R.string.notice_room_join, msg.userId);
        }
        else if (RoomMember.MEMBERSHIP_LEAVE.equals(membership)) {
            return mContext.getString(R.string.notice_room_leave, msg.userId);
        }
        else if (RoomMember.MEMBERSHIP_BAN.equals(membership)) {
            return mContext.getString(R.string.notice_room_ban, msg.userId);
        }
        else {
            // eh?
            Log.e(LOG_TAG, "Unknown membership: "+membership);
        }
        return null;
    }

    private String getTimestamp(long ts) {
        return mDateFormat.format(new Date(ts));
    }

    private String getAvatarChangeNotice(Event msg) {
        // TODO: Pictures!
        return mContext.getString(R.string.notice_avatar_url_changed, msg.userId);
    }

    private String getDisplayNameChangeNotice(Event msg) {
        return mContext.getString(R.string.notice_display_name_changed,
                msg.userId,
                msg.prevContent.getAsJsonPrimitive("displayname").getAsString(),
                msg.content.getAsJsonPrimitive("displayname").getAsString()
        );
    }

    private boolean hasStringValueChanged(Event msg, String key) {
        JsonObject prevContent = msg.prevContent;
        if (prevContent.has(key) && msg.content.has(key)) {
            String old = prevContent.get(key) == JsonNull.INSTANCE ? null : prevContent.get(key).getAsString();
            String current = msg.content.get(key) == JsonNull.INSTANCE ? null : msg.content.get(key).getAsString();
            if (old == null && current == null) {
                return false;
            }
            else if (old != null) {
                return !old.equals(current);
            }
            else {
                return !current.equals(old);
            }
        }
        else if (!prevContent.has(key) && !msg.content.has(key)) {
            return false; // this key isn't in either prev or current
        }
        else {
            return true; // this key is in one but not the other.
        }
    }

    private void setBackgroundColour(View view, int position) {
        if (mOddColourResId != 0 && mEvenColourResId != 0) {
            view.setBackgroundColor(position%2 == 0 ? mEvenColourResId : mOddColourResId);
        }
    }

    private boolean isKnownEvent(Event event) {
        if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            JsonPrimitive j = event.content.getAsJsonPrimitive("msgtype");
            String msgType = j == null ? null : j.getAsString();
            if (Message.MSGTYPE_IMAGE.equals(msgType) || Message.MSGTYPE_TEXT.equals(msgType) ||
                    Message.MSGTYPE_EMOTE.equals(msgType)) {
                return true;
            }
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
            if (event.prevContent != null) {
                // check that something between prev and current has changed before accepting.
                return
                        hasStringValueChanged(event, "avatar_url") ||
                        hasStringValueChanged(event, "displayname") ||
                        hasStringValueChanged(event, "membership");
            }
            return true; // no prev content is a change.
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type) ||
                 Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
            return true;
        }
        return false;
    }


}