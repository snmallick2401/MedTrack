package com.medtrack.medicine.entity;
import com.medtrack.shared.model.BaseEntity; import jakarta.persistence.*;
@Entity @Table(name="medicine_categories") public class MedicineCategory extends BaseEntity { @Column(nullable=false,unique=true) private String code; @Column(nullable=false) private String name; protected MedicineCategory(){} public String getCode(){return code;} public String getName(){return name;} }
