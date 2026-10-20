package com.orders.order_management.core.service;

import com.orders.order_management.core.dto.MultiOutletMonitoringResponse;
import com.orders.order_management.core.dto.OutletMonitoringDto;
import com.orders.order_management.core.entity.Outlet;
import com.orders.order_management.core.repository.OutletMonitoringProjection;
import com.orders.order_management.core.repository.OutletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutletService {

    private final OutletRepository outletRepository;

    public List<Outlet> getAllOutlets() {
        return outletRepository.findAll();
    }

    public List<Outlet> getActiveOutlets() {
        return getActiveOutlets(null, 50);
    }

    public List<Outlet> getActiveOutlets(String search, Integer limit) {
        int max = (limit != null && limit > 0) ? Math.min(limit, 200) : 50;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, max);
        String searchParam = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return outletRepository.searchActiveOutlets(searchParam, pageable);
    }

    @Transactional
    public Outlet createOutlet(Outlet outlet) {
        return outletRepository.save(outlet);
    }

    @Transactional
    public Outlet updateOutlet(String id, Outlet outletDetails) {
        Outlet outlet = outletRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Outlet not found"));
        outlet.setName(outletDetails.getName());
        outlet.setCode(outletDetails.getCode());
        outlet.setAddress(outletDetails.getAddress());
        outlet.setContactNumber(outletDetails.getContactNumber());
        outlet.setActive(outletDetails.isActive());
        return outletRepository.save(outlet);
    }

    @Transactional
    public void deleteOutlet(String id) {
        Outlet outlet = outletRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Outlet not found"));
        outletRepository.delete(outlet);
    }

    public MultiOutletMonitoringResponse getMonitoringSummary() {
        return getMonitoringSummary(null, null, null, null);
    }

    public MultiOutletMonitoringResponse getMonitoringSummary(Integer page, Integer size, String search, String status) {
        Instant startOfDay = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<OutletMonitoringProjection> rawList = outletRepository.getMonitoringSummary(startOfDay);

        BigDecimal totalSales = BigDecimal.ZERO;
        long totalOrders = 0;
        long totalActiveOrders = 0;
        long totalOccupiedTables = 0;
        long totalTables = 0;
        int activeOutletsCount = 0;

        for (OutletMonitoringProjection p : rawList) {
            BigDecimal sales = p.getTodaySales() != null ? p.getTodaySales() : BigDecimal.ZERO;
            long orders = p.getTodayOrders() != null ? p.getTodayOrders() : 0;
            long activeOrders = p.getActiveOrders() != null ? p.getActiveOrders() : 0;
            long occupied = p.getOccupiedTables() != null ? p.getOccupiedTables() : 0;
            long tables = p.getTotalTables() != null ? p.getTotalTables() : 0;
            boolean active = Boolean.TRUE.equals(p.getIsActive());

            if (active) {
                activeOutletsCount++;
            }

            totalSales = totalSales.add(sales);
            totalOrders += orders;
            totalActiveOrders += activeOrders;
            totalOccupiedTables += occupied;
            totalTables += tables;
        }

        List<OutletMonitoringDto> dtos = new ArrayList<>();
        int pageNumber = 0;
        int pageSize = rawList.size();
        long totalElements = rawList.size();
        int totalPages = 1;
        boolean isLast = true;

        if (page != null && size != null && size > 0) {
            Boolean activeFilter = null;
            if ("ACTIVE".equalsIgnoreCase(status)) {
                activeFilter = true;
            } else if ("INACTIVE".equalsIgnoreCase(status)) {
                activeFilter = false;
            }

            String searchParam = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
            org.springframework.data.domain.Page<OutletMonitoringProjection> pageResult = 
                    outletRepository.searchMonitoringSummary(startOfDay, searchParam, activeFilter, pageable);

            pageNumber = pageResult.getNumber();
            pageSize = pageResult.getSize();
            totalElements = pageResult.getTotalElements();
            totalPages = pageResult.getTotalPages();
            isLast = pageResult.isLast();

            for (OutletMonitoringProjection p : pageResult.getContent()) {
                dtos.add(mapToDto(p));
            }
        } else {
            for (OutletMonitoringProjection p : rawList) {
                dtos.add(mapToDto(p));
            }
        }

        return MultiOutletMonitoringResponse.builder()
                .consolidatedTodaySales(totalSales)
                .consolidatedTodayOrders(totalOrders)
                .consolidatedActiveOrders(totalActiveOrders)
                .consolidatedOccupiedTables(totalOccupiedTables)
                .consolidatedTotalTables(totalTables)
                .totalOutlets(rawList.size())
                .activeOutlets(activeOutletsCount)
                .outlets(dtos)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(isLast)
                .build();
    }

    private OutletMonitoringDto mapToDto(OutletMonitoringProjection p) {
        BigDecimal sales = p.getTodaySales() != null ? p.getTodaySales() : BigDecimal.ZERO;
        long orders = p.getTodayOrders() != null ? p.getTodayOrders() : 0;
        long activeOrders = p.getActiveOrders() != null ? p.getActiveOrders() : 0;
        long occupied = p.getOccupiedTables() != null ? p.getOccupiedTables() : 0;
        long tables = p.getTotalTables() != null ? p.getTotalTables() : 0;
        boolean active = Boolean.TRUE.equals(p.getIsActive());

        BigDecimal aov = orders > 0
                ? sales.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return OutletMonitoringDto.builder()
                .id(p.getId())
                .name(p.getName())
                .code(p.getCode())
                .address(p.getAddress())
                .contactNumber(p.getContactNumber())
                .active(active)
                .todaySales(sales)
                .todayOrders(orders)
                .activeOrders(activeOrders)
                .totalTables(tables)
                .occupiedTables(occupied)
                .averageOrderValue(aov)
                .build();
    }
}
