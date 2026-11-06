package com.example.demo1.user.service;

import com.example.demo1.user.dto.UserCreateRequest;
import com.example.demo1.user.dto.UserPageRequest;
import com.example.demo1.user.dto.UserPageResponse;
import com.example.demo1.user.dto.UserResponse;
import com.example.demo1.user.dto.UserUpdateRequest;
import com.example.demo1.user.entity.User;
import com.example.demo1.user.exception.DuplicateEmailException;
import com.example.demo1.user.exception.UserNotFoundException;
import com.example.demo1.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클래스 기본값은 읽기 전용 트랜잭션이고, 데이터를 변경하는 메서드만 @Transactional로 재정의한다.
 * 이메일은 대소문자를 구분하지 않고 찾는다. User@Example.com과 user@example.com은 같은 사용자이며,
 * 응답에는 가입할 때 입력한 대소문자 그대로 나간다. 비교 규칙은 {@link User#toEmailKey}에 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        User user = new User(
                request.email(), passwordEncoder.encode(request.password()), request.nickname(), request.roles());
        if (userRepository.existsById(user.getEmailKey())) {
            throw new DuplicateEmailException(user.getEmail());
        }
        try {
            // 위 확인과 저장 사이에 같은 이메일이 먼저 저장되면 기본 키 중복으로 실패한다.
            // flush로 insert를 바로 실행해 그 경우도 같은 예외로 바꾼다.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException(user.getEmail());
        }
        return UserResponse.from(user);
    }

    public UserResponse getOne(String email) {
        return UserResponse.from(findUserWithRoles(email));
    }

    public UserPageResponse getAll(UserPageRequest request) {
        return UserPageResponse.from(userRepository.findAll(request.toPageable()));
    }

    /**
     * 비밀번호는 값이 있을 때만 바꾼다. 닉네임과 권한은 요청 값으로 교체한다.
     */
    @Transactional
    public UserResponse update(String email, UserUpdateRequest request) {
        User user = findUserWithRoles(email);
        if (request.password() != null) {
            user.changePassword(passwordEncoder.encode(request.password()));
        }
        user.changeNickname(request.nickname());
        user.changeRoles(request.roles());

        // 변경 감지로 저장되지만 updatedAt은 flush 시점에 갱신되므로, 응답 생성 전에 flush한다.
        userRepository.flush();
        return UserResponse.from(user);
    }

    /**
     * 사용자와 권한 행을 함께 삭제한다.
     */
    @Transactional
    public void delete(String email) {
        userRepository.delete(findUserWithRoles(email));
    }

    /**
     * @param email 대소문자와 관계없이 찾는다.
     */
    private User findUserWithRoles(String email) {
        return userRepository.findWithRolesByEmailKey(User.toEmailKey(email))
                .orElseThrow(() -> new UserNotFoundException(email));
    }
}
