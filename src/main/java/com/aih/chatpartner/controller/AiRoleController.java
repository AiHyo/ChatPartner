package com.aih.chatpartner.controller;

import com.aih.chatpartner.common.BaseResponse;
import com.aih.chatpartner.common.PageResponse;
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
     * 角色搜索与筛选
     */
    @GetMapping("/roles")
    public BaseResponse<List<AiRole>> searchRoles(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false, defaultValue = "hot") String sort,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "12") Integer size,
            @RequestParam(required = false, defaultValue = "false") Boolean onlyNotFriend,
            HttpServletRequest request) {
        Long userId = null;
        try {
            User loginUser = userService.getLoginUser(request);
            userId = loginUser.getId();
        } catch (Exception ignored) {}
        List<AiRole> list = aiRoleService.searchRoles(q, tag, sort, page, size, userId, onlyNotFriend);
        return ResultUtils.success(list);
    }

    /**
     * 角色分页（带总数）
     */
    @GetMapping("/roles/page")
    public BaseResponse<PageResponse<AiRole>> searchRolesPage(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false, defaultValue = "hot") String sort,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "12") Integer size,
            @RequestParam(required = false, defaultValue = "false") Boolean onlyNotFriend,
            HttpServletRequest request) {
        Long userId = null;
        try {
            User loginUser = userService.getLoginUser(request);
            userId = loginUser.getId();
        } catch (Exception ignored) {}
        PageResponse<AiRole> resp = aiRoleService.searchRolesPage(q, tag, sort, page, size, userId, onlyNotFriend);
        return ResultUtils.success(resp);
    }

    /**
     * 标签列表
     */
    @GetMapping("/tags")
    public BaseResponse<List<String>> listTags() {
        return ResultUtils.success(aiRoleService.listAllTags());
    }

    /**
     * 点赞角色
     */
    @PostMapping("/{roleId}/like")
    public BaseResponse<Boolean> likeRole(@PathVariable Long roleId) {
        if (roleId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(aiRoleService.likeRole(roleId));
    }

    /**
     * 点赞切换（需要登录）
     */
    @PostMapping("/{roleId}/toggle-like")
    public BaseResponse<Boolean> toggleLike(@PathVariable Long roleId, HttpServletRequest request) {
        if (roleId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean liked = aiRoleService.toggleLike(roleId, loginUser.getId());
        return ResultUtils.success(liked);
    }

    /**
     * 获取当前用户已点赞的角色ID列表
     */
    @GetMapping("/liked-ids")
    public BaseResponse<List<Long>> likedRoleIds(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(aiRoleService.listLikedRoleIds(loginUser.getId()));
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
