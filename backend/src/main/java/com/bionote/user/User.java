package com.bionote.user;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id @JdbcTypeCode(SqlTypes.CHAR) private UUID id;
    @Column(name="display_name", nullable=false, length=50) private String displayName;
    @Column(name="email_normalized", nullable=false, unique=true, length=255) private String emailNormalized;
    @Column(name="password_hash", nullable=false, length=100) private String passwordHash;
    @Column(name="avatar_storage_key", length=255) private String avatarStorageKey;
    @Column(name="avatar_mime_type", length=100) private String avatarMimeType;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private long version;

    protected User() {}
    public User(UUID id, String displayName, String emailNormalized, String passwordHash, Instant now) {
        this.id=id; this.displayName=displayName; this.emailNormalized=emailNormalized; this.passwordHash=passwordHash;
        this.createdAt=now; this.updatedAt=now;
    }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public UUID getId(){return id;} public String getDisplayName(){return displayName;}
    public void setDisplayName(String value){displayName=value;} public String getEmailNormalized(){return emailNormalized;}
    public String getPasswordHash(){return passwordHash;} public String getAvatarStorageKey(){return avatarStorageKey;}
    public void setAvatarStorageKey(String value){avatarStorageKey=value;} public String getAvatarMimeType(){return avatarMimeType;}
    public void setAvatarMimeType(String value){avatarMimeType=value;} public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
