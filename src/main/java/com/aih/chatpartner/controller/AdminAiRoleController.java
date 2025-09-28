package com.aih.chatpartner.controller;

import com.aih.chatpartner.annotation.AuthCheck;
import com.aih.chatpartner.common.BaseResponse;
import com.aih.chatpartner.common.PageResponse;
import com.aih.chatpartner.common.ResultUtils;
import com.aih.chatpartner.constant.UserConstant;
import com.aih.chatpartner.model.dto.admin.AdminRoleUpsertRequest;
import com.aih.chatpartner.model.dto.common.IdBatchRequest;
import com.aih.chatpartner.model.entity.AiRole;
import com.aih.chatpartner.model.entity.User;
import com.aih.chatpartner.service.AiRoleService;
import com.aih.chatpartner.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/aiRole")
public class AdminAiRoleController {

    @Resource
    private AiRoleService aiRoleService;
    @Resource
    private UserService userService;

    /**
     * 分页查询
     */
    @GetMapping("/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PageResponse<AiRole>> page(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false, defaultValue = "new") String sort,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "12") Integer size,
            @RequestParam(required = false) Integer isActive,
            @RequestParam(required = false) Integer isSystem
    ) {
        PageResponse<AiRole> resp = aiRoleService.adminPage(q, tag, sort, page, size, isActive, isSystem);
        return ResultUtils.success(resp);
    }

    /**
     * 新建角色
     */
    @PostMapping("/create")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> create(@RequestBody AdminRoleUpsertRequest req, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long id = aiRoleService.adminCreate(req, loginUser.getId());
        return ResultUtils.success(id);
    }

    /**
     * 更新角色
     */
    @PatchMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> update(@RequestBody AdminRoleUpsertRequest req) {
        return ResultUtils.success(aiRoleService.adminUpdate(req));
    }

    /**
     * 修改启用状态
     */
    @PatchMapping("/{id}/status")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> changeStatus(@PathVariable Long id, @RequestParam Integer isActive) {
        return ResultUtils.success(aiRoleService.adminChangeStatus(id, isActive));
    }

    /**
     * 删除（逻辑删除）
     */
    @DeleteMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> delete(@PathVariable Long id) {
        return ResultUtils.success(aiRoleService.adminDelete(id));
    }

    /**
     * 批量删除（逻辑删除）
     */
    @PostMapping("/deleteBatch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteBatch(@RequestBody IdBatchRequest req) {
        return ResultUtils.success(aiRoleService.adminDeleteBatch(req.getIds()));
    }
}
