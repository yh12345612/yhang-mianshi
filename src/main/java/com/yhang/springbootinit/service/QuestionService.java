package com.yhang.springbootinit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yhang.springbootinit.common.BaseResponse;
import com.yhang.springbootinit.model.dto.question.QuestionEsDTO;
import com.yhang.springbootinit.model.dto.question.QuestionQueryRequest;
import com.yhang.springbootinit.model.entity.Question;
import com.yhang.springbootinit.model.entity.QuestionBankQuestion;
import com.yhang.springbootinit.model.entity.User;
import com.yhang.springbootinit.model.vo.QuestionVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 题目服务
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
public interface QuestionService extends IService<Question> {

    /**
     * 校验数据
     *
     * @param question
     * @param add 对创建的数据进行校验
     */
    void validQuestion(Question question, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);
    
    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    QuestionVO getQuestionVO(Question question, HttpServletRequest request);

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request);

    Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest);

    /**
     * 从 ES 查询题目
     *
     * @param questionQueryRequest
     * @return
     */
    Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest);
    void batchDeleteQuestions(List<Long> questionIdList);
    void batchAddQuestionsToBank(List<Long> questionIdList, Long questionBankId, User loginUser);
    void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions);

    void batchRemoveQuestionsFromBank(List<Long> questionIdList, Long questionBankId);

    boolean aiGenerateQuestion(String questionType,int count,User user);
}
