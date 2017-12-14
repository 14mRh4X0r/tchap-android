/*
 * Copyright 2017 Vector Creations Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.fragments;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.NestedScrollView;

import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.rest.model.group.Group;

import butterknife.BindView;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.util.VectorUtils;

public class GroupDetailsHomeFragment extends GroupDetailsBaseFragment {
    private static final String LOG_TAG = GroupDetailsHomeFragment.class.getSimpleName();

    @BindView(R.id.group_avatar)
    ImageView mGroupAvatar;

    @BindView(R.id.group_name_text_view)
    TextView mGroupNameTextView;

    @BindView(R.id.group_topic_text_view)
    TextView mGroupTopicTextView;

    @BindView(R.id.group_members_icon_view)
    ImageView mGroupMembersIconView;

    @BindView(R.id.group_members_text_view)
    TextView mGroupMembersTextView;

    @BindView(R.id.group_rooms_icon_view)
    ImageView mGroupRoomsIconView;

    @BindView(R.id.group_rooms_text_view)
    TextView mGroupRoomsTextView;

    @BindView(R.id.html_text_view)
    TextView mGroupHtmlTextView;

    @BindView(R.id.no_html_text_view)
    TextView noLongDescriptionTextView;

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_details_home, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshViews();
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */
    @Override
    protected void initViews() {
        mGroupMembersIconView.setImageDrawable(CommonActivityUtils.tintDrawableWithColor(ContextCompat.getDrawable(mActivity, R.drawable.riot_tab_groups), mGroupMembersTextView.getCurrentTextColor()));
        mGroupRoomsIconView.setImageDrawable(CommonActivityUtils.tintDrawableWithColor(ContextCompat.getDrawable(mActivity, R.drawable.riot_tab_rooms), mGroupMembersTextView.getCurrentTextColor()));
    }

    /*
     * *********************************************************************************************
     * Data management
     * *********************************************************************************************
     */

    @Override
    public void refreshViews() {
        Group group = mActivity.getGroup();

        VectorUtils.loadGroupAvatar(mActivity, mSession, mGroupAvatar, group);

        mGroupNameTextView.setText(group.getDisplayName());

        mGroupTopicTextView.setText(group.getShortDescription());
        mGroupTopicTextView.setVisibility(TextUtils.isEmpty(mGroupTopicTextView.getText()) ? View.GONE : View.VISIBLE);

        int roomCount = (null != group.getGroupRooms()) ? group.getGroupRooms().getEstimatedRoomCount() : 0;
        int memberCount = (null != group.getGroupUsers()) ? group.getGroupUsers().getEstimatedUsersCount() : 1;

        mGroupRoomsTextView.setText((1 == roomCount) ? getString(R.string.group_one_room) : getString(R.string.group_rooms, roomCount));
        mGroupMembersTextView.setText((1 == memberCount) ? getString(R.string.group_one_member) : getString(R.string.group_members, memberCount));

        if (!TextUtils.isEmpty(group.getLongDescription())) {
            mGroupHtmlTextView.setVisibility(View.VISIBLE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                mGroupHtmlTextView.setText(Html.fromHtml(group.getLongDescription(),Html.FROM_HTML_MODE_LEGACY));
            } else {
                mGroupHtmlTextView.setText(group.getLongDescription());
            }
            noLongDescriptionTextView.setVisibility(View.GONE);
        } else {
            noLongDescriptionTextView.setVisibility(View.VISIBLE);
            mGroupHtmlTextView.setVisibility(View.GONE);
        }
    }
}
