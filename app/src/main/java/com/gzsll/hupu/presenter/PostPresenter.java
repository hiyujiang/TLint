package com.gzsll.hupu.presenter;

import com.gzsll.hupu.Constants;
import com.gzsll.hupu.api.forum.ForumApi;
import com.gzsll.hupu.bean.BaseData;
import com.gzsll.hupu.bean.PermissionData;
import com.gzsll.hupu.bean.UploadData;
import com.gzsll.hupu.bean.UploadInfo;
import com.gzsll.hupu.components.storage.UserStorage;
import com.gzsll.hupu.helper.ConfigHelper;
import com.gzsll.hupu.helper.FileHelper;
import com.gzsll.hupu.helper.SecurityHelper;
import com.gzsll.hupu.helper.SettingPrefHelper;
import com.gzsll.hupu.helper.ToastHelper;
import com.gzsll.hupu.ui.view.PostView;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by sll on 2016/3/9.
 */
public class PostPresenter extends Presenter<PostView> {

    @Inject
    UserStorage mUserStorage;
    @Inject
    ForumApi mForumApi;
    @Inject
    SecurityHelper mSecurityHelper;
    @Inject
    FileHelper mFileHelper;
    @Inject
    ConfigHelper mConfigHelper;
    @Inject
    ToastHelper mToastHelper;
    @Inject
    SettingPrefHelper mSettingPrefHelper;


    @Inject
    @Singleton
    public PostPresenter() {
    }


    private ArrayList<String> paths = new ArrayList<>();
    int uploadCount = 0;


    public void checkPermission(int type, String fid, String tid) {
        mForumApi.checkPermission(fid, tid, type == Constants.TYPE_POST ? "threadPublish" : "threadReply").subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<PermissionData>() {
            @Override
            public void call(PermissionData permissionData) {
                if (permissionData != null) {
                    if (permissionData.error != null) {
                        view.renderError(permissionData.error);
                    } else if (mSettingPrefHelper.isNeedExam()) {
                        view.renderExam(permissionData.exam);
                    }
                }
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                throwable.printStackTrace();
            }
        });
    }


    public void parse(ArrayList<String> paths) {
        this.paths = paths;
    }

    public void comment(final String tid, final String fid, final String pid, final String content) {
        view.showLoading();
        if (paths != null && !paths.isEmpty()) {
            final List<String> images = new ArrayList<>();
            Observable.from(paths).flatMap(new Func1<String, Observable<UploadData>>() {
                @Override
                public Observable<UploadData> call(String s) {
                    return mForumApi.upload(s);
                }
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<UploadData>() {
                @Override
                public void onStart() {
                    uploadCount = 0;
                    images.clear();
                }

                @Override
                public void onCompleted() {
                    uploadCount++;
                    if (uploadCount == paths.size()) {
                        addReply(tid, fid, pid, content, images);
                    }
                }

                @Override
                public void onError(Throwable e) {
                    uploadCount++;
                    if (uploadCount == paths.size()) {
                        addReply(tid, fid, pid, content, images);
                    }
                }

                @Override
                public void onNext(UploadData uploadData) {
                    if (uploadData != null) {
                        for (UploadInfo info : uploadData.files) {
                            images.add(info.requestUrl);
                        }
                    }
                }
            });

        } else {
            addReply(tid, fid, pid, content, null);
        }
    }


    private void addReply(String tid, String fid, String pid, String content, List<String> imgs) {
        StringBuilder buffer = new StringBuilder(content);
        if (imgs != null) {
            for (String url : imgs) {
                buffer.append("<br><br><img src=\"").append(url).append("\"><br><br>");
            }
        }
        System.out.println("buffer:" + buffer.toString());
        mForumApi.addReplyByApp(tid, fid, pid, buffer.toString()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<BaseData>() {
            @Override
            public void call(BaseData result) {
                view.hideLoading();
                if (result != null) {
                    if (result.error != null) {
                        mToastHelper.showToast(result.error.text);
                    } else if (result.status == 200) {
                        mToastHelper.showToast("发送成功~");
                        view.postSuccess();
                    }
                } else {
                    mToastHelper.showToast("您的网络有问题，请检查后重试");
                }
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                view.hideLoading();
                mToastHelper.showToast("您的网络有问题，请检查后重试");
            }
        });
    }


    public void post(final String fid, final String content, final String title) {
        view.showLoading();
        if (paths != null && !paths.isEmpty()) {
            final List<String> images = new ArrayList<>();
            Observable.from(paths).flatMap(new Func1<String, Observable<UploadData>>() {
                @Override
                public Observable<UploadData> call(String s) {
                    return mForumApi.upload(s);
                }
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<UploadData>() {
                @Override
                public void onStart() {
                    uploadCount = 0;
                    images.clear();
                }

                @Override
                public void onCompleted() {
                    uploadCount++;
                    if (uploadCount == paths.size()) {
                        addPost(fid, content, title, images);
                    }
                }

                @Override
                public void onError(Throwable e) {
                    uploadCount++;
                    if (uploadCount == paths.size()) {
                        addPost(fid, content, title, images);
                    }
                }

                @Override
                public void onNext(UploadData uploadData) {
                    if (uploadData != null) {
                        for (UploadInfo info : uploadData.files) {
                            images.add(info.requestUrl);
                        }
                    }
                }
            });

        } else {
            addPost(fid, content, title, null);
        }
    }

    private void addPost(String fid, String content, String title, List<String> imgs) {
        StringBuilder buffer = new StringBuilder(content);
        if (imgs != null) {
            for (String url : imgs) {
                buffer.append("<br><br><img src=\"").append(url).append("\"><br><br>");
            }
        }
        mForumApi.addThread(title, buffer.toString(), fid).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<BaseData>() {
            @Override
            public void call(BaseData result) {
                view.hideLoading();
                if (result != null) {
                    if (result.error != null) {
                        mToastHelper.showToast(result.error.text);
                    } else if (result.status == 200) {
                        mToastHelper.showToast("发送成功~");
                        view.postSuccess();
                    }
                } else {
                    mToastHelper.showToast("您的网络有问题，请检查后重试");
                }
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                view.hideLoading();
                mToastHelper.showToast("您的网络有问题，请检查后重试");
            }
        });
    }


    @Override
    public void detachView() {
        paths.clear();
    }
}
