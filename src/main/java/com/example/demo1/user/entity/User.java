package com.example.demo1.user.entity;

import com.example.demo1.user.exception.InvalidUserRoleException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * user는 PostgreSQL 예약어라 테이블 이름은 users로 한다.
 * <p>
 * 이메일은 두 가지로 저장한다.
 * <ul>
 *     <li>emailKey(기본 키): 앞뒤 공백을 지우고 소문자로 바꾼 값. 중복 확인과 조회에 쓴다.
 *     User@Example.com과 user@example.com은 같은 사용자로 본다.</li>
 *     <li>email: 사용자가 입력한 대소문자 그대로의 값. 응답과 화면 표시에 쓴다.</li>
 * </ul>
 * 대소문자를 구분하지 않는 중복 방지는 기본 키가 보장하므로 별도 인덱스가 필요 없다.
 * <p>
 * 기본 키를 직접 넣는 엔티티는 Spring Data의 save()가 새 엔티티인지 판단하지 못해,
 * 같은 이메일이 있으면 insert 대신 merge(덮어쓰기)를 한다. 이를 막으려고 {@link Persistable}을 구현하고,
 * 아직 저장되지 않아 작성일이 없는 엔티티만 새 엔티티로 본다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User implements Persistable<String> {

    @Id
    @Column(name = "email_key", length = 100)
    private String emailKey;

    @Column(nullable = false, length = 100)
    private String email;

    /**
     * BCrypt로 암호화한 값만 저장한다. 원문 비밀번호는 저장하지 않는다.
     */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 사용자는 권한을 하나 이상 가진다. 권한은 값 목록이라 사용자와 함께 저장, 삭제된다.
     * 목록 조회에서 사용자마다 권한 쿼리가 따로 나가지 않도록(N+1) 여러 사용자의 권한을 한 번에 읽는다.
     */
    @Getter(AccessLevel.NONE)
    @ElementCollection
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_email_key"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @BatchSize(size = 100)
    private Set<UserRole> roles = new HashSet<>();

    /**
     * @param email           사용자가 입력한 이메일. 앞뒤 공백만 지우고 대소문자는 그대로 둔다.
     * @param encodedPassword BCrypt로 암호화한 비밀번호
     * @param nickname        비어 있으면 이메일을 닉네임으로 쓴다
     * @param roles           하나 이상의 권한
     */
    public User(String email, String encodedPassword, String nickname, Collection<UserRole> roles) {
        this.email = email.strip();
        this.emailKey = toEmailKey(email);
        this.password = encodedPassword;
        changeNickname(nickname);
        changeRoles(roles);
    }

    /**
     * 비어 있으면 이메일을 닉네임으로 쓴다.
     */
    public void changeNickname(String nickname) {
        this.nickname = nickname == null || nickname.isBlank() ? email : nickname.strip();
    }

    /**
     * 조회와 중복 확인에 쓰는 키로 바꾼다. 앞뒤 공백을 지우고 소문자로 바꾼다.
     * Locale.ROOT를 써서 터키어처럼 I를 다른 글자로 바꾸는 언어 설정의 영향을 받지 않게 한다.
     */
    public static String toEmailKey(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 권한 전체를 바꾼다. 빈 목록은 허용하지 않는다.
     */
    public void changeRoles(Collection<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new InvalidUserRoleException("권한은 하나 이상이어야 합니다.");
        }
        this.roles.clear();
        this.roles.addAll(roles);
    }

    /**
     * 목록을 직접 수정하지 못하게 해서 권한 변경이 changeRoles만 거치도록 한다.
     */
    public Set<UserRole> getRoles() {
        // EnumSet.copyOf는 빈 컬렉션에서 예외가 나므로 빈 EnumSet에 담는다.
        EnumSet<UserRole> copy = EnumSet.noneOf(UserRole.class);
        copy.addAll(roles);
        return Collections.unmodifiableSet(copy);
    }

    @Override
    public String getId() {
        return emailKey;
    }

    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}
