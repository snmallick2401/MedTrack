package com.medtrack.medicine.controller;

import com.medtrack.medicine.entity.MedicineCategory;
import com.medtrack.medicine.repository.MedicineCategoryRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/medicine-categories")
public class MedicineCategoryController {
    private final MedicineCategoryRepository categories;

    public MedicineCategoryController(MedicineCategoryRepository c) {
        this.categories = c;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<MedicineCategory> list() {
        return categories.findAll();
    }
}