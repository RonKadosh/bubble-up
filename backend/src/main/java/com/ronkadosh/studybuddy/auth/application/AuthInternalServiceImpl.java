package com.ronkadosh.studybuddy.auth.application;

import com.ronkadosh.studybuddy.auth.internal.AuthInternalService;
import com.ronkadosh.studybuddy.auth.model.User;
import com.ronkadosh.studybuddy.auth.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthInternalServiceImpl implements AuthInternalService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean userExists(UUID userId) {
        return userRepository.existsById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getEmail(UUID userId) {
        return userRepository.findById(userId).map(User::getEmail);
    }
}
