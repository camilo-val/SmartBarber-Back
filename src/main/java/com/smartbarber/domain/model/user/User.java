package com.smartbarber.domain.model.user;

import com.smartbarber.domain.enums.UserStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.user.UserMessageExceptions;

import java.time.Instant;
import java.util.UUID;

public class User {
    private final UUID id;
    private final String firebaseId;
    private final UserStatus status;
    private final Instant createAt;
    private final Instant updateAt;
    private final Short roleId;

    private User(UUID id, String firebaseId, UserStatus status, Instant createAt, Instant updateAt, Short roleId){
        this.id = id;
        this.firebaseId = firebaseId;
        this.status = status;
        this.createAt = createAt;
        this.updateAt = updateAt;
        this.roleId = roleId;
    }

    public static User createUserString (String firebaseId, Short roleId){
        return new User(
                null,
                firebaseId,
                UserStatus.ACTIVE,
                Instant.now(),
                null,
                roleId
        );
    }

    public User update(String firebaseId, UserStatus status, Short roleId){
        if (id == null) {
            throw new BusinessExceptions(
                    UserMessageExceptions.INVALID_USER
            );
        }
        return new User(
                this.id,
                firebaseId,
                status,
                this.createAt,
                this.updateAt,
                roleId
        );
    }
    public User blockUSer(UUID id){
        if (id == null) {
            throw new BusinessExceptions(
                    UserMessageExceptions.INVALID_USER
            );
        }
        return new User(
                id,
                this.firebaseId,
                UserStatus.BLOCKED,
                this.createAt,
                Instant.now(),
                this.roleId
        );
    }
    public static User rebuild(UUID id, String firebaseId, UserStatus status, Instant createAt, Instant updateAt, Short roleId){
        if (id == null || status == null){
            throw new BusinessExceptions(UserMessageExceptions.INVALID_USER);
        }
        return new User(
                id,
                firebaseId,
                status,
                createAt,
                updateAt,
                roleId
        );
    }

    private static boolean isNullOrBlank(String texto){ return texto == null || texto.isBlank(); }

    public UUID getId() { return id; }

    public String getFirebaseId() { return firebaseId; }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreateAt() {
        return createAt;
    }

    public Instant getUpdateAt() {
        return updateAt;
    }

    public Short getRoleId(){return roleId;}

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", firebaseId='" + firebaseId + '\'' +
                ", status=" + status +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                ", roleId=" + roleId +
                '}';
    }
}

