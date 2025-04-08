package com.example.hrms.biz.department.model.criteria;

import com.example.hrms.common.http.criteria.Page;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentCriteria extends Page {
    private Long departmentId;
    private String departmentName;
    private String employeeName;
    private String roleName;

    public DepartmentCriteria() {}

    public DepartmentCriteria(Long departmentId, String departmentName, String roleName, String userName) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.employeeName = employeeName;
        this.roleName = roleName;
    }
}