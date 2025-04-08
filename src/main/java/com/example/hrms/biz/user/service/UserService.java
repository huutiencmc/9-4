package com.example.hrms.biz.user.service;

import com.example.hrms.biz.user.model.User;
import com.example.hrms.biz.user.model.criteria.UserCriteria;
import com.example.hrms.biz.user.model.dto.UserDTO;
import com.example.hrms.enumation.RoleEnum;
import com.example.hrms.biz.user.repository.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public boolean checkUsernamePassword(String username, String rawPassword) {
        String encodedPassword = userMapper.getPasswordByUsername(username);
        return encodedPassword != null && passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public User getUserByUsername(String username) {
        return userMapper.getUserByUsername(username);
    }

    public List<User> getAllUsers() {
        return userMapper.getAllUsers();
    }

    public List<User> searchUsers(UserCriteria criteria) {
        return userMapper.searchUsers(
                criteria.getDepartmentIds(),
                criteria.getRoles()
        );
    }

    @Transactional
    public int insertUser(User user) {
        // Mã hóa password trước khi lưu
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userMapper.insertUser(user);
    }

    @Transactional
    public int updateUser(User user) {
        // Nếu có thay đổi password thì mã hóa
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        return userMapper.updateUser(user);
    }

    @Transactional
    public int deleteUser(String username) {
        return userMapper.deleteUser(username);
    }

    public int count(UserCriteria criteria) {
        return userMapper.count(criteria);
    }

    public List<UserDTO.Resp> list(UserCriteria criteria) {
        return userMapper.searchUsers(criteria.getDepartmentIds(), criteria.getRoles())
                .stream()
                .map(this::convertToUserResp)
                .collect(Collectors.toList());
    }


    public boolean isUsernameDuplicated(String username) {
        return checkUsernameExists(username) > 0;
    }

    public RoleEnum getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(role -> {
                    try {
                        return RoleEnum.valueOf(role.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private UserDTO.Resp convertToUserResp(User user) {
        UserDTO.Resp resp = new UserDTO.Resp();
        resp.setUsername(user.getUsername());
        resp.setEmployeeName(user.getEmployeeName());
        resp.setEmail(user.getEmail());
        resp.setDepartmentId(user.getDepartmentId());
        resp.setDepartmentName(user.getDepartmentName());
        resp.setRoleName(user.getRoleName());
        resp.setIsSupervisor(user.isSupervisor());
        resp.setStatus(user.getStatus());
        return resp;
    }

    public boolean isValidPassword(String password) {
        return password != null &&
                password.length() >= 10 &&
                !password.equals(password.toLowerCase()) &&
                !password.equals(password.toUpperCase()) &&
                password.matches(".*[^a-zA-Z0-9].*");
    }
    public int checkUsernameExists(String username) {
        return userMapper.checkUsernameExists(username);
    }
}