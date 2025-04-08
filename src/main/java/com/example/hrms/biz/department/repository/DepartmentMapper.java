package com.example.hrms.biz.department.repository;

import com.example.hrms.biz.department.model.Department;
import com.example.hrms.biz.department.model.criteria.DepartmentCriteria;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DepartmentMapper {

    @Insert("INSERT INTO Departments (department_name) VALUES (#{departmentName})")
    @Options(useGeneratedKeys = true, keyProperty = "departmentId")
    void insertDepartment(Department department);

    // Lấy danh sách phòng ban (Chỉ tên phòng ban)
    @Select("SELECT department_id AS departmentId, department_name AS departmentName FROM Departments ORDER BY department_name ASC")
    List<Department> findAllDepartments();

    // Lấy danh sách phòng ban kèm nhân viên và vai trò
    @Select("""
    SELECT 
        d.department_id AS departmentId, 
        d.department_name AS departmentName, 
        u.employee_name AS employeeName, 
        r.role_name AS roleName 
    FROM Users u 
    JOIN Departments d ON u.department_id = d.department_id 
    JOIN Roles r ON u.role_name = r.role_name 
    WHERE 
        (#{departmentId} IS NULL OR d.department_id = #{departmentId}) 
        AND (#{departmentName} IS NULL OR d.department_name LIKE CONCAT('%', #{departmentName}, '%')) 
        AND (#{employeeName} IS NULL OR u.employee_name LIKE CONCAT('%', #{employeeName}, '%')) 
        AND (#{roleName} IS NULL OR r.role_name LIKE CONCAT('%', #{roleName}, '%')) 
    ORDER BY d.department_name ASC, r.role_name ASC
""")
    List<Department> findWithEmployeesAndRoles(DepartmentCriteria criteria);


    // Tìm phòng ban theo ID
    @Select("""
        SELECT 
            d.department_id AS departmentId, 
            d.department_name AS departmentName, 
            r.role_name AS roleName, 
            u.employee_name AS employeeName 
        FROM Departments d 
        JOIN Users u ON d.department_id = u.department_id 
        JOIN Roles r ON u.role_name = r.role_name 
        WHERE d.department_id = #{id} 
        ORDER BY r.role_name ASC
    """)
    List<Department> findById(Long id);

    // Đếm số lượng phòng ban theo tên (loại trừ ID hiện tại)
    @Select("SELECT COUNT(*) FROM Departments WHERE department_name = #{departmentName} AND (#{departmentId} IS NULL OR department_id != #{departmentId})")
    int countByName(@Param("departmentName") String departmentName, @Param("departmentId") Long departmentId);

    @Update("UPDATE Departments SET department_name = #{departmentName} WHERE department_id = #{departmentId}")
    void updateDepartment(Department department);

    @Delete("DELETE FROM Departments WHERE department_id = #{departmentId}")
    void deleteDepartment(Long departmentId);
}