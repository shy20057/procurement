package com.example.procurement.sourcing.service.project;

import com.example.procurement.sourcing.dto.req.project.CreateSourcingProjectRequest;
import com.example.procurement.sourcing.dto.resp.project.SourcingProjectResponse;

import java.util.List;

public interface SourcingProjectService {
    List<SourcingProjectResponse> list(String status, String keyword);

    SourcingProjectResponse get(Long id);

    SourcingProjectResponse create(CreateSourcingProjectRequest request);

    SourcingProjectResponse submit(Long id);

    SourcingProjectResponse approve(Long id);
}
