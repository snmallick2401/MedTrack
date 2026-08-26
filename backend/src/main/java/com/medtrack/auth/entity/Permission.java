package com.medtrack.auth.entity;
import com.medtrack.shared.model.BaseEntity; import jakarta.persistence.*;
@Entity @Table(name="permissions") public class Permission extends BaseEntity { @Column(nullable=false,unique=true) private String name; protected Permission(){} public String getName(){return name;} }
