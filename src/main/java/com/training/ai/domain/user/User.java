package com.training.ai.domain.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author sheeran
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_user")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    
    @Column(nullable = false, length = 100)
    private String password;
    
    @Column(length = 50)
    private String email;
    
    @Column(length = 20)
    private String phone;
    
    @Column(length = 20)
    private String nickname;
    
    @Column(length = 100)
    private String avatar;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;
    
    @Column(nullable = false)
    private LocalDateTime createTime;
    
    @Column
    private LocalDateTime updateTime;
    
    @Version
    private Integer version;
    
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
