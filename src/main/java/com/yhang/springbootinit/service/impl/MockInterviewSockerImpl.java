package com.yhang.springbootinit.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.yhang.springbootinit.common.ErrorCode;
import com.yhang.springbootinit.constant.CommonConstant;
import com.yhang.springbootinit.exception.BusinessException;
import com.yhang.springbootinit.exception.ThrowUtils;
import com.yhang.springbootinit.manager.AIManager;
import com.yhang.springbootinit.mapper.MockInterviewMapper;
import com.yhang.springbootinit.model.dto.mockinterview.MockInterviewAddRequest;
import com.yhang.springbootinit.model.dto.mockinterview.MockInterviewChatMessage;
import com.yhang.springbootinit.model.dto.mockinterview.MockInterviewEventRequest;
import com.yhang.springbootinit.model.dto.mockinterview.MockInterviewQueryRequest;
import com.yhang.springbootinit.model.entity.MockInterview;
import com.yhang.springbootinit.model.entity.User;
import com.yhang.springbootinit.model.enums.MockInterviewEventEnum;
import com.yhang.springbootinit.model.enums.MockInterviewStatusEnum;
import com.yhang.springbootinit.service.MockInterviewService;
import com.yhang.springbootinit.utils.SqlUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MockInterviewSockerImpl extends ServiceImpl<MockInterviewMapper, MockInterview> implements MockInterviewService {
    @Resource
    private AIManager aiManager;
    @Override
    public Long createMockInterview(MockInterviewAddRequest mockInterviewAddRequest, User loginUser) {
        if(mockInterviewAddRequest==null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if(loginUser==null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String workExperience = mockInterviewAddRequest.getWorkExperience();
        String jobPosition = mockInterviewAddRequest.getJobPosition();
        String difficulty = mockInterviewAddRequest.getDifficulty();
        ThrowUtils.throwIf(StrUtil.hasBlank(workExperience,jobPosition,difficulty),ErrorCode.PARAMS_ERROR);
        MockInterview mockInterview=new MockInterview();
        mockInterview.setWorkExperience(workExperience);
        mockInterview.setJobPosition(jobPosition);
        mockInterview.setDifficulty(difficulty);
        mockInterview.setUserId(loginUser.getId());
        mockInterview.setStatus(MockInterviewStatusEnum.TO_START.getValue());
        boolean save = save(mockInterview);
        ThrowUtils.throwIf(!save,ErrorCode.OPERATION_ERROR);
        return mockInterview.getId();
    }

    @Override
    public QueryWrapper<MockInterview> getQueryWrapper(MockInterviewQueryRequest mockInterviewQueryRequest) {
        QueryWrapper<MockInterview> queryWrapper = new QueryWrapper<>();
        if (mockInterviewQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = mockInterviewQueryRequest.getId();
        String workExperience = mockInterviewQueryRequest.getWorkExperience();
        String jobPosition = mockInterviewQueryRequest.getJobPosition();
        String difficulty = mockInterviewQueryRequest.getDifficulty();
        Integer status = mockInterviewQueryRequest.getStatus();
        Long userId = mockInterviewQueryRequest.getUserId();
        String sortField = mockInterviewQueryRequest.getSortField();
        String sortOrder = mockInterviewQueryRequest.getSortOrder();
        // 补充需要的查询条件
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.like(StringUtils.isNotBlank(workExperience), "workExperience", workExperience);
        queryWrapper.like(StringUtils.isNotBlank(jobPosition), "jobPosition", jobPosition);
        queryWrapper.like(StringUtils.isNotBlank(difficulty), "difficulty", difficulty);
        queryWrapper.eq(ObjectUtils.isNotEmpty(status), "status", status);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public String handleMockInterviewEvent(MockInterviewEventRequest mockInterviewEventRequest, User loginUser) {
        Long id=mockInterviewEventRequest.getId();
        if(id==null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        MockInterview mockInterview = getById(id);
        ThrowUtils.throwIf(mockInterview==null,ErrorCode.NOT_FOUND_ERROR);
        if(!mockInterview.getUserId().equals(loginUser.getId()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        String event=mockInterviewEventRequest.getEvent();
        MockInterviewEventEnum mockInterviewEventEnum = MockInterviewEventEnum.getEnumByValue(event);
        //接入ai
        String systemPrompt=String.format("你是一位严厉的程序员面试官，我是候选人，来应聘 {工作年限} 的 {工作岗位} 岗位，面试难度为 {面试难度}。请你向我依次提出问题（最多 20 个问题），我也会依次回复。在这期间请完全保持真人面试官的口吻，比如适当引导学员、或者表达出你对学员回答的态度。\n" +
                "必须满足如下要求：\n" +
                "1. 当学员回复 “开始” 时，你要正式开始面试\n" +
                "2. 当学员表示希望 “结束面试” 时，你要结束面试\n" +
                "3. 此外，当你觉得这场面试可以结束时（比如候选人回答结果较差、不满足工作年限的招聘需求、或者候选人态度不礼貌），必须主动提出面试结束，不用继续询问更多问题了。并且要在回复中包含字符串【面试结束】\n" +
                "4. 面试结束后，应该给出候选人整场面试的表现和总结。\n",mockInterview.getWorkExperience(),mockInterview.getJobPosition(),mockInterview.getDifficulty());


        switch (mockInterviewEventEnum)
        {
            case START:
                return handleStartMessage(id, systemPrompt);
            case CHAT:
                return handleCharMessage(mockInterviewEventRequest, id, mockInterview);
            case END:
                return handleEndMessage(id, mockInterview);
        }
        return "";
    }
    private String handleEndMessage(Long id, MockInterview mockInterview) {
        String historyMessage = mockInterview.getMessages();
        List<MockInterviewChatMessage> mockInterviewChatMessageList = JSONUtil.parseArray(historyMessage).toList(MockInterviewChatMessage.class);
        String userEndPrompt="结束";
        final ChatMessage userEndMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userEndPrompt).build();
        final List<ChatMessage> chatMessages = transformToChatMessage(mockInterviewChatMessageList);
        chatMessages.add(userEndMessage);
        String endResult = aiManager.doChat(chatMessages);
        final ChatMessage endAssistantMessage=ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(endResult).build();
        chatMessages.add(endAssistantMessage);
        List<MockInterviewChatMessage> mockInterviewChatMessagesEnd = transformFromChatMessage(chatMessages);
        String jsonStrEnd = JSONUtil.toJsonStr(mockInterviewChatMessagesEnd);
        MockInterview mockInterview2=new MockInterview();
        mockInterview2.setStatus(MockInterviewStatusEnum.ENDED.getValue());
        mockInterview2.setId(id);
        mockInterview2.setMessages(jsonStrEnd);
        boolean newSave=updateById(mockInterview2);
        ThrowUtils.throwIf(!newSave,ErrorCode.OPERATION_ERROR,"数据库保存失败");
        return endResult;
    }

    private String handleCharMessage(MockInterviewEventRequest mockInterviewEventRequest, Long id, MockInterview mockInterview) {
        String message= mockInterviewEventRequest.getMessage();
        String chatHistoryMessage = mockInterview.getMessages();
        List<MockInterviewChatMessage> mockInterviewChatMessageList = JSONUtil.parseArray(chatHistoryMessage).toList(MockInterviewChatMessage.class);
        final ChatMessage userEndMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(message).build();
        final List<ChatMessage> chatMessages = transformToChatMessage(mockInterviewChatMessageList);
        chatMessages.add(userEndMessage);
        String chatResult = aiManager.doChat(chatMessages);
        final ChatMessage endAssistantMessage=ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(chatResult).build();
        chatMessages.add(endAssistantMessage);
        List<MockInterviewChatMessage> mockInterviewChatMessagesEnd = transformFromChatMessage(chatMessages);
        String jsonStrEnd = JSONUtil.toJsonStr(mockInterviewChatMessagesEnd);
        MockInterview mockInterview2=new MockInterview();
        mockInterview2.setStatus(MockInterviewStatusEnum.ENDED.getValue());
        mockInterview2.setId(id);
        mockInterview2.setMessages(jsonStrEnd);
        if(chatResult.contains("[面试结束]"))
        {
            mockInterview2.setStatus(MockInterviewStatusEnum.ENDED.getValue());
        }
        boolean newSave=updateById(mockInterview2);
        ThrowUtils.throwIf(!newSave,ErrorCode.OPERATION_ERROR,"数据库保存失败");
        return chatResult;
    }

    private String handleStartMessage(Long id,  String systemPrompt) {
        MockInterview mockInterview1=new MockInterview();
        mockInterview1.setStatus(MockInterviewStatusEnum.IN_PROGRESS.getValue());
        mockInterview1.setId(id);
        String userPrompt="开始";
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        String result = aiManager.doChat(messages);
        final ChatMessage assistantMessage=ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(result).build();
        messages.add(assistantMessage);
        List<MockInterviewChatMessage> mockInterviewChatMessages = transformFromChatMessage(messages);
        String jsonStr = JSONUtil.toJsonStr(mockInterviewChatMessages);
        mockInterview1.setMessages(jsonStr);
        boolean save = updateById(mockInterview1);
        ThrowUtils.throwIf(!save,ErrorCode.OPERATION_ERROR,"数据库保存失败");
        return result;
    }

    private List<MockInterviewChatMessage> transformFromChatMessage(List<ChatMessage> chatMessageList)
    {
        return chatMessageList.stream().map(chatMessage->{
           MockInterviewChatMessage message=new MockInterviewChatMessage();
           message.setMessage((String) chatMessage.getContent());
           message.setRole(chatMessage.getRole().value());
           return message;
        }).collect(Collectors.toList());
    }

    private List<ChatMessage> transformToChatMessage(List<MockInterviewChatMessage> chatMessageList)
    {
        return chatMessageList.stream().map(chatMessage->{
            final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.valueOf(StringUtils.upperCase(chatMessage.getRole())
                    )).content(chatMessage.getMessage()).build();
            return systemMessage;
        }).collect(Collectors.toList());
    }
}
