package com.yzx.chat.presenter;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.yzx.chat.R;
import com.yzx.chat.base.BaseHttpCallback;
import com.yzx.chat.bean.ContactBean;
import com.yzx.chat.bean.UserBean;
import com.yzx.chat.configure.Constants;
import com.yzx.chat.contract.SplashContract;
import com.yzx.chat.network.api.JsonResponse;
import com.yzx.chat.network.api.auth.AuthApi;
import com.yzx.chat.network.api.auth.TokenVerifyBean;
import com.yzx.chat.network.api.contact.ContactApi;
import com.yzx.chat.network.api.contact.GetUserContactsBean;
import com.yzx.chat.network.chat.IMClient;
import com.yzx.chat.network.framework.Call;
import com.yzx.chat.network.framework.HttpCallback;
import com.yzx.chat.network.framework.HttpResponse;
import com.yzx.chat.tool.ApiHelper;
import com.yzx.chat.network.chat.DBManager;
import com.yzx.chat.tool.IdentityManager;
import com.yzx.chat.util.AndroidUtil;
import com.yzx.chat.util.LogUtil;
import com.yzx.chat.util.AsyncUtil;
import com.yzx.chat.util.MD5Util;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.rong.imlib.RongIMClient;

/**
 * Created by YZX on 2017年11月04日.
 * 每一个不曾起舞的日子 都是对生命的辜负
 */
public class SplashPresenter implements SplashContract.Presenter {
    //
    private SplashContract.View mSplashView;
    private Call<JsonResponse<TokenVerifyBean>> mTokenVerify;
    private Call<JsonResponse<GetUserContactsBean>> mGetUserFriendsTask;

    private AtomicInteger mTaskCount;
    private boolean isInitIMComplete;
    private boolean isInitHTTPComplete;

    @Override
    public void attachView(SplashContract.View view) {
        mSplashView = view;
        mTaskCount = new AtomicInteger(2);
    }

    @Override
    public void detachView() {
        AsyncUtil.cancelCall(mTokenVerify);
        AsyncUtil.cancelCall(mGetUserFriendsTask);
        mSplashView = null;
    }

    @Override
    public void init(boolean isAlreadyLogged) {
        if (!IdentityManager.initFromLocal()) {
            mSplashView.startLoginActivity();
        } else {
            isInitIMComplete = isAlreadyLogged;
            initHTTPServer(isAlreadyLogged);
        }

    }

    private synchronized void initComplete() {
        switch (mTaskCount.decrementAndGet()) {
            case 0:
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (isInitHTTPComplete && isInitIMComplete) {
                            DBManager.init(mSplashView.getContext(), MD5Util.encrypt(IdentityManager.getInstance().getUserID()), Constants.DATABASE_VERSION);
                            mSplashView.startHomeActivity();
                        } else {
                            mSplashView.startLoginActivity();
                        }
                    }
                });
                break;
            case 1:
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        initIMServer();
                    }
                });
                break;
        }
    }

    private void initIMServer() {
        if (isInitIMComplete) {
            initComplete();
            return;
        }
        IMClient.getInstance().login(IdentityManager.getInstance().getToken(), new IMClient.OnLoginListener() {
            @Override
            public void onLoginSuccess(boolean isConnectedToServer) {
                if (!isConnectedToServer) {
                    LogUtil.e("login success, but can not connect to the server");
                }
                isInitIMComplete = true;
                initComplete();
            }

            @Override
            public void onLoginFailure(String error) {
                AndroidUtil.showToast(R.string.SplashPresenter_TokenIncorrect);
                LogUtil.e(error);
                initComplete();
            }
        });
    }

    private void initHTTPServer(boolean isAlreadyLogged) {
        ContactApi contactApi = (ContactApi) ApiHelper.getProxyInstance(ContactApi.class);
        AuthApi authApi = (AuthApi) ApiHelper.getProxyInstance(AuthApi.class);

        AsyncUtil.cancelCall(mTokenVerify);
        mTokenVerify = authApi.tokenVerify();
        mTokenVerify.setCallback(new HttpCallback<JsonResponse<TokenVerifyBean>>() {
            private boolean isSuccess;

            @Override
            public void onResponse(HttpResponse<JsonResponse<TokenVerifyBean>> response) {
                if (response.getResponseCode() == 200) {
                    JsonResponse<TokenVerifyBean> jsonResponse = response.getResponse();
                    if (jsonResponse != null) {
                        TokenVerifyBean tokenVerifyBean = jsonResponse.getData();
                        if (jsonResponse.getStatus() == 200 && tokenVerifyBean != null) {
                            UserBean userBean = tokenVerifyBean.getUser();
                            if (userBean != null && !userBean.isEmpty() && IdentityManager.getInstance().updateUserInfo(userBean)) {
                                isSuccess = true;
                                return;
                            }
                        }
                    }
                }
                initComplete();
            }

            @Override
            public void onError(@NonNull Throwable e) {
                e.printStackTrace();
                if (IdentityManager.initFromLocal()) {
                    isSuccess = true;
                } else {
                    mSplashView.startLoginActivity();
                }
            }

            @Override
            public boolean isExecuteNextTask() {
                return isSuccess;
            }
        });

        AsyncUtil.cancelCall(mGetUserFriendsTask);
        mGetUserFriendsTask = contactApi.getUserContacts();
        mGetUserFriendsTask.setCallback(new BaseHttpCallback<GetUserContactsBean>() {
            @Override
            protected void onSuccess(GetUserContactsBean response) {
                List<ContactBean> contactBeans = response.getContacts();
                if (contactBeans != null) {
                    IMClient.getInstance().contactManager().initContacts(contactBeans);
                } else {
                    LogUtil.e("response.getContacts() is null");
                    IMClient.getInstance().contactManager().initContactsFromDB();
                }
                isInitHTTPComplete = true;
                initComplete();
            }

            @Override
            protected void onFailure(String message) {
                LogUtil.e(message);
                IMClient.getInstance().contactManager().initContactsFromDB();
                initComplete();
            }
        });
        if (isAlreadyLogged) {
            sHttpExecutor.submit(mGetUserFriendsTask);
        } else {
            sHttpExecutor.submit(mTokenVerify, mGetUserFriendsTask);
        }
    }

}
