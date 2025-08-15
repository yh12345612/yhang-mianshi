package com.yhang.springbootinit.manager;

import cn.hutool.core.collection.CollUtil;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import com.yhang.springbootinit.common.ErrorCode;
import com.yhang.springbootinit.exception.BusinessException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AIManager {
    @Resource
    private ArkService arkService;
    public String doChat(String systemPrompt,String userPrompt)
    {
        String default_model="deepseek-v3-250324";
        return doChat(systemPrompt,userPrompt,default_model);
    }
    public String doChat(String systemPrompt,String userPrompt,String model)
    {

        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        return doChat(messages,model);
    }

    public String doChat(List<ChatMessage> messages,String model)
    {
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                // 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
//                .model("deepseek-v3-250324")
                .model(model)
                .messages(messages)
                .build();

        List<ChatCompletionChoice> choices = arkService.createChatCompletion(chatCompletionRequest).getChoices();
//        arkService.shutdownExecutor();
        if(CollUtil.isNotEmpty(choices)) {
            return (String) choices.get(0).getMessage().getContent();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI调用失败");
    }
    public String doChat(List<ChatMessage> messages)
    {
        String default_model="deepseek-v3-250324";
        return doChat(messages,default_model);
    }
}
