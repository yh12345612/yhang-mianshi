package com.yhang.springbootinit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yhang.springbootinit.model.dto.mockinterview.MockInterviewAddRequest;
import com.yhang.springbootinit.model.dto.mockinterview.MockInterviewEventRequest;
import com.yhang.springbootinit.model.dto.mockinterview.MockInterviewQueryRequest;
import com.yhang.springbootinit.model.entity.MockInterview;
import com.yhang.springbootinit.model.entity.User;
import org.springframework.stereotype.Service;


public interface MockInterviewService extends IService<MockInterview> {

    /**
     * 创建模拟面试
     *
     * @param mockInterviewAddRequest
     * @param loginUser
     * @return
     */
    Long createMockInterview(MockInterviewAddRequest mockInterviewAddRequest, User loginUser);

    /**
     * 构造查询条件
     *
     * @param mockInterviewQueryRequest
     * @return
     */
    QueryWrapper<MockInterview> getQueryWrapper(MockInterviewQueryRequest mockInterviewQueryRequest);

    /**
     * 处理模拟面试事件
     * @param mockInterviewEventRequest
     * @param loginUser
     * @return AI 给出的回复
     */
    String handleMockInterviewEvent(MockInterviewEventRequest mockInterviewEventRequest, User loginUser);
}