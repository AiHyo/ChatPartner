package com.aih.chatpartner.controller;

import com.aih.chatpartner.annotation.AuthCheck;
import com.aih.chatpartner.common.BaseResponse;
import com.aih.chatpartner.common.ResultUtils;
import com.aih.chatpartner.constant.UserConstant;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.aih.chatpartner.model.entity.User;
import com.aih.chatpartner.model.vo.FriendRoleVO;
import com.aih.chatpartner.service.FriendService;
import com.aih.chatpartner.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/friend")
public class FriendController {

    @Resource
    private FriendService friendService;
    @Resource
    private UserService userService;

    @GetMapping("/list")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<List<FriendRoleVO>> list(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(friendService.listFriends(loginUser.getId()));
        
    }

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> add(@RequestParam Long roleId, HttpServletRequest request) {
        if (roleId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(friendService.addFriend(loginUser.getId(), roleId));
    }

    @DeleteMapping("/{roleId}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> remove(@PathVariable Long roleId, HttpServletRequest request) {
        if (roleId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(friendService.removeFriend(loginUser.getId(), roleId));
    }

    @PatchMapping("/{roleId}/pin")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> pin(
            @PathVariable Long roleId,
            @RequestParam boolean pinned,
            @RequestParam(required = false) Integer pinOrder,
            HttpServletRequest request) {
        if (roleId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(friendService.updatePin(loginUser.getId(), roleId, pinned, pinOrder));
    }
}
