/*
 * Copyright (c) 2021 Huawei Device Co., Ltd.
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

package com.huawei.codelab.slice;

import com.huawei.codelab.ResourceTable;
import com.huawei.codelab.devices.SelectDeviceDialog;
import com.huawei.codelab.utils.CommonData;
import com.huawei.codelab.utils.CommonUtil;
import com.huawei.codelab.utils.LogUtil;

import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.ability.IAbilityConnection;
import ohos.aafwk.content.Intent;
import ohos.aafwk.content.Operation;
import ohos.agp.components.Button;
import ohos.agp.components.Component;
import ohos.agp.components.Image;
import ohos.agp.components.Text;
import ohos.agp.utils.LayoutAlignment;
import ohos.agp.window.dialog.ToastDialog;
import ohos.app.Context;
import ohos.bundle.ElementName;
import ohos.bundle.IBundleManager;
import ohos.data.distributed.common.KvManagerConfig;
import ohos.data.distributed.common.KvManagerFactory;
import ohos.distributedschedule.interwork.DeviceInfo;
import ohos.distributedschedule.interwork.DeviceManager;
import ohos.event.commonevent.CommonEventData;
import ohos.event.commonevent.CommonEventManager;
import ohos.event.commonevent.CommonEventSubscribeInfo;
import ohos.event.commonevent.CommonEventSubscriber;
import ohos.event.commonevent.CommonEventSupport;
import ohos.event.commonevent.MatchingSkills;
import ohos.rpc.IRemoteBroker;
import ohos.rpc.IRemoteObject;
import ohos.rpc.MessageParcel;
import ohos.rpc.MessageOption;
import ohos.rpc.RemoteException;

import java.util.ArrayList;
import java.util.List;

import static ohos.security.SystemPermission.DISTRIBUTED_DATASYNC;

/**
 * PictureGameAbilitySlice
 *
 * @since 2021-01-11
 */
public class PictureGameAbilitySlice extends AbilitySlice {
    private static final String TAG = CommonData.TAG + PictureGameAbilitySlice.class.getSimpleName();
    private static final int PERMISSION_CODE = 20201203;
    private static final int DELAY_TIME = 10;
    private Button restartButton;
    private Button pictureTogether;
    private Image image00 = null;
    private Image image01 = null;
    private Image image02 = null;
    private Image image10 = null;
    private Image image11 = null;
    private Image image12 = null;
    private Image image20 = null;
    private Image image21 = null;
    private Image image22 = null;
    private Image[] imagePosition = {image00, image01, image02, image10, image11, image12, image20, image21, image22};
    private int[] imageList = {ResourceTable.Media_picture_01, ResourceTable.Media_picture_02,
            ResourceTable.Media_picture_03, ResourceTable.Media_picture_04,
            ResourceTable.Media_picture_05, ResourceTable.Media_picture_06,
            ResourceTable.Media_picture_07, ResourceTable.Media_picture_08,
            ResourceTable.Media_picture_09};
    private int[] imageResourceTable = {ResourceTable.Id_pt_0000, ResourceTable.Id_pt_0001, ResourceTable.Id_pt_0002,
            ResourceTable.Id_pt_0100, ResourceTable.Id_pt_0101, ResourceTable.Id_pt_0102,
            ResourceTable.Id_pt_0200, ResourceTable.Id_pt_0201, ResourceTable.Id_pt_0202};
    private int[] imageIndex = new int[imageList.length];

    private int imageX = 3;
    private int imageY = 3;
    private int imageCount = imageX * imageY;
    private int blankPosition = imageCount - 1;
    private int blankResId = ResourceTable.Id_pt_0202;
    private List<DeviceInfo> devices = new ArrayList<>();
    private PictureRemoteProxy proxy;
    private IAbilityConnection conn;
    private boolean isConnected = false;
    private MyCommonEventSubscriber subscriber;

    private String remoteDeviceId;
    private boolean isLocal = true;
    private String localDeviceId;
    private int moveImageId = -1;
    private int movePosition = -1;

