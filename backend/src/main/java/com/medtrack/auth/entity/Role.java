package com.medtrack.auth.entity;
import com.medtrack.shared.model.BaseEntity; import jakarta.persistence.*; import java.util.*;
@Entity @Table(name="roles") public class Role extends BaseEntity { @Column(nullable=false,unique=true) private String name; @ManyToMany(fetch=FetchType.EAGER) @JoinTable(name="role_permissions",joinColumns=@JoinColumn(name="role_id"),inverseJoinColumns=@JoinColumn(name="permission_id")) private Set<Permission> permissions=new HashSet<>(); protected Role(){} public String getName(){return name;} public Set<Permission> getPermissions(){return permissions;} }
