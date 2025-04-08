package com.example.hrms.biz.department.controller;

import com.example.hrms.biz.department.model.Department;
import com.example.hrms.biz.department.model.criteria.DepartmentCriteria;
import com.example.hrms.biz.department.service.DepartmentService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequestMapping("/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }
    @GetMapping("")
    public String openDepartmentView(Model model) {
        // Lấy tất cả phòng ban
        List<Department> departments = departmentService.listWithEmployeesAndRoles(new DepartmentCriteria());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdminOrSupervisor = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN") || auth.getAuthority().equals("SUPERVISOR"));

        model.addAttribute("departments", departments);
        model.addAttribute("isAdminOrSupervisor", isAdminOrSupervisor);
        return "department"; // Trả về view 'department.html'
    }


}
