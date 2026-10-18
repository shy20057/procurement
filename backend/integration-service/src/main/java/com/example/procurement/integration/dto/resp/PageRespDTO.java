package com.example.procurement.integration.dto.resp;
import java.util.List;
public record PageRespDTO<T>(List<T> list, long total, int page, int size) {}
