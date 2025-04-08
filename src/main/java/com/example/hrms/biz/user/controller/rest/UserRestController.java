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
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
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
            @ApiResponse(responseCode = "200", description = "Get success",
                    content = { @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = UserDTO.Resp.class))) }),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content) })
    @GetMapping("")
    public ResultPageData<List<UserDTO.Resp>> list(UserCriteria criteria) {
        int total = userService.count(criteria);
        ResultPageData<List<UserDTO.Resp>> response = new ResultPageData<>(criteria, total);
        if (total > 0) {
            response.setResultData(userService.list(criteria));
        } else {
            response.setResultData(Collections.emptyList());
        }
        return response;
    }
    @GetMapping("/privileges")
    public ResultData<String> getUserPrivileges() throws JsonProcessingException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN"));
        Map<String, Object> response = new HashMap<>();
        response.put("isAdmin", isAdmin);
        response.put("username", currentUsername);
        String jsonResponse = new ObjectMapper().writeValueAsString(response);
        return new ResultData<>(jsonResponse);
    }
    @Operation(summary = "Get all users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Get success",
                    content = @Content) })
    @GetMapping("/all")
    public ResultData<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return new ResultData<>(Result.SUCCESS, "Users retrieved successfully", users);
    }

    @Operation(summary = "Check username and password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login success",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content)
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

        // Store security context case
        RequestUtils.setSessionAttr(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        Authentication authentication = new PreAuthenticatedAuthenticationToken(user, null, SecurityUtils.getAuthorities(user.getRoleName()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Set session valid within 30 minutes
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

        // Không cho phép cập nhật Username và Email
        if (!existingUser.getUsername().equals(userReq.getUsername()) ||
                !existingUser.getEmail().equals(userReq.getEmail())) {
            return new Result("Error", "Username and Email cannot be updated.");
        }

        // Lấy role của người đang đăng nhập
        RoleEnum currentUserRole = userService.getCurrentUserRole();

        // Nếu người dùng là Supervisor, cấm cập nhật Department và Role là Admin
        if (RoleEnum.SUPERVISOR.equals(currentUserRole)) {
            if (userReq.getDepartmentId() != null &&
                    !userReq.getDepartmentId().equals(existingUser.getDepartmentId())) {
                return new Result("Error", "Supervisors are not allowed to update Department.");
            }
            if (RoleEnum.ADMIN.equals(userReq.getRoleName())) {
                return new Result("Error", "Supervisors are not allowed to assign Admin role.");
            }
        }

        // Cập nhật thông tin hợp lệ
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deleted",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class)) }),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content) })
    @DeleteMapping("/{username}")
    public Result deleteUser(@PathVariable String username) {
        userService.deleteUser(username);
        return new Result("Success", "User deleted successfully.");
    }

    @Operation(summary = "Get user by username")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content)
    })
    @GetMapping("/getUserByUsername/{username}")
    public User getUserByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        return user != null ? user : null;
    }

    @Operation(summary = "Get user by userRole")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content)
    })
    @GetMapping("/getUserRole")
    public ResultData getUserRole() {
        String role = SecurityUtils.getCurrentUserRole() ;
        return new ResultData("Success", "Get role", role);
    }

    @Operation(summary = "Create account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Account created",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class)) }),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflict",
                    content = @Content) })
    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERVISOR')")
    public Result createAccount(@RequestBody UserDTO.Req userReq) {
        // Kiểm tra username đã tồn tại chưa
        if (userService.checkUsernameExists(userReq.getUsername()) > 0) {
            return new Result("Conflict", "Username already exists.");
        }
        if (userService.isUsernameDuplicated(userReq.getUsername())) {
            return new Result("Conflict", "Username already exists.");
        }

        // Validate dữ liệu
        if (userReq.getUsername().length() > 50) {
            return new Result("Invalid request", "Username must be less than 50 characters.");
        }

        if (!isValidPassword(userReq.getPassword())) {
            return new Result("Invalid request",
                    "Password must be at least 10 characters with uppercase, lowercase and special character.");
        }

        // Tạo user mới
        User user = new User();
        user.setUsername(userReq.getUsername());
        user.setEmployeeName(userReq.getEmployeeName());
        user.setEmail(userReq.getUsername() + "@cmcglobal.com.vn");
        user.setPassword(passwordEncoder.encode(userReq.getPassword()));
        user.setDepartmentId(userReq.getDepartmentId());
        user.setRoleName(userReq.getRoleName());
        user.setSupervisor(userReq.isSupervisor());
        user.setStatus("Active"); // Mặc định Active khi tạo mới

        int result = userService.insertUser(user);
        if (result > 0) {
            return new Result("Success", "Account created successfully.");
        } else {
            return new Result("Error", "Failed to create account.");
        }
    }
    private boolean isValidPassword(String password) {
        if (password.length() < 10) {
            return false;
        }
        boolean hasUppercase = !password.equals(password.toLowerCase());
        boolean hasLowercase = !password.equals(password.toUpperCase());
        boolean hasSpecial = password.matches(".*[^a-zA-Z0-9].*");
        return hasUppercase && hasLowercase && hasSpecial;
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

        if (!isValidlPassword(req.getNewPassword())) {
            return new Result("Error", "Mật khẩu phải lớn hơn 10 ký tự, bao gồm cả ký tự hoa thường và có ít nhất một ký tự đặc biệt.");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userService.updateUser(user);

        return new Result("Success", "Password changed successfully.");
    }

    private boolean isValidlPassword(String password) {
        return password.length() >= 10 &&
                password.matches(".*[A-Z].*") &&
                password.matches(".*[a-z].*") &&
                password.matches(".*[^a-zA-Z0-9].*");
    }

    @Operation(summary = "Change password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not logged in",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Old password is incorrect",
                    content = @Content)
    })
    @PutMapping("/change-password")
    public Result changePassword(@RequestBody UserDTO.ChangePasswordReq request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return new Result("Error", "User not found.");
        }
        // Kiểm tra mật khẩu cũ có đúng không
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            return new Result("Error", "Old password is incorrect.");
        }
        // Kiểm tra mật khẩu mới có hợp lệ không
        if (!isValidPassword(request.getNewPassword())) {
            return new Result("Error", "New password must be at least 10 characters long and include uppercase, lowercase, and special character.");
        }
        // Mã hóa và cập nhật mật khẩu mới
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userService.updateUser(user);
        return new Result("Success", "Password changed successfully.");
    }

    @Operation(summary = "Logout user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logged out successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User is not logged in",
                    content = @Content)
    })
    @PostMapping("/logout")
    public Result logout(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        // Invalidate the session
        session.invalidate();

        // Remove cookies
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