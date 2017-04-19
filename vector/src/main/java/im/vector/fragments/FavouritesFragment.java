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

import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Filter;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.FavoriteAdapter;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SimpleDividerItemDecoration;

public class FavouritesFragment extends AbsHomeFragment {
    private static final String LOG_TAG = "FavouritesFragment";

    @BindView(R.id.favorites_recycler_view)
    RecyclerView mFavoritesRecyclerView;

    @BindView(R.id.favorites_placeholder)
    TextView mFavoritesPlaceHolder;

    // rooms management
    private FavoriteAdapter mFavoritesAdapter;

    // the searched pattern
    private String mSearchedPattern;

    // the activity
    private VectorHomeActivity mActivity;

    // the favorite rooms list
    private List<Room> mFavorites = new ArrayList<>();

    // detect i
    private final MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onRoomTagEvent(String roomId) {
            if (mActivity.isWaitingViewVisible()) {
                onRoomTagUpdated(null);
            }
        }
    };

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static FavouritesFragment newInstance() {
        return new FavouritesFragment();
    }

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favourites, container, false);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = (VectorHomeActivity) getActivity();

        initViews();

        if (savedInstanceState != null) {
            // Restore adapter items
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mSession.getDataHandler().addListener(mEventsListener);
        refreshFavorites();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSession.getDataHandler().removeListener(mEventsListener);
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */
    @Override
    protected void onFloatingButtonClick() {
    }

    @Override
    protected List<Room> getRooms() {
        return new ArrayList<>(mFavorites);
    }

    @Override
    protected void onFilter(String pattern, final OnFilterListener listener) {
        if (!TextUtils.equals(mSearchedPattern, pattern)) {
            mSearchedPattern = pattern;

            mFavoritesAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
                @Override
                public void onFilterComplete(int count) {
                    updateRoomsDisplay(count);
                    listener.onFilterDone(count);
                }
            });
        }
    }

    @Override
    protected void onResetFilter() {
        mSearchedPattern = "";
        mFavoritesAdapter.getFilter().filter("");
        updateRoomsDisplay(mFavoritesAdapter.getItemCount());
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);

        // favorites
        mFavoritesRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mFavoritesRecyclerView.setHasFixedSize(true);
        mFavoritesRecyclerView.setNestedScrollingEnabled(false);

        mFavoritesAdapter = new FavoriteAdapter(getContext(), R.layout.adapter_item_room_view, new FavoriteAdapter.OnFavoritesListener() {
            @Override
            public void onSelectFavorite(Room room, int position) {
                onFavoriteSelected(room, position);
            }
        }, this);

        mFavoritesRecyclerView.addItemDecoration(new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, margin));
        mFavoritesRecyclerView.addItemDecoration(new EmptyViewItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, 40, 16, 14));

        mFavoritesRecyclerView.setAdapter(mFavoritesAdapter);
        initFavoritesDragDrop();
    }

    @Override
    public void onSummariesUpdate() {
        if (!mActivity.isWaitingViewVisible()) {
            refreshFavorites();
        }
    }

    /*
     * *********************************************************************************************
     * favorites rooms management
     * *********************************************************************************************
     */

    /**
     * Update the rooms display
     *
     * @param count the matched rooms count
     */
    private void updateRoomsDisplay(int count) {
        mFavoritesPlaceHolder.setVisibility((0 == count) && !TextUtils.isEmpty(mSearchedPattern) ? View.VISIBLE : View.GONE);
        mFavoritesRecyclerView.setVisibility((0 != count) ? View.VISIBLE : View.GONE);
    }

    /**
     * Init the rooms display
     */
    private void refreshFavorites() {
        final List<String> favouriteRoomIdList = mSession.roomIdsWithTag(RoomTag.ROOM_TAG_FAVOURITE);

        mFavorites.clear();

        if (0 != favouriteRoomIdList.size()) {
            IMXStore store = mSession.getDataHandler().getStore();
            List<RoomSummary> roomSummaries = new ArrayList<>(store.getSummaries());

            for (RoomSummary summary : roomSummaries) {
                if (favouriteRoomIdList.contains(summary.getRoomId())) {
                    Room room = store.getRoom(summary.getRoomId());

                    if (null != room) {
                        mFavorites.add(room);
                    }
                }
            }

            Comparator<Room> favComparator = new Comparator<Room>() {
                public int compare(Room r1, Room r2) {
                    return favouriteRoomIdList.indexOf(r1.getRoomId()) - favouriteRoomIdList.indexOf(r2.getRoomId());
                }
            };

            Collections.sort(mFavorites, favComparator);
        }

        mFavoritesAdapter.setRooms(mFavorites);
        updateRoomsDisplay(mFavorites.size());
    }

    /**
     * Handle a room selection
     *
     * @param room     the room
     * @param position the room index in the list
     */
    private void onFavoriteSelected(Room room, int position) {
        final String roomId;
        // cannot join a leaving room
        if (room == null || room.isLeaving()) {
            roomId = null;
        } else {
            roomId = room.getRoomId();
        }

        if (roomId != null) {
            final RoomSummary roomSummary = mSession.getDataHandler().getStore().getSummary(roomId);

            if (null != roomSummary) {
                room.sendReadReceipt(null);

                // Reset the highlight
                if (roomSummary.setHighlighted(false)) {
                    mSession.getDataHandler().getStore().flushSummary(roomSummary);
                }
            }

            // Update badge unread count in case device is offline
            CommonActivityUtils.specificUpdateBadgeUnreadCount(mSession, getContext());

            // Launch corresponding room activity
            HashMap<String, Object> params = new HashMap<>();
            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);

            CommonActivityUtils.goToRoomPage(getActivity(), mSession, params);
        }

        // Refresh the adapter item
        mFavoritesAdapter.notifyItemChanged(position);
    }

    /**
     * Init the drag and drop management
     */
    private void initFavoritesDragDrop() {
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            private int mFromPosition = -1;
            private String mRoomId;
            private int mToPosition = -1;

            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                // do not allow the drag and drop if there is a pending search
                return makeMovementFlags(!TextUtils.isEmpty(mSearchedPattern) ? 0 : (ItemTouchHelper.UP | ItemTouchHelper.DOWN), 0);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();

                if (-1 == mFromPosition) {
                    mFromPosition = fromPosition;
                    mRoomId = mFavoritesAdapter.getRoom(mFromPosition).getRoomId();
                }

                mToPosition = target.getAdapterPosition();

                mFavoritesAdapter.notifyItemMoved(mFromPosition, mToPosition);
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                if ((mFromPosition >= 0) && (mToPosition >= 0)) {
                    Log.d(LOG_TAG, "## initFavoritesDragDrop() : move room id " + mRoomId + " from " + mFromPosition + " to " + mToPosition);

                    // compute the new tag order
                    Double tagOrder = mSession.tagOrderToBeAtIndex(mToPosition, mFromPosition, RoomTag.ROOM_TAG_FAVOURITE);
                    updateRoomTag(mSession, mRoomId, tagOrder, RoomTag.ROOM_TAG_FAVOURITE);
                }

                mFromPosition = -1;
                mToPosition = -1;
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(mFavoritesRecyclerView);
    }

    /**
     * A room tag has been updated
     *
     * @param errorMessage the error message if any.
     */
    private void onRoomTagUpdated(String errorMessage) {
        mActivity.stopWaitingView();

        refreshFavorites();
        mFavoritesAdapter.notifyDataSetChanged();

        if (!TextUtils.isEmpty(errorMessage)) {
            Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Update the room tag.
     *
     * @param session  the session
     * @param roomId   the room id.
     * @param tagOrder the tag order.
     * @param newtag   the new tag.
     */
    private void updateRoomTag(MXSession session, String roomId, Double tagOrder, String newtag) {
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            String oldTag = null;

            // retrieve the tag from the room info
            RoomAccountData accountData = room.getAccountData();

            if ((null != accountData) && accountData.hasTags()) {
                oldTag = accountData.getKeys().iterator().next();
            }

            // if the tag order is not provided, compute it
            if (null == tagOrder) {
                tagOrder = 0.0;

                if (null != newtag) {
                    tagOrder = session.tagOrderToBeAtIndex(0, Integer.MAX_VALUE, newtag);
                }
            }

            // show a spinner
            mActivity.showWaitingView();

            // and work
            room.replaceTag(oldTag, newtag, tagOrder, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    // wait the room tag echo
                }

                @Override
                public void onNetworkError(Exception e) {
                    onRoomTagUpdated(e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onRoomTagUpdated(e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onRoomTagUpdated(e.getLocalizedMessage());
                }
            });
        }
    }
}
