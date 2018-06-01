/*
 * Copyright 2018 New Vector Ltd
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

package fr.gouv.tchap.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.support.design.widget.TextInputEditText;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ValueCallback;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.CreateRoomParams;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.util.ResourceUtils;

import java.util.HashMap;

import butterknife.BindView;
import butterknife.OnClick;
import butterknife.OnTextChanged;

import fr.gouv.tchap.util.HexagonMaskView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.MXCActionBarActivity;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorUtils;

public class TchapRoomCreationActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = TchapRoomCreationActivity.class.getSimpleName();

    private static final int REQ_CODE_UPDATE_ROOM_AVATAR = 0x10;

    @BindView(R.id.hexagon_mask_view)
    HexagonMaskView hexagonMaskView;

    @BindView(R.id.rly_hexagon_avatar)
    View hexagonAvatar;

    @BindView(R.id.tv_add_avatar_image)
    TextView addAvatarText;

    @BindView(R.id.et_room_name)
    TextInputEditText etRoomName;

    @BindView(R.id.switch_public_private_rooms)
    Switch switchPublicPrivateRoom;

    private MXSession mSession;
    private Uri mThumbnailUri = null;
    private CreateRoomParams mRoomParams = new CreateRoomParams();

    @Override
    public int getLayoutRes() {
        return R.layout.activity_tchap_room_creation;
    }

    @Override
    public void initUiAndData() {
        setWaitingView(findViewById(R.id.room_creation_spinner_views));

        mSession = Matrix.getInstance(this).getDefaultSession();

        CreateRoomParams mRoomParams = new CreateRoomParams();
        mRoomParams.visibility = RoomState.DIRECTORY_VISIBILITY_PRIVATE;
        mRoomParams.preset = CreateRoomParams.PRESET_PRIVATE_CHAT;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(this, R.attr.icon_tint_on_dark_action_bar_color));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_create_new_room:
                createNewRoom();

                // Deactivate the validate icon to avoid multiple rooms creation.
                disableRoomCreationIcon(item);

                // Hide the keyboard to see the waiting view while the room is being created.
                hideKeyboard();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.tchap_room_creation_menu, menu);
        MenuItem item = menu.findItem(R.id.action_create_new_room);

        if (null != mRoomParams.name) {
            enableRoomCreationIcon(item);
        } else {
            disableRoomCreationIcon(item);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    private void enableRoomCreationIcon(MenuItem item) {
        item.setEnabled(true);
        item.getIcon().setAlpha(255);
    }

    private void disableRoomCreationIcon(MenuItem item) {
        item.setEnabled(false);
        item.getIcon().setAlpha(130);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    @OnClick(R.id.rly_hexagon_avatar)
    void addRoomAvatar() {
        Intent intent = new Intent(TchapRoomCreationActivity.this, VectorMediasPickerActivity.class);
        intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true);
        startActivityForResult(intent, REQ_CODE_UPDATE_ROOM_AVATAR);
    }

    @OnClick(R.id.switch_public_private_rooms)
    void actionNotAvailable() {
        switchPublicPrivateRoom.setChecked(false);

        new AlertDialog.Builder(this)
                .setMessage(R.string.action_not_available_yet)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    @OnTextChanged(R.id.et_room_name)
    protected void onTextChanged(CharSequence text) {
        String roomName = text.toString().trim();

        if (!roomName.isEmpty()) {
            mRoomParams.name = roomName;
        } else {
            mRoomParams.name = null;
        }

        invalidateOptionsMenu();
        Log.i(LOG_TAG, "room name:" + mRoomParams.name);
    }

    /**
     * Process the result of the room avatar picture.
     *
     * @param aRequestCode request ID
     * @param aResultCode  request status code
     * @param aData        result data
     */
    @Override
    public void onActivityResult(int aRequestCode, int aResultCode, final Intent aData) {
        super.onActivityResult(aRequestCode, aResultCode, aData);

        if (REQ_CODE_UPDATE_ROOM_AVATAR == aRequestCode) {
            onActivityResultRoomAvatarUpdate(aResultCode, aData);
        }
    }

    /**
     * Update the avatar from the data provided the medias picker.
     *
     * @param aResultCode the result code.
     * @param intent      the provided data.
     */
    private void onActivityResultRoomAvatarUpdate(int aResultCode, final Intent intent) {
        // sanity check
        if (null == mSession) {
            return;
        }

        if (aResultCode == Activity.RESULT_OK) {
            mThumbnailUri = VectorUtils.getThumbnailUriFromIntent(this, intent, mSession.getMediasCache());

            if (null != mThumbnailUri) {
                addAvatarText.setVisibility(View.GONE);
                hexagonMaskView.setBackgroundColor(Color.WHITE);
                Glide.with(this)
                        .load(mThumbnailUri)
                        .apply(new RequestOptions()
                                .override(hexagonMaskView.getWidth(), hexagonMaskView.getHeight())
                                .centerCrop()
                        )
                        .into(hexagonMaskView);
            }
        }
    }

    /**
     * Create a new room with params.
     * The room name is mandatory.
     */
    private void createNewRoom() {
        showWaitingView();
        mSession.createRoom(mRoomParams, new SimpleApiCallback<String>(TchapRoomCreationActivity.this) {
            @Override
            public void onSuccess(final String roomId) {
                if (null != mThumbnailUri) {
                    // save the bitmap URL on the server
                    uploadRoomAvatar(roomId, mThumbnailUri);
                } else {
                    openRoom(roomId);
                }
            }

            private void onError(final String message) {
                getWaitingView().post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != message) {
                            Log.e(LOG_TAG, "Fail to create the room");
                            Toast.makeText(TchapRoomCreationActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                        hideWaitingView();
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Upload the avatar on the server.
     *
     * @param roomId          the room id.
     * @param thumbnailUri    the uri of the avatar image.
     */
    private void uploadRoomAvatar(final String roomId, final Uri thumbnailUri) {
        showWaitingView();
        ResourceUtils.Resource resource = ResourceUtils.openResource(TchapRoomCreationActivity.this, mThumbnailUri, null);
        if (null != resource) {
            mSession.getMediasCache().uploadContent(resource.mContentStream, null, resource.mMimeType, null, new MXMediaUploadListener() {

                @Override
                public void onUploadError(String uploadId, int serverResponseCode, final String serverErrorMessage) {
                    hideWaitingView();
                    Log.e(LOG_TAG, "Fail to upload the avatar");
                    promptRoomAvatarError(new ValueCallback<Boolean>() {
                        @Override
                        public void onReceiveValue(Boolean retry) {
                            if (retry) {
                                // Try again
                                uploadRoomAvatar(roomId, thumbnailUri);
                            } else {
                                // Despite an error in the treatment of the avatar image
                                // the user chooses to ignore the problem and continue the process of opening the room
                                openRoom(roomId);
                            }
                        }
                    });
                }

                @Override
                public void onUploadComplete(final String uploadId, final String contentUri) {
                    hideWaitingView();
                    updateRoomAvatar(roomId, contentUri);
                }
            });
        }
    }

    /**
     * Update the room avatar.
     *
     * @param roomId        the room id.
     * @param contentUri    the uri of the avatar image.
     */
    private void updateRoomAvatar(final String roomId, final String contentUri) {
        showWaitingView();
        Log.d(LOG_TAG, "The avatar has been uploaded, update the room avatar");
        mSession.getDataHandler().getRoom(roomId).updateAvatarUrl(contentUri, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                hideWaitingView();
                openRoom(roomId);
            }

            private void onError(String message) {
                if (null != this) {
                    hideWaitingView();
                    Log.e(LOG_TAG, "## updateAvatarUrl() failed " + message);
                    promptRoomAvatarError(new ValueCallback<Boolean>() {
                        @Override
                        public void onReceiveValue(Boolean retry) {
                            if (retry) {
                                // Try again
                                updateRoomAvatar(roomId, contentUri);
                            } else {
                                // Despite an error in the treatment of the avatar image
                                // the user chooses to ignore the problem and continue the process of opening the room
                                openRoom(roomId);
                            }
                        }
                    });
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Open the room that has just been created.
     *
     * @param roomId        the room id.
     */
    private void openRoom(final String roomId) {
        Log.d(LOG_TAG, "## openRoom(): start VectorHomeActivity..");

        HashMap<String, Object> params = new HashMap<>();
        params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
        params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
        params.put(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);
        CommonActivityUtils.goToRoomPage(TchapRoomCreationActivity.this, mSession, params);
    }

    private void promptRoomAvatarError(final ValueCallback<Boolean> valueCallback) {
        hideWaitingView();

        new AlertDialog.Builder(TchapRoomCreationActivity.this)
                .setMessage(R.string.settings_error_message_saving_avatar_on_server)
                .setPositiveButton(R.string.resend, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Try again
                        valueCallback.onReceiveValue(true);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.auth_skip, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Despite an error in the treatment of the avatar image
                        // the user chooses to ignore the problem and continue the process of opening the room
                        valueCallback.onReceiveValue(false);
                        dialog.dismiss();
                    }
                })
                .show();
    }
}