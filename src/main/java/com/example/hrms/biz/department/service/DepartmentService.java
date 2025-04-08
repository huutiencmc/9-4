package com.example.hrms.biz.department.service;

import com.example.hrms.biz.department.model.Department;
import com.example.hrms.biz.department.model.criteria.DepartmentCriteria;
import com.example.hrms.biz.department.model.dto.DepartmentDTO;
import com.example.hrms.biz.department.repository.DepartmentMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DepartmentService {
    private final DepartmentMapper departmentMapper;

    public DepartmentService(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    public List<Department> findById(Long id) {
        List<Department> departments = departmentMapper.findById(id);
        if (departments.isEmpty()) {
            throw new RuntimeException("Department not found: " + id);
        }
        return departments;
    }

    public void insert(DepartmentDTO.Req req) {
        if (departmentMapper.countByName(req.getDepartmentName(), null) > 0) {
            throw new IllegalArgumentException("Department name already exists");
        }

        Department department = new Department();
        department.setDepartmentName(req.getDepartmentName());
        departmentMapper.insertDepartment(department);

        if (department.getDepartmentId() == null) {
            throw new RuntimeException("Failed to insert department");
        }
    }

    public void updateDepartment(Long id, DepartmentDTO.Req req) {
        if (findById(id).isEmpty()) {
            throw new RuntimeException("Department not found");
        }
        if (departmentMapper.countByName(req.getDepartmentName(), id) > 0) {
            throw new RuntimeException("Department name already exists");
        }
        Department department = req.toDepartment();
        department.setDepartmentId(id);
        departmentMapper.updateDepartment(department);
    }

    public void deleteDepartment(Long departmentId) {
        if (findById(departmentId).isEmpty()) {
            throw new RuntimeException("Department not found");
        }
        departmentMapper.deleteDepartment(departmentId);
    }


    public List<Department> listWithEmployeesAndRoles(DepartmentCriteria criteria) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isEmployee = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("EMPLOYEE"));

        if (isEmployee) {
            // Trả về tất cả các phòng ban cho employee nhưng chỉ có tên phòng ban
            return departmentMapper.findAllDepartments();  // Lấy tất cả phòng ban, chỉ có ID và tên
        } else {
            // Trả về phòng ban đầy đủ thông tin cho admin và supervisor
            return departmentMapper.findWithEmployeesAndRoles(criteria);  // Lấy đầy đủ thông tin về nhân viên và vai trò
        }
    }


}