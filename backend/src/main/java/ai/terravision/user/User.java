package ai.terravision.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean emailVerified;

    @Column
    private String verificationToken;

    @Column
    private Instant verificationTokenExpiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // for Hibernate
    }

    public User(String email, String passwordHash, Role role, boolean emailVerified) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.emailVerified = emailVerified;
        this.createdAt = Instant.now();
    }

    public void issueVerificationToken(String token, Instant expiresAt) {
        this.verificationToken = token;
        this.verificationTokenExpiresAt = expiresAt;
    }

    public boolean isVerificationTokenExpired() {
        return verificationTokenExpiresAt == null || Instant.now().isAfter(verificationTokenExpiresAt);
    }

    public void markVerified() {
        this.emailVerified = true;
        this.verificationToken = null;
        this.verificationTokenExpiresAt = null;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
