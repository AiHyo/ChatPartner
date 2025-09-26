package com.aih.chatpartner.controller;

import com.aih.chatpartner.common.BaseResponse;
import com.aih.chatpartner.common.ResultUtils;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.aih.chatpartner.model.entity.AiRole;
import com.aih.chatpartner.model.entity.User;
import com.aih.chatpartner.service.AiRoleService;
import com.aih.chatpartner.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI角色接口
 *
 * @author AiHyo
 */
@RestController
@RequestMapping("/aiRole")
@Slf4j
public class AiRoleController {

    @Resource
    private AiRoleService aiRoleService;

    @Resource
    private UserService userService;

    /**
     * 获取系统角色列表
     *
     * @return
     */
    @GetMapping("/system")
    public BaseResponse<List<AiRole>> getSystemRoles() {
        List<AiRole> systemRoles = aiRoleService.getAvailableSystemRoles();
        return ResultUtils.success(systemRoles);
    }

    /**
     * 获取用户可用角色列表（系统角色 + 用户自定义角色）
     *
     * @param request
     * @return
     */
    @GetMapping("/available")
    public BaseResponse<List<AiRole>> getUserAvailableRoles(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        List<AiRole> availableRoles = aiRoleService.getUserAvailableRoles(loginUser.getId());
        return ResultUtils.success(availableRoles);
    }

    /**
     * 根据ID获取角色详情
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<AiRole> getRoleById(@RequestParam Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        AiRole aiRole = aiRoleService.getRoleById(id);
        if (aiRole == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "角色不存在");
        }
        return ResultUtils.success(aiRole);
    }
}