    private long sendTime = 0;
    private long reciveTime = 0;
    private boolean shareFlag = true;

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_picture);
        grantPermission(getContext());
        initView(intent);
        initRemoteView(intent);
        subscribe();
    }

    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }

    private void initView(Intent intent) {
        LogUtil.info(TAG, "initView");
        localDeviceId = KvManagerFactory.getInstance()
                .createKvManager(new KvManagerConfig(this))
                .getLocalDeviceInfo().getId();
        isLocal = intent.getBooleanParam(CommonData.KEY_IS_LOCAL, true);

        for (int i = 0; i < imageCount; i++) {
            if (findComponentById(imageResourceTable[i]) instanceof Image) {
                imagePosition[i] = (Image) findComponentById(imageResourceTable[i]);
                imagePosition[i].setClickedListener(new ImageClick());
            }
        }
        if (findComponentById(ResourceTable.Id_restart_button) instanceof Button) {
            restartButton = (Button) findComponentById(ResourceTable.Id_restart_button);
        }
        if (findComponentById(ResourceTable.Id_picture_together) instanceof Button) {
            pictureTogether = (Button) findComponentById(ResourceTable.Id_picture_together);
        }
        restartButton.setClickedListener(component -> restartFun());
        pictureTogether.setClickedListener(component -> getDevices());
        if (findComponentById(ResourceTable.Id_text_title) instanceof Text) {
            Text textTitle = (Text) findComponentById(ResourceTable.Id_text_title);
            textTitle.setText(isLocal ? "益智拼图游戏(本地端)" : "益智拼图游戏(远程端)");
        }
        pictureTogether.setVisibility(isLocal ? Component.VISIBLE : Component.HIDE);

        pictureRandom();
    }


    private void initRemoteView(Intent intent) {
        if (!isLocal) {
            remoteDeviceId = intent.getStringParam(CommonData.KEY_REMOTE_DEVICEID);
            connectRemotePa(remoteDeviceId, PictureRemoteProxy.REQUEST_SEND_DATA);
            if (imageIndex != null) {
                updateDataInfo(intent);
            }
        }
    }

    private class ImageClick implements Component.ClickedListener {
        @Override
        public void onClick(Component component) {
            int imageId = component.getId();
            for (int position = 0; position < imageIndex.length; position++) {
                if (imageId == imageResourceTable[position]) {
                    // Complete the picture movement and record the movement information
                    moveFun(imageId, position);
                    moveImageId = imageId;
                    movePosition = position;
                }
            }
            // Refresh the page display
            setImages(imageIndex);
            // send data
            senDataToRemoteFun();
        }
    }


    private void moveFun(int resourceId, int position) {
        LogUtil.info(TAG, "moveFun, resourceId is " + resourceId + " position is " + position);
        int positionX = position / imageX;
        int positionY = position % imageY;
        int blankX = blankPosition / imageX;
        int blankY = blankPosition % imageY;
        int num1 = Math.abs(positionX - blankX);
        int num2 = Math.abs(positionY - blankY);
        LogUtil.info(TAG, "blankPosition is " + blankPosition +
                " num1 is " + num1 + " num2 is " + num2);
        if ((num1 == 0 && num2 == 1) || (num1 == 1 && num2 == 0)) {
            LogUtil.info(TAG, "can move image");
            if (findComponentById(resourceId) instanceof Image) {
                Image clickImage = (Image) findComponentById(resourceId);
                clickImage.setVisibility(Component.INVISIBLE);
            }
            Image blankButton = (Image) findComponentById(blankResId);
            blankButton.setPixelMap(imageList[imageIndex[position]]);
            blankButton.setVisibility(Component.VISIBLE);

            int imageTemp = imageIndex[position];
            imageIndex[position] = imageIndex[blankPosition];
            imageIndex[blankPosition] = imageTemp;
            LogUtil.info(TAG, "moveFun, come to change blank position");
            blankPosition = position;
            blankResId = resourceId;
        }
        gameOverFun();
    }

    private void setFlagValue(int resourceId, int position) {
        LogUtil.info(TAG, "setFlagValue, resourceId is " + resourceId + " position is " + position);
        int positionX = position / imageX;
        int positionY = position % imageY;
        int blankX = blankPosition / imageX;
        int blankY = blankPosition % imageY;
        int num1 = Math.abs(positionX - blankX);
        int num2 = Math.abs(positionY - blankY);
        LogUtil.info(TAG, "blankPosition is " + blankPosition +
                " num1 is " + num1 + " num2 is " + num2);

        if ((num1 == 0 && num2 == 1) || (num1 == 1 && num2 == 0)) {
            LogUtil.info(TAG, "setFlagValue, come to change blank position");
            blankPosition = position;
            blankResId = resourceId;
        }
        gameOverFun();
    }

    private void restartFun() {
        LogUtil.info(TAG, "restartFun");
        restore();
        pictureRandom();
        senDataToRemoteFun();
    }

    private void restore() {
        LogUtil.info(TAG, "restore");
        setImageClickable(true);
        if (findComponentById(blankResId) instanceof Image) {
            Image image = (Image) findComponentById(blankResId);
            image.setVisibility(Component.VISIBLE);
        }
        if (findComponentById(ResourceTable.Id_pt_0202) instanceof Image) {
            Image blank = (Image) findComponentById(ResourceTable.Id_pt_0202);
            blank.setVisibility(Component.INVISIBLE);
        }
        blankResId = ResourceTable.Id_pt_0202;
        blankPosition = imageCount - 1;
    }

    private void setImageClickable(boolean flag) {
        LogUtil.info(TAG, "setImageClickable and flag is " + flag);
        for (int i = 0; i < imageCount; i++) {
            imagePosition[i].setClickable(flag);
        }
    }

    private void gameOverFun() {
        boolean loop = true;
        for (int i = 0; i < imageIndex.length; i++) {
            if (imageIndex[i] != i) {
                loop = false;
                break;
            }
        }

        if (loop) {
            LogUtil.info(TAG, "game is over");
            setImageClickable(false);
            imagePosition[imageCount - 1].setPixelMap(ResourceTable.Media_picture_09);
            imagePosition[imageCount - 1].setVisibility(Component.VISIBLE);
        }
    }

    private void connectRemotePa(String deviceId, int requestType) {
        if (!deviceId.isEmpty()) {
            Intent connectPaIntent = new Intent();
            Operation operation = new Intent.OperationBuilder()
                    .withDeviceId(deviceId)
                    .withBundleName(getBundleName())
                    .withAbilityName(CommonData.PICTURE_GAME_SERVICE_NAME)
                    .withFlags(Intent.FLAG_ABILITYSLICE_MULTI_DEVICE)
                    .build();
            connectPaIntent.setOperation(operation);

            conn = new IAbilityConnection() {
                @Override
                public void onAbilityConnectDone(ElementName elementName, IRemoteObject remote, int resultCode) {
                    LogUtil.info(TAG, "onAbilityConnectDone......");
                    connectAbility(elementName, remote, requestType);
                }

                @Override
                public void onAbilityDisconnectDone(ElementName elementName, int i) {
                    disconnectAbility(this);
                    LogUtil.info(TAG, "onAbilityDisconnectDone......");
                }
            };
            isConnected = getContext().connectAbility(connectPaIntent, conn);
        }
    }

    private void connectAbility(ElementName elementName, IRemoteObject remote, int requestType) {
        proxy = new PictureRemoteProxy(remote);
        LogUtil.error(TAG, "connectRemoteAbility done");
        if (proxy != null) {
            try {
                proxy.senDataToRemote(requestType);
            } catch (RemoteException e) {
                LogUtil.error(TAG, "onAbilityConnectDone RemoteException");
            }
        }
    }

    class PictureRemoteProxy implements IRemoteBroker {
        private static final int ERR_OK = 0;
        private static final int REQUEST_START_ABILITY = 1;
        private static final int REQUEST_SEND_DATA = 2;
        private final IRemoteObject remote;

        PictureRemoteProxy(IRemoteObject remote) {
            this.remote = remote;
        }

        @Override
        public IRemoteObject asObject() {
            return remote;
        }

        private void senDataToRemote(int requestType) throws RemoteException {
            MessageParcel data = MessageParcel.obtain();
            MessageParcel reply = MessageParcel.obtain();
            try {
                isLocal = false;
                data.writeIntArray(imageIndex);
                data.writeString(localDeviceId);
                data.writeBoolean(isLocal);
                data.writeInt(moveImageId);
                data.writeInt(movePosition);
                MessageOption option = new MessageOption(MessageOption.TF_SYNC);
                remote.sendRequest(requestType, data, reply, option);
                int ec = reply.readInt();
                if (ec != ERR_OK) {
                    LogUtil.error(TAG, "ec != ERR_OK RemoteException");
                }
            } catch (RemoteException e) {
                LogUtil.error(TAG, "RemoteException");
            } finally {
                data.reclaim();
                reply.reclaim();
            }
        }
    }

    private void senDataToRemoteFun() {
        if (!shareFlag) {
            LogUtil.info(TAG, "should not senDataToRemote");
            return;
        }
        sendTime = System.currentTimeMillis();
        LogUtil.info(TAG, "senDataToRemote, and sendTime is " + sendTime);
        if (isConnected && proxy != null) {
            try {
                proxy.senDataToRemote(PictureRemoteProxy.REQUEST_SEND_DATA);
            } catch (RemoteException e) {
                LogUtil.info(TAG, "processEvent RemoteException");
            }
        } else {
            LogUtil.info(TAG, "processEvent RemoteException Error");
        }
    }

    private static void grantPermission(Context context) {
        LogUtil.info(TAG, "grantPermission");
        if (context.verifySelfPermission(DISTRIBUTED_DATASYNC) != IBundleManager.PERMISSION_GRANTED) {
            if (context.canRequestPermission(DISTRIBUTED_DATASYNC)) {
                context.requestPermissionsFromUser(
                        new String[]{DISTRIBUTED_DATASYNC}, PERMISSION_CODE);
            }
        }
    }

    private void getDevices() {
        if (devices.size() > 0) {
            devices.clear();
        }
        List<DeviceInfo> deviceInfos =
                DeviceManager.getDeviceList(ohos.distributedschedule.interwork.DeviceInfo.FLAG_GET_ONLINE_DEVICE);
        LogUtil.info(TAG, "deviceInfos size is :" + deviceInfos.size());
        devices.addAll(deviceInfos);
        showDevicesDialog();
    }

    private void showDevicesDialog() {
        new SelectDeviceDialog(this, devices, deviceInfo -> {
            connectRemotePa(deviceInfo.getDeviceId(), PictureRemoteProxy.REQUEST_START_ABILITY);
        }).show();
    }

    class MyCommonEventSubscriber extends CommonEventSubscriber {
        MyCommonEventSubscriber(CommonEventSubscribeInfo info) {
            super(info);
        }

        @Override
        public void onReceiveEvent(CommonEventData commonEventData) {
            reciveTime = System.currentTimeMillis();
            LogUtil.info(TAG, "onReceiveEvent, sendTime - reciveTime is " + (sendTime - reciveTime));
            if (Math.abs(sendTime - reciveTime) <= 500) {
                LogUtil.info(TAG, "almost at the same time, do not handle recive msg");
                shareFlag = false;
                new ToastDialog(getContext())
                        .setText("操作冲突，后续独立")
                        .setAlignment(LayoutAlignment.CENTER)
                        .show();
                return;
            }

            Intent intent = commonEventData.getIntent();
            updateDataInfo(intent);
        }
    }

    private void updateDataInfo(Intent intent) {
        imageIndex = intent.getIntArrayParam(CommonData.KEY_IMAGE_INDEX);
        moveImageId = intent.getIntParam(CommonData.KEY_MOVE_IMAGE_ID, -1);
        movePosition = intent.getIntParam(CommonData.KEY_MOVE_POSITION, -1);
        LogUtil.info(TAG, "receive moveImageId:" + moveImageId);
        LogUtil.info(TAG, "receive movePosition:" + movePosition);
        getUITaskDispatcher().delayDispatch(() -> setImages(imageIndex), DELAY_TIME);
    }


    private void setImages(int[] imageIndex) {
        LogUtil.info(TAG, "setImages");
        if (imageIndex != null && imageIndex.length > 0) {
            for (int index = 0; index < imageIndex.length; index++) {
                imagePosition[index].setPixelMap(imageList[imageIndex[index]]);

                if (imageIndex[index] == 8) {
                    imagePosition[index].setVisibility(Component.INVISIBLE);
                } else {
                    imagePosition[index].setVisibility(Component.VISIBLE);
                }
            }
        }
        setFlagValue(moveImageId, movePosition);
    }

    private void subscribe() {
        MatchingSkills matchingSkills = new MatchingSkills();
        matchingSkills.addEvent(CommonData.PICTURE_GAME_EVENT);
        matchingSkills.addEvent(CommonEventSupport.COMMON_EVENT_SCREEN_ON);
        CommonEventSubscribeInfo subscribeInfo = new CommonEventSubscribeInfo(matchingSkills);
        subscriber = new MyCommonEventSubscriber(subscribeInfo);
        try {
            LogUtil.info("", "PICTURE_GAME_EVENT subscribeCommonEvent");
            CommonEventManager.subscribeCommonEvent(subscriber);
        } catch (RemoteException e) {
            LogUtil.error("", "subscribeCommonEvent occur exception.");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unSubscribe();
    }

    private void unSubscribe() {
        try {
            CommonEventManager.unsubscribeCommonEvent(subscriber);
        } catch (RemoteException e) {
            LogUtil.error(TAG, "unSubscribe Exception");
        }
    }

    private void pictureRandom() {
        LogUtil.info(TAG, "pictureRandom start");
        for (int i = 0; i < imageList.length; i++) {
            imageIndex[i] = i;
        }
        for (int count = 0; count < 20; count++) {
            int rand1 = CommonUtil.getRandomInt(imageList.length - 1);
            int rand2 = CommonUtil.getRandomInt(imageList.length - 1);
            int imageTemp = imageIndex[rand1];
            imageIndex[rand1] = imageIndex[rand2];
            imageIndex[rand2] = imageTemp;
        }
        for (int i = 0; i < imageCount; i++) {
            imagePosition[i].setPixelMap(imageList[imageIndex[i]]);
        }
        LogUtil.info(TAG, "pictureRandom end");
    }
}