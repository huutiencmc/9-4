package com.example.hrms.biz.user.controller.rest;

import com.example.hrms.biz.user.model.User;
import com.example.hrms.biz.user.model.criteria.UserCriteria;
import com.example.hrms.biz.user.model.dto.UserDTO;
import com.example.hrms.biz.user.service.UserService;
import com.example.hrms.common.http.model.Result;
import com.example.hrms.common.http.model.ResultData;
import com.example.hrms.common.http.model.ResultPageData;
import com.example.hrms.enumation.RoleEnum;
import com.example.hrms.exception.InvalidPasswordException;
import com.example.hrms.security.SecurityUtils;
import com.example.hrms.utils.RequestUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "User API v1")
@RestController
@RequestMapping("/api/v1/users")
public class UserRestController {

    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserRestController(UserService userService) {
        this.userService = userService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Operation(summary = "List users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = UserDTO.Resp.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @GetMapping("")
    public ResultPageData<List<UserDTO.Resp>> list(UserCriteria criteria) {
        int total = userService.count(criteria);
        ResultPageData<List<UserDTO.Resp>> response = new ResultPageData<>(criteria, total);
        response.setResultData(total > 0 ? userService.list(criteria) : Collections.emptyList());
        return response;
    }


    @Operation(summary = "Get all users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    })
    @GetMapping("/all")
    public ResultData<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return new ResultData<>(Result.SUCCESS, "Users retrieved successfully", users);
    }

    @Operation(summary = "Login")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/login")
    public Result checkLogin(@RequestBody UserDTO.Req loginRequest, HttpSession session) {
        User user = userService.getUserByUsername(loginRequest.getUsername());
        if (user == null) {
            throw new UsernameNotFoundException("Username not found.");
        }

        boolean passwordMatches = passwordEncoder.matches(loginRequest.getPassword(), user.getPassword());
        if (!passwordMatches) {
            throw new InvalidPasswordException("Invalid password.");
        }

        RequestUtils.setSessionAttr(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        Authentication authentication = new PreAuthenticatedAuthenticationToken(
                user, null, SecurityUtils.getAuthorities(user.getRoleName()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        RequestUtils.session(false).setMaxInactiveInterval(1800);
        return new Result("Success", "Login successful.");
    }

    @PutMapping("/update/{username}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERVISOR')")
    public Result updateAccount(@PathVariable String username, @RequestBody UserDTO.UpdateReq userReq) {
        User existingUser = userService.getUserByUsername(username);
        if (existingUser == null) {
            return new Result("Error", "User not found.");
        }

        if (!existingUser.getUsername().equals(userReq.getUsername()) ||
                !existingUser.getEmail().equals(userReq.getEmail())) {
            return new Result("Error", "Username and Email cannot be updated.");
        }

        RoleEnum currentUserRole = userService.getCurrentUserRole();
        if (RoleEnum.SUPERVISOR.equals(currentUserRole)) {
            if (userReq.getDepartmentId() != null &&
                    !userReq.getDepartmentId().equals(existingUser.getDepartmentId())) {
                return new Result("Error", "Supervisors are not allowed to update Department.");
            }
            if (RoleEnum.ADMIN.equals(userReq.getRoleName())) {
                return new Result("Error", "Supervisors are not allowed to assign Admin role.");
            }
        }

        existingUser.setEmployeeName(userReq.getEmployeeName());
        if (userReq.getPassword() != null && !userReq.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(userReq.getPassword()));
        }
        existingUser.setDepartmentId(userReq.getDepartmentId());
        existingUser.setRoleName(userReq.getRoleName());
        existingUser.setSupervisor(userReq.isSupervisor());
        existingUser.setStatus(userReq.getStatus());

        userService.updateUser(existingUser);
        return new Result("Success", "Account updated successfully.");
    }

    @Operation(summary = "Delete user")
    @DeleteMapping("/{username}")
    public Result deleteUser(@PathVariable String username) {
        userService.deleteUser(username);
        return new Result("Success", "User deleted successfully.");
    }

    @Operation(summary = "Get user by username")
    @GetMapping("/getUserByUsername/{username}")
    public User getUserByUsername(@PathVariable String username) {
        return userService.getUserByUsername(username);
    }

    @Operation(summary = "Get current user role")
    @GetMapping("/getUserRole")
    public ResultData getUserRole() {
        String role = SecurityUtils.getCurrentUserRole();
        return new ResultData("Success", "Role retrieved successfully", role);
    }

    @Operation(summary = "Create account")
    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERVISOR')")
    public Result createAccount(@RequestBody UserDTO.Req userReq) {
        if (userService.checkUsernameExists(userReq.getUsername()) > 0 ||
                userService.isUsernameDuplicated(userReq.getUsername())) {
            return new Result("Conflict", "Username already exists.");
        }

        if (userReq.getUsername().length() > 50) {
            return new Result("Invalid request", "Username must be less than 50 characters.");
        }

        if (!isValidPassword(userReq.getPassword())) {
            return new Result("Invalid request",
                    "Password must be at least 10 characters long, including uppercase, lowercase, and a special character.");
        }

        User user = new User();
        user.setUsername(userReq.getUsername());
        user.setEmployeeName(userReq.getEmployeeName());
        user.setEmail(userReq.getUsername() + "@cmcglobal.com.vn");
        user.setPassword(passwordEncoder.encode(userReq.getPassword()));
        user.setDepartmentId(userReq.getDepartmentId());
        user.setRoleName(userReq.getRoleName());
        user.setSupervisor(userReq.isSupervisor());
        user.setStatus("Active");

        int result = userService.insertUser(user);
        return result > 0 ?
                new Result("Success", "Account created successfully.") :
                new Result("Error", "Failed to create account.");
    }

    private boolean isValidPassword(String password) {
        return password.length() >= 10 &&
                !password.equals(password.toLowerCase()) &&
                !password.equals(password.toUpperCase()) &&
                password.matches(".*[^a-zA-Z0-9].*");
    }

    @GetMapping("/check")
    public Result checkUsernameExists(@RequestParam String username) {
        int count = userService.checkUsernameExists(username);
        return new Result("Success", count > 0 ? "Username is already taken" : "Username is available");
    }

    @PutMapping("/change-password/{username}")
    public Result changePassword(@PathVariable String username, @RequestBody UserDTO.ChangePasswordReq req) {
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return new Result("Error", "User not found.");
        }

        if (!isValidPassword(req.getNewPassword())) {
            return new Result("Error", "Password must be at least 10 characters long and include uppercase, lowercase, and a special character.");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userService.updateUser(user);

        return new Result("Success", "Password changed successfully.");
    }

    @Operation(summary = "Change password (current user)")
    @PutMapping("/change-password")
    public Result changePassword(@RequestBody UserDTO.ChangePasswordReq request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return new Result("Error", "User not found.");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            return new Result("Error", "Old password is incorrect.");
        }

        if (!isValidPassword(request.getNewPassword())) {
            return new Result("Error", "New password must be at least 10 characters long and include uppercase, lowercase, and a special character.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userService.updateUser(user);
        return new Result("Success", "Password changed successfully.");
    }

    @Operation(summary = "Logout user")
    @PostMapping("/logout")
    public Result logout(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        session.invalidate();

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                cookie.setValue("");
                cookie.setPath("/");
                cookie.setMaxAge(0);
                response.addCookie(cookie);
            }
        }

        return new Result("Success", "Logged out successfully.");
    }
}
