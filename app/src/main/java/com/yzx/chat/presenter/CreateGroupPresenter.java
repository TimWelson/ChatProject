package com.yzx.chat.presenter;

import com.yzx.chat.bean.ContactBean;
import com.yzx.chat.contract.CreateGroupContract;

import java.util.List;

/**
 * Created by YZX on 2018年02月27日.
 * 优秀的代码是它自己最好的文档,当你考虑要添加一个注释时,问问自己:"如何能改进这段代码，以让它不需要注释？"
 */

public class CreateGroupPresenter implements CreateGroupContract.Presenter {

    private CreateGroupContract.View mCreateGroupView;


    @Override
    public void attachView(CreateGroupContract.View view) {
        mCreateGroupView = view;
    }

    @Override
    public void detachView() {
        mCreateGroupView = null;
    }

    @Override
    public void createGroup(String name, List<ContactBean> members) {

    }
}
